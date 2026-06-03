package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDateTime;

@Service
public class BazosParser {

    private static final Logger log = LoggerFactory.getLogger(BazosParser.class);

    private static final String BASE_URL = "https://reality.bazos.cz";
    private final Object rateLimitLock = new Object();
    private volatile boolean rateLimitedForCurrentCycle = false;
    private volatile long rateLimitedUntilMillis = 0;
    private volatile String rateLimitReason = "";
    private int requestsMadeThisCycle = 0;

    @Value("${rentbot.bazos.max-requests-per-cycle:30}")
    private int maxRequestsPerCycle;

    @Value("${rentbot.bazos.rate-limit-cooldown-ms:900000}")
    private long rateLimitCooldownMillis;

    @Value("${rentbot.bazos.request-delay-ms:500}")
    private long requestDelayMillis;

    private static final Pattern LAYOUT_PATTERN =
            Pattern.compile("(\\d+\\s*\\+\\s*(kk|\\d+))", Pattern.CASE_INSENSITIVE);

    private static final String[] KNOWN_PLACES = {
            "Praha", "Praha-východ", "Praha-západ", "Praha vychod", "Praha zapad",
            "Brno", "Ostrava", "Plzeň", "Plzen", "Pardubice", "Olomouc",
            "Liberec", "Zlín", "Zlin", "Most", "Kladno", "Kolín", "Kolin",
            "Kutná Hora", "Kutna Hora", "Ústí nad Labem", "Usti nad Labem",
            "Hradec Králové", "Hradec Kralove", "Jihlava", "Karlovy Vary",
            "Mladá Boleslav", "Mlada Boleslav", "České Budějovice", "Ceske Budejovice",
            "Český Brod", "Cesky Brod", "Nymburk", "Poděbrady", "Podebrady",

            "Beroun", "Břeclav", "Breclav", "Česká Lípa", "Ceska Lipa",
            "Cheb", "Chomutov", "Chrudim", "Děčín", "Decin", "Domažlice", "Domazlice",
            "Frýdek-Místek", "Frydek-Mistek", "Havlíčkův Brod", "Havlickuv Brod",
            "Hodonín", "Hodonin", "Jablonec nad Nisou", "Jindřichův Hradec", "Jindrichuv Hradec",
            "Karviná", "Karvina", "Kroměříž", "Kromeriz", "Mělník", "Melnik",
            "Náchod", "Nachod", "Nový Jičín", "Novy Jicin", "Opava",
            "Písek", "Pisek", "Přerov", "Prerov", "Prostějov", "Prostejov",
            "Rakovník", "Rakovnik", "Sokolov", "Strakonice", "Šumperk", "Sumperk",
            "Svitavy", "Tábor", "Tabor", "Teplice", "Třebíč", "Trebic",
            "Trutnov", "Uherské Hradiště", "Uherske Hradiste", "Vsetín", "Vsetin",
            "Vyškov", "Vyskov", "Znojmo"
    };

    public List<ListingDto> fetchListings(Region region) throws IOException {
        if (isRateLimitedForCurrentCycle()) {
            log.debug(
                    "Bazos skipped region={} reason={} cooldownRemainingSeconds={}",
                    regionTitle(region),
                    currentSkipReason(),
                    currentSkipRemainingSeconds()
            );
            return List.of();
        }

        List<String> urls = buildUrls(region);

        if (urls.isEmpty()) {
            log.info(
                    "Bazos skipped region={} reason=no_zipcodes",
                    region != null ? region.getTitle() : "default"
            );
            return List.of();
        }

        List<ListingDto> result = new ArrayList<>();
        int candidateLinks = 0;
        int skippedBlankTitle = 0;
        int skippedBlankLink = 0;
        int skippedPrice = 0;
        int skippedLayout = 0;
        int localityFallbacks = 0;
        int pagesWithoutLinks = 0;
        int diagnosticSamples = 0;

        for (String url : urls) {
            if (!reserveRequestSlot(region, url)) {
                break;
            }

            log.debug("Bazos URL = {}", url);

            var response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                    .referrer("https://www.google.com/")
                    .timeout(15000)
                    .ignoreHttpErrors(true)
                    .execute();

            if (response.statusCode() == 429) {
                markRateLimited("rate_limited_cooldown", rateLimitCooldownMillis);
                log.warn(
                        "Bazos rate limited region={} finalUrl={} cooldownSeconds={}. Skipping Bazos until cooldown expires.",
                        regionTitle(region),
                        response.url(),
                        currentSkipRemainingSeconds()
                );
                break;
            }

            byte[] bytes = response.bodyAsBytes();

            Document doc = Jsoup.parse(
                    new String(bytes, StandardCharsets.UTF_8),
                    url
            );

            Elements links = doc.select("a[href*='inzerat']");
            candidateLinks += links.size();

            if (links.isEmpty()) {
                pagesWithoutLinks++;

                if (diagnosticSamples < 3) {
                    diagnosticSamples++;
                    log.warn(
                            "Bazos page has no listing links: status={} finalUrl={} title={} bodySample={}",
                            response.statusCode(),
                            response.url(),
                            normalizeLogSample(doc.title(), 120),
                            normalizeLogSample(doc.body() != null ? doc.body().text() : "", 250)
                    );
                }
            }

            for (Element linkEl : links) {
                String title = linkEl.text().trim();

                if (title.isBlank()) {
                    skippedBlankTitle++;
                    continue;
                }

                Element container = findReasonableContainer(linkEl);

                String containerText = container != null ? container.text() : "";
                String fullText = title + "\n" + containerText;

                String link = extractLink(linkEl);

                if (link.isBlank()) {
                    skippedBlankLink++;
                    continue;
                }

                String layout = extractLayout(fullText);
                int price = extractPrice(linkEl, container, fullText);

                String locality = normalizeDisplayLocality(extractLocality(fullText));
                if (shouldUseRegionLocalityFallback(locality) && region != null && region.getTitle() != null) {
                    locality = region.getTitle();
                    localityFallbacks++;
                }

                String photoUrl = extractPhoto(container);

                if (price <= 0 || price > 60000) {
                    skippedPrice++;
                    continue;
                }

                if (layout == null) {
                    skippedLayout++;
                    continue;
                }

                result.add(new ListingDto(
                        title,
                        price,
                        link,
                        layout,
                        locality,
                        photoUrl,
                        "Bazoš",
                        LocalDateTime.now()
                ));
            }

            pauseBetweenRequests();
        }

        log.info(
                "Bazos summary region={} pagesWithoutLinks={} candidateLinks={} accepted={} skippedBlankTitle={} skippedBlankLink={} skippedPrice={} skippedLayout={} localityFallbacks={}",
                region != null ? region.getTitle() : "default",
                pagesWithoutLinks,
                candidateLinks,
                result.size(),
                skippedBlankTitle,
                skippedBlankLink,
                skippedPrice,
                skippedLayout,
                localityFallbacks
        );

        return dedupeByLink(result);
    }

    public void resetRateLimitCycle() {
        synchronized (rateLimitLock) {
            requestsMadeThisCycle = 0;

            if (isCoolingDown(System.currentTimeMillis())) {
                rateLimitedForCurrentCycle = true;
                rateLimitReason = "rate_limited_cooldown";
                return;
            }

            rateLimitedForCurrentCycle = false;
            rateLimitReason = "";
            rateLimitedUntilMillis = 0;
        }
    }

    public boolean isRateLimitedForCurrentCycle() {
        synchronized (rateLimitLock) {
            if (isCoolingDown(System.currentTimeMillis())) {
                rateLimitedForCurrentCycle = true;
                rateLimitReason = "rate_limited_cooldown";
                return true;
            }

            if ("rate_limited_cooldown".equals(rateLimitReason)) {
                rateLimitedForCurrentCycle = false;
                rateLimitReason = "";
                rateLimitedUntilMillis = 0;
            }

            return rateLimitedForCurrentCycle;
        }
    }

    public String currentSkipReason() {
        if (isCoolingDown(System.currentTimeMillis())) {
            return "rate_limited_cooldown";
        }
        if (rateLimitReason == null || rateLimitReason.isBlank()) {
            return "rate_limited_current_cycle";
        }
        return rateLimitReason;
    }

    public long currentSkipRemainingSeconds() {
        long remainingMillis = rateLimitedUntilMillis - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            return 0;
        }
        return (remainingMillis + 999) / 1000;
    }

    private boolean reserveRequestSlot(Region region, String url) {
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();

            if (isCoolingDown(now)) {
                rateLimitedForCurrentCycle = true;
                rateLimitReason = "rate_limited_cooldown";
                log.debug(
                        "Bazos skipped region={} reason=rate_limited_cooldown cooldownRemainingSeconds={}",
                        regionTitle(region),
                        currentSkipRemainingSeconds()
                );
                return false;
            }

            int requestBudget = Math.max(1, maxRequestsPerCycle);
            if (requestsMadeThisCycle >= requestBudget) {
                rateLimitedForCurrentCycle = true;
                rateLimitReason = "request_budget_exhausted_current_cycle";
                log.info(
                        "Bazos request budget exhausted region={} requests={} maxRequestsPerCycle={} nextUrl={}",
                        regionTitle(region),
                        requestsMadeThisCycle,
                        requestBudget,
                        url
                );
                return false;
            }

            requestsMadeThisCycle++;
            return true;
        }
    }

    private void markRateLimited(String reason, long cooldownMillis) {
        synchronized (rateLimitLock) {
            rateLimitedForCurrentCycle = true;
            rateLimitReason = reason;
            rateLimitedUntilMillis = System.currentTimeMillis() + Math.max(60_000, cooldownMillis);
        }
    }

    private boolean isCoolingDown(long now) {
        return rateLimitedUntilMillis > now;
    }

    private void pauseBetweenRequests() throws IOException {
        long delay = Math.max(0, requestDelayMillis);
        if (delay == 0) {
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while throttling Bazos requests", e);
        }
    }

    private String regionTitle(Region region) {
        return region != null ? region.getTitle() : "default";
    }

    private String normalizeLogSample(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, maxLength) + "...";
    }

    private List<String> buildUrls(Region region) {

        if (region == null || region.getCode() == null) {
            return List.of();
        }

        String code = region.getCode().toUpperCase();

        List<String> zipCodes = REGION_ZIPCODES.get(code);

        if (zipCodes == null || zipCodes.isEmpty()) {
            return List.of();
        }

        List<String> urls = new ArrayList<>();

        for (String zip : zipCodes) {

            urls.add(
                    BASE_URL + "/pronajmu/byt/?hlokalita="
                            + zip
                            + "&humkreis=15"
            );

            urls.add(
                    BASE_URL + "/pronajmu/podnajem/?hlokalita="
                            + zip
                            + "&humkreis=15"
            );
        }

        return urls;
    }

    private Element findReasonableContainer(Element linkEl) {
        Element current = linkEl;

        for (int i = 0; i < 6 && current != null; i++) {
            if (current.text() != null && current.text().length() > 60) {
                return current;
            }
            current = current.parent();
        }

        return linkEl.parent();
    }

    private String extractLink(Element linkEl) {
        if (linkEl == null) {
            return "";
        }

        String href = linkEl.attr("href");

        if (href == null || href.isBlank()) {
            return "";
        }

        if (href.startsWith("http")) {
            return href;
        }

        if (href.startsWith("/")) {
            return BASE_URL + href;
        }

        return BASE_URL + "/" + href;
    }

    private int extractPrice(Element linkEl, Element container, String fullText) {
        int priceFromDom = extractPriceFromDom(linkEl, container);
        if (priceFromDom > 0) {
            return priceFromDom;
        }

        return extractPriceFromText(fullText);
    }

    private int extractPriceFromDom(Element linkEl, Element container) {
        if (linkEl == null) {
            return 0;
        }

        Element current = linkEl;
        for (int i = 0; i < 5 && current != null; i++) {
            int price = extractPriceFromText(current.text());
            if (price > 0) {
                return price;
            }
            current = current.parent();
        }

        if (container != null) {
            for (Element child : container.children()) {
                int price = extractPriceFromText(child.text());
                if (price > 0) {
                    return price;
                }
            }

            Element next = container.nextElementSibling();
            if (next != null) {
                int price = extractPriceFromText(next.text());
                if (price > 0) {
                    return price;
                }
            }

            Element prev = container.previousElementSibling();
            if (prev != null) {
                int price = extractPriceFromText(prev.text());
                if (price > 0) {
                    return price;
                }
            }
        }

        return 0;
    }

    private int extractPriceFromText(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        String normalized = text.replace('\u00A0', ' ')
                .replace(",-", " Kč")
                .replace("/měsíc", " Kč")
                .replace("/mesic", " Kč")
                .replace("/mes.", " Kč")
                .replace("za měsíc", " Kč")
                .replace("mesicne", " Kč")
                .replace("měsíčně", " Kč");

        Pattern strict = Pattern.compile(
                "(\\d{1,3}(?:\\s\\d{3})+|\\d{4,5})\\s*(?:Kč|kc)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher m = strict.matcher(normalized);
        while (m.find()) {
            String raw = m.group(1).replaceAll("\\s+", "");
            try {
                int value = Integer.parseInt(raw);

                // аренда комнаты/квартиры, а не залог/продажа/телефон
                if (value >= 3000 && value <= 60000) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return 0;
    }

    private String extractLocality(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = text.replace('\u00A0', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        String best = extractKnownPlaceFragment(normalized, KNOWN_PLACES);
        if (!best.isBlank()) {
            return best;
        }

        String[] lines = text.split("\\R");
        for (String line : lines) {
            String s = line == null ? "" : line.trim();
            if (s.isBlank()) {
                continue;
            }

            String extracted = extractKnownPlaceFragment(s, KNOWN_PLACES);
            if (!extracted.isBlank()) {
                return extracted;
            }
        }

        return "";
    }

    private String extractLayout(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String lower = text.toLowerCase();

        if (lower.contains("spolubydlení")
                || lower.contains("spolubydleni")
                || lower.contains("samostatný pokoj")
                || lower.contains("samostatny pokoj")
                || lower.contains("pronájem pokoje")
                || lower.contains("pronajem pokoje")
                || lower.contains("pokoj k pronájmu")
                || lower.contains("pokoj k pronajmu")
                || lower.matches(".*\\bpokoj\\b.*")) {
            return "ROOM";
        }

        Matcher m = LAYOUT_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }

        return m.group(1).toLowerCase().replaceAll("\\s+", "");
    }

    private String extractPhoto(Element container) {
        if (container == null) {
            return null;
        }

        Element img = container.selectFirst("img[src]");
        if (img == null) {
            return null;
        }

        String src = img.attr("src");
        if (src == null || src.isBlank()) {
            return null;
        }

        if (src.startsWith("http")) {
            return src;
        }
        if (src.startsWith("/")) {
            return BASE_URL + src;
        }
        return BASE_URL + "/" + src;
    }

    private List<ListingDto> dedupeByLink(List<ListingDto> input) {
        Map<String, ListingDto> map = new LinkedHashMap<>();

        for (ListingDto dto : input) {
            if (dto == null || dto.link() == null || dto.link().isBlank()) {
                continue;
            }
            map.putIfAbsent(dto.link(), dto);
        }

        return new ArrayList<>(map.values());
    }

    private int indexOfIgnoreCase(String text, String part) {
        if (text == null || part == null) {
            return -1;
        }
        return text.toLowerCase().indexOf(part.toLowerCase());
    }

    private int findFirstStop(String s) {
        String lower = s.toLowerCase();

        int comma = s.indexOf(',');
        int pipe = s.indexOf('|');
        int semicolon = s.indexOf(';');
        int kc = lower.indexOf("kč");
        int m2 = lower.indexOf("m²");
        int m2alt = lower.indexOf("m2");
        int sentence = s.indexOf('.');
        int exclamation = s.indexOf('!');
        int question = s.indexOf('?');

        int stop = -1;

        if (comma >= 0) stop = comma;
        if (pipe >= 0 && (stop == -1 || pipe < stop)) stop = pipe;
        if (semicolon >= 0 && (stop == -1 || semicolon < stop)) stop = semicolon;
        if (kc >= 0 && (stop == -1 || kc < stop)) stop = kc;
        if (m2 >= 0 && (stop == -1 || m2 < stop)) stop = m2;
        if (m2alt >= 0 && (stop == -1 || m2alt < stop)) stop = m2alt;
        if (sentence >= 0 && (stop == -1 || sentence < stop)) stop = sentence;
        if (exclamation >= 0 && (stop == -1 || exclamation < stop)) stop = exclamation;
        if (question >= 0 && (stop == -1 || question < stop)) stop = question;

        return stop;
    }

    private String cleanupLocality(String s) {
        if (s == null) {
            return "";
        }

        return s.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\-–,.:;\\s]+", "")
                .replaceAll("[\\-–,.:;\\s]+$", "")
                .replace(" ?", "")
                .replace("? ", "")
                .trim();
    }

    private String extractKnownPlaceFragment(String text, String[] knownPlaces) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String best = "";

        for (String place : knownPlaces) {
            int idx = indexOfIgnoreCase(text, place);
            if (idx < 0) {
                continue;
            }

            String tail = text.substring(idx);

            // сначала жёстко режем по очевидным стопам
            int hardStop = findFirstHardStopForLocality(tail);
            if (hardStop > 0) {
                tail = tail.substring(0, hardStop);
            }

            // потом пробуем выделить максимум "город + район/часть"
            String candidate = extractLocalityWindow(tail);

            candidate = cleanupLocality(candidate);

            if (candidate.isBlank()) {
                continue;
            }

            if (candidate.length() > best.length()) {
                best = candidate;
            }
        }

        return best;
    }

    private int findFirstHardStopForLocality(String s) {
        String lower = s.toLowerCase();

        String[] stopWords = {
                "byt je",
                "k bytu",
                "volny",
                "volný",
                "nabizim",
                "nabízím",
                "pronajmu",
                "pronájem",
                "dum",
                "dům",
                "balkon",
                "lodzie",
                "lodžie",
                "sklep",
                "parkovani",
                "parkování",
                "kauce",
                "rk nevolat",
                "nevolat",
                "ihned",
                "od ",
                "vhodny",
                "vhodný"
        };

        int stop = findFirstStop(s);

        for (String stopWord : stopWords) {
            int idx = lower.indexOf(stopWord);
            if (idx >= 0 && (stop == -1 || idx < stop)) {
                stop = idx;
            }
        }

        return stop;
    }

    private String extractLocalityWindow(String tail) {
        if (tail == null || tail.isBlank()) {
            return "";
        }

        // Примерно:
        // Praha 9 - Letňany
        // Kolín V
        // Kutná Hora
        // Praha-východ
        Pattern p = Pattern.compile(
                "^([A-Za-zÀ-ž\\-]+(?:\\s+[A-Za-zÀ-ž0-9\\-]+){0,5}(?:\\s*[-–]\\s*[A-Za-zÀ-ž0-9\\-]+(?:\\s+[A-Za-zÀ-ž0-9\\-]+){0,3})?)"
        );

        Matcher m = p.matcher(tail.trim());
        if (m.find()) {
            return m.group(1);
        }

        return tail;
    }

    private String normalizeDisplayLocality(String locality) {
        if (locality == null || locality.isBlank()) {
            return "";
        }

        String s = locality
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        String lower = s.toLowerCase();

        // praha 8 u metra praha 8 → Praha 8
        Matcher m = Pattern.compile("\\bpraha\\s*(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE).matcher(s);
        if (m.find()) {
            return "Praha " + m.group(1);
        }

        // убираем дубли слов: "praha praha"
        s = s.replaceAll("(?i)\\b(\\w+)\\s+\\1\\b", "$1");

        return s;
    }

    private boolean shouldUseRegionLocalityFallback(String locality) {
        if (locality == null || locality.isBlank()) {
            return true;
        }

        String lower = normalizeLocality(locality);
        if (lower == null || lower.isBlank()) {
            return true;
        }

        return lower.contains("vchodem")
                || lower.contains("bytovem")
                || lower.contains("zarizene")
                || lower.contains("uzamykatelny")
                || lower.contains("kuchyn")
                || lower.contains("zahrada");
    }

    private String normalizeLocality(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }

        return s.toLowerCase()
                .replace('ě', 'e')
                .replace('š', 's')
                .replace('č', 'c')
                .replace('ř', 'r')
                .replace('ž', 'z')
                .replace('ý', 'y')
                .replace('á', 'a')
                .replace('í', 'i')
                .replace('é', 'e')
                .replace('ů', 'u')
                .replace('ú', 'u')
                .replace('ň', 'n')
                .replace('ď', 'd')
                .replace('ť', 't')
                .replaceAll("[^a-z0-9\\s,-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static final Map<String, List<String>> REGION_ZIPCODES = Map.ofEntries(

            Map.entry("PRAHA", List.of("11000", "12000", "13000", "14000", "15000", "16000", "17000", "18000", "19000")),

            Map.entry("BRNO", List.of(
                    "60200", "60300", "60400",
                    "61200", "61300", "61400", "61500", "61600",
                    "61700", "61800", "61900",
                    "62000", "62100", "62200", "62300",
                    "62400", "62500", "62700", "62800",
                    "63500", "63700", "63800", "63900",
                    "66434", "66441", "66448"
            )),

            Map.entry("OSTRAVA", List.of(
                    "70030", "70200", "70300", "70400",
                    "70800", "70900", "71000", "71100",
                    "71200", "71300", "71400", "71500",
                    "71600", "71700", "71800", "71900",
                    "72000", "72100", "72200", "72300",
                    "72400", "72525", "72526", "72527",
                    "73932", "74285"
            )),

            Map.entry("PLZEN", List.of(
                    "30100", "30200", "30300", "30400",
                    "30500", "30600", "31200", "31600",
                    "31800", "31900", "32100", "32200",
                    "32300", "32600", "33011", "33023"
            )),

            Map.entry("LIBEREC", List.of(
                    "46001", "46005", "46006", "46007",
                    "46008", "46010", "46311", "46312",
                    "46331"
            )),

            Map.entry("PARDUBICE", List.of(
                    "53002", "53003", "53006", "53009",
                    "53341", "53345", "53351", "53352"
            )),

            Map.entry("HRADEC_KRALOVE", List.of("50002", "50003", "50006", "50008")),

            Map.entry("OLOMOUC", List.of(
                    "77900", "77911", "77941",
                    "78301", "78335", "78371"
            )),

            Map.entry("KOLIN", List.of(
                    "28002", "28003", "28004",
                    "28144", "28161", "28201"
            )),

            Map.entry("KUTNA_HORA", List.of(
                    "28401", "28403", "28522",
                    "28504", "28601"
            )),

            Map.entry("TRUTNOV", List.of(
                    "54101", "54102",
                    "54221", "54223", "54224",
                    "54225", "54226", "54227",
                    "54232", "54371", "54401"
            )),

            Map.entry("JIHLAVA", List.of(
                    "58601", "58603", "58605",
                    "58811", "58813", "58821"
            )),

            Map.entry("KARLOVY_VARY", List.of(
                    "36001", "36004", "36005",
                    "36225", "36263", "36301"
            )),

            Map.entry("MLADA_BOLESLAV", List.of(
                    "29301", "29305", "29421",
                    "29471", "29473"
            )),

            Map.entry("KLADNO", List.of("27201", "27203", "27204")),

            Map.entry("CESKE_BUDEJOVICE", List.of(
                    "37001", "37004", "37005",
                    "37371", "37372", "37373"
            )),

            Map.entry("NYMBURK", List.of(
                    "28802", "28803", "28922",
                    "28923", "28924"
            )),

            Map.entry("BEROUN", List.of(
                    "26601", "26603", "26727",
                    "26751", "26753"
            )),

            Map.entry("BRECLAV", List.of(
                    "69002", "69003",
                    "69141", "69142", "69144"
            )),

            Map.entry("CESKA_LIPA", List.of(
                    "47001", "47006", "47124",
                    "47141"
            )),

            Map.entry("CHEB", List.of(
                    "35002", "35003",
                    "35101", "35124"
            )),

            Map.entry("CHOMUTOV", List.of(
                    "43001", "43003", "43111",
                    "43151", "43191"
            )),

            Map.entry("CHRUDIM", List.of(
                    "53701", "53703",
                    "53803", "53821"
            )),

            Map.entry("DECIN", List.of(
                    "40501", "40502",
                    "40711", "40713"
            )),

            Map.entry("DOMAZLICE", List.of(
                    "34401", "34404",
                    "34506", "34522"
            )),

            Map.entry("FRYDEK_MISTEK", List.of(
                    "73801", "73802",
                    "73911", "73921", "73961"
            )),

            Map.entry("HAVLICKUV_BROD", List.of(
                    "58001", "58002",
                    "58222", "58291"
            )),

            Map.entry("HODONIN", List.of(
                    "69501", "69503",
                    "69611", "69662"
            )),

            Map.entry("JABLONEC", List.of(
                    "46601", "46602",
                    "46822", "46841"
            )),

            Map.entry("JINDRICHUV_HRADEC", List.of(
                    "37701", "37703",
                    "37821", "37853"
            )),

            Map.entry("KARVINA", List.of(
                    "73301", "73401", "73506",
                    "73511", "73514"
            )),

            Map.entry("KROMERIZ", List.of(
                    "76701", "76705",
                    "76811", "76824"
            )),

            Map.entry("MELNIK", List.of(
                    "27601", "27602",
                    "27711", "27713"
            )),

            Map.entry("NACHOD", List.of(
                    "54701", "54703",
                    "54901", "54941"
            )),

            Map.entry("NOVY_JICIN", List.of(
                    "74101", "74102",
                    "74213", "74221"
            )),

            Map.entry("OPAVA", List.of(
                    "74601", "74602",
                    "74705", "74721"
            )),

            Map.entry("PISEK", List.of(
                    "39701", "39703",
                    "39811", "39843"
            )),

            Map.entry("PREROV", List.of(
                    "75002", "75004",
                    "75124", "75131"
            )),

            Map.entry("PROSTEJOV", List.of(
                    "79601", "79604",
                    "79811", "79821"
            )),

            Map.entry("RAKOVNIK", List.of(
                    "26901", "26903",
                    "27023", "27033"
            )),

            Map.entry("SOKOLOV", List.of(
                    "35601", "35604",
                    "35731", "35735"
            )),

            Map.entry("STRAKONICE", List.of(
                    "38601", "38603",
                    "38711", "38716"
            )),

            Map.entry("SUMPERK", List.of(
                    "78701", "78705",
                    "78813", "78815"
            )),

            Map.entry("SVITAVY", List.of(
                    "56802", "56803",
                    "56943", "56957"
            )),

            Map.entry("TABOR", List.of(
                    "39001", "39002", "39003",
                    "39111", "39137"
            )),

            Map.entry("TEPLICE", List.of(
                    "41501", "41503",
                    "41741", "41742"
            )),

            Map.entry("TREBIC", List.of(
                    "67401", "67405",
                    "67521", "67522", "67571"
            )),

            Map.entry("UHERSKE_HRADISTE", List.of(
                    "68601", "68603",
                    "68724", "68725"
            )),

            Map.entry("VSETIN", List.of(
                    "75501", "75505",
                    "75605", "75661"
            )),

            Map.entry("VYSKOV", List.of(
                    "68201", "68203",
                    "68352", "68354"
            )),

            Map.entry("ZNOJMO", List.of(
                    "66902", "66904",
                    "67103", "67124", "67126",
                    "67128", "67165"
            )),

            Map.entry("ZLIN", List.of(
                    "76001", "76005",
                    "76302", "76312", "76314",
                    "76315", "76361"
            ))
    );
}
