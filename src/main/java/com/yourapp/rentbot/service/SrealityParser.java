package com.yourapp.rentbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SrealityParser {

    private static final Logger log = LoggerFactory.getLogger(SrealityParser.class);

    private static final Pattern LAYOUT_PATTERN =
            Pattern.compile("(\\d+\\s*\\+\\s*(kk|\\d+))", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_FROM_TEXT_PATTERN =
            Pattern.compile("(\\d[\\d\\s.]{2,})\\s*K\\s*\\p{L}*", Pattern.CASE_INSENSITIVE);

    private static final int MAX_PAGES = 30;
    private static final int HTML_FALLBACK_MAX_PAGES = 3;
    private static final int PER_PAGE = 20;
    private static final String SREALITY_BASE_URL = "https://www.sreality.cz";
    private volatile boolean temporarilyUnavailableForCurrentCycle = false;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public static Integer getDistrictId(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) {
            return null;
        }

        return SREALITY_DISTRICT_IDS.get(regionCode.toUpperCase());
    }

    public static Integer getRegionId(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) {
            return null;
        }

        return SREALITY_REGION_IDS.get(regionCode.toUpperCase());
    }

    public List<ListingDto> fetchListings(Integer srealityDistrictId) throws IOException {
        return fetchListings(null, srealityDistrictId);
    }

    public List<ListingDto> fetchListings(String regionCode, Integer srealityDistrictId) throws IOException {
        String runId = UUID.randomUUID().toString().substring(0, 8);

        if (temporarilyUnavailableForCurrentCycle) {
            log.info("Sreality run={} skipped regionCode={} reason=temporarily_unavailable_current_cycle",
                    runId, regionCode);
            return List.of();
        }

        if (srealityDistrictId == null) {
            log.info("Sreality run={} skipped: srealityDistrictId is null", runId);
            return List.of();
        }

        try {
            List<ListingDto> result = new ArrayList<>();
            boolean triedHtml = false;

            Integer srealityRegionId = getRegionId(regionCode);

            log.info("Sreality run={} started, regionCode={}, districtId={}, regionId={}",
                    runId, regionCode, srealityDistrictId, srealityRegionId);

            if (hasHtmlSlug(regionCode)) {
                triedHtml = true;
                List<ListingDto> htmlListings = fetchListingsFromHtml(regionCode, runId);
                if (!htmlListings.isEmpty()) {
                    log.info("Sreality run={} finished via html, raw={}, deduped={}",
                            runId, htmlListings.size(), htmlListings.size());
                    return htmlListings;
                }

                log.info("Sreality run={} html returned no listings, api fallback skipped for known slug", runId);
                return List.of();
            }

            for (int page = 1; page <= MAX_PAGES; page++) {
                String apiUrl = buildApiUrl(srealityDistrictId, srealityRegionId, page);

                log.debug("Sreality run={} page={} url={}", runId, page, apiUrl);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Accept-Language", "cs-CZ,cs;q=0.9,en;q=0.8")
                        .header("Referer", "https://www.sreality.cz/")
                        .GET()
                        .build();

                HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

                if (resp.statusCode() != 200) {
                    String body = resp.body() == null ? "" : new String(resp.body(), StandardCharsets.UTF_8);

                    log.warn(
                            "Sreality run={} page={} non-200 status={}, url={}, body={}",
                            runId,
                            page,
                            resp.statusCode(),
                            apiUrl,
                            body.substring(0, Math.min(300, body.length()))
                    );

                    if (page == 1 && !triedHtml) {
                        List<ListingDto> fallback = fetchListingsFromHtml(regionCode, runId);
                        if (!fallback.isEmpty()) {
                            log.info("Sreality run={} finished via html fallback, raw={}, deduped={}",
                                    runId, fallback.size(), fallback.size());
                            return fallback;
                        }
                    }

                    break;
                }

                String json = new String(resp.body(), StandardCharsets.UTF_8);
                JsonNode root = mapper.readTree(json);
                JsonNode estates = root.path("_embedded").path("estates");

                if (!estates.isArray()) {
                    log.warn("Sreality run={} page={} has no estates array", runId, page);
                    break;
                }

                log.info("Sreality run={} page={} parsed={} listings", runId, page, estates.size());

                if (estates.isEmpty()) {
                    if (page == 1) {
                        log.warn("Sreality run={} first page empty, districtId={}", runId, srealityDistrictId);
                    }
                    break;
                }

                for (JsonNode estate : estates) {
                    String title = estate.path("name").asText("");
                    int priceCzk = extractPrice(estate);
                    String link = buildLink(estate);
                    String layout = extractLayout(title);
                    String photoUrl = extractPhotoUrl(estate);
                    String locality = estate.path("locality").asText("");

                    if (link == null || link.isBlank() || link.equals("https://www.sreality.cz")) {
                        continue;
                    }

                    result.add(new ListingDto(
                            title,
                            priceCzk,
                            link,
                            layout,
                            locality,
                            photoUrl,
                            "Sreality",
                            LocalDateTime.now()
                    ));
                }

                if (estates.size() < PER_PAGE) {
                    break;
                }
            }

            List<ListingDto> deduped = dedupeByLink(result);

            log.info("Sreality run={} finished, raw={}, deduped={}", runId, result.size(), deduped.size());

            return deduped;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching Sreality", e);
        }
    }

    public void resetTemporaryUnavailableCycle() {
        temporarilyUnavailableForCurrentCycle = false;
    }

    private boolean hasHtmlSlug(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) {
            return false;
        }

        String slug = SREALITY_SLUGS.get(regionCode.toUpperCase());
        return slug != null && !slug.isBlank();
    }

    private String buildApiUrl(Integer srealityDistrictId, Integer srealityRegionId, int page) {
        long tms = System.currentTimeMillis();

        return new StringBuilder("https://www.sreality.cz/api/cs/v2/estates")
                .append("?category_main_cb=1")
                .append("&category_type_cb=2")
                .append("&locality_district_id=").append(srealityDistrictId)
                .append("&page=").append(page)
                .append("&per_page=").append(PER_PAGE)
                .append("&tms=").append(tms)
                .toString();
    }

    private List<ListingDto> fetchListingsFromHtml(String regionCode, String runId) {
        String slug = SREALITY_SLUGS.get(regionCode == null ? "" : regionCode.toUpperCase());
        if (slug == null || slug.isBlank()) {
            log.warn("Sreality run={} html fallback skipped: no slug for regionCode={}", runId, regionCode);
            return List.of();
        }

        List<ListingDto> result = new ArrayList<>();

        for (int page = 1; page <= HTML_FALLBACK_MAX_PAGES; page++) {
            String pageUrl = buildHtmlSearchUrl(slug, page);

            try {
                var response = Jsoup.connect(pageUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "cs-CZ,cs;q=0.9,en;q=0.8")
                        .referrer("https://www.sreality.cz/")
                        .timeout(20_000)
                        .ignoreHttpErrors(true)
                        .execute();

                if (response.statusCode() != 200) {
                    log.warn("Sreality run={} html page={} non-200 status={} url={}",
                            runId, page, response.statusCode(), response.url());

                    if (page == 1 && (response.statusCode() == 404 || response.statusCode() == 429)) {
                        temporarilyUnavailableForCurrentCycle = true;
                        log.warn("Sreality run={} marked temporarily unavailable for current scheduler cycle, status={}",
                                runId, response.statusCode());
                    }

                    break;
                }

                Document doc = response.parse();

                List<ListingDto> pageListings = parseHtmlListings(doc);
                log.info("Sreality run={} html fallback page={} parsed={} listings", runId, page, pageListings.size());

                if (pageListings.isEmpty()) {
                    break;
                }

                result.addAll(pageListings);

                if (pageListings.size() < PER_PAGE) {
                    break;
                }
            } catch (IOException e) {
                log.warn("Sreality run={} html fallback failed url={}: {}", runId, pageUrl, e.toString());
                break;
            }
        }

        return dedupeByLink(result);
    }

    private String buildHtmlSearchUrl(String slug, int page) {
        String baseUrl = SREALITY_BASE_URL + "/hledani/pronajem/byty/" + slug;
        if (page <= 1) {
            return baseUrl;
        }

        return baseUrl + "?strana=" + page;
    }

    private List<ListingDto> parseHtmlListings(Document doc) {
        Map<String, ListingDto> listings = new LinkedHashMap<>();
        Elements links = doc.select("a[href*=/detail/pronajem/byt/]");

        for (Element linkElement : links) {
            String link = normalizeDetailLink(linkElement.attr("href"));
            if (link.isBlank() || listings.containsKey(link)) {
                continue;
            }

            String cardText = extractCardText(linkElement);
            String title = extractHtmlTitle(cardText, link);
            int price = extractHtmlPrice(cardText);
            String layout = extractLayout(title + " " + cardText);
            String locality = extractHtmlLocality(cardText, title, link);
            String photoUrl = extractHtmlPhotoUrl(linkElement);

            listings.put(link, new ListingDto(
                    title,
                    price,
                    link,
                    layout,
                    locality,
                    photoUrl,
                    "Sreality",
                    LocalDateTime.now()
            ));
        }

        return new ArrayList<>(listings.values());
    }

    private String normalizeDetailLink(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }

        String link = href.trim();
        if (link.startsWith("//")) {
            link = "https:" + link;
        } else if (link.startsWith("/")) {
            link = SREALITY_BASE_URL + link;
        }

        int queryIndex = link.indexOf('?');
        if (queryIndex >= 0) {
            link = link.substring(0, queryIndex);
        }

        int hashIndex = link.indexOf('#');
        if (hashIndex >= 0) {
            link = link.substring(0, hashIndex);
        }

        return link;
    }

    private String extractCardText(Element linkElement) {
        Element current = linkElement;
        while (current != null) {
            String text = current.text();
            int detailLinks = current.select("a[href*=/detail/pronajem/byt/]").size();
            if (text != null && text.length() >= 25 && detailLinks <= 3) {
                return text;
            }
            current = current.parent();
        }

        return linkElement.text();
    }

    private String extractHtmlTitle(String cardText, String link) {
        if (cardText != null && !cardText.isBlank()) {
            String normalized = cardText.replaceAll("\\s+", " ").trim();
            Matcher priceMatcher = PRICE_FROM_TEXT_PATTERN.matcher(normalized);
            if (priceMatcher.find()) {
                normalized = normalized.substring(0, priceMatcher.start()).trim();
            }
            if (!normalized.isBlank()) {
                return normalized;
            }
        }

        String layout = extractLayout(link);
        if (layout == null || layout.isBlank()) {
            return "Pronajem bytu";
        }

        return "Pronajem bytu " + layout;
    }

    private int extractHtmlPrice(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        Matcher matcher = PRICE_FROM_TEXT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return 0;
        }

        String digits = matcher.group(1).replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return 0;
        }

        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractHtmlLocality(String cardText, String title, String link) {
        String fromTitle = localityFromTitle(title);

        if (cardText != null && title != null) {
            String normalized = cardText.replaceAll("\\s+", " ").trim();
            String withoutTitle = normalized.startsWith(title)
                    ? normalized.substring(title.length()).trim()
                    : normalized;
            Matcher priceMatcher = PRICE_FROM_TEXT_PATTERN.matcher(withoutTitle);
            if (priceMatcher.find()) {
                withoutTitle = withoutTitle.substring(priceMatcher.end()).trim();
            }
            if (isUsableLocality(withoutTitle)) {
                return withoutTitle;
            }
        }

        if (isUsableLocality(fromTitle)) {
            return fromTitle;
        }

        return localityFromDetailLink(link);
    }

    private String localityFromTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }

        String normalized = title.replaceAll("\\s+", " ").trim();

        int comma = normalized.lastIndexOf(',');
        if (comma >= 0 && comma + 1 < normalized.length()) {
            String locality = normalized.substring(comma + 1).trim();
            if (isUsableLocality(locality)) {
                return locality;
            }
        }

        Matcher matcher = Pattern.compile("\\bm\\s*²\\s+(.+)$", Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (matcher.find()) {
            String locality = matcher.group(1).trim();
            if (isUsableLocality(locality)) {
                return locality;
            }
        }

        return "";
    }

    private boolean isUsableLocality(String locality) {
        if (locality == null || locality.isBlank()) {
            return false;
        }

        String normalized = locality.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.length() > 80) {
            return false;
        }

        String lower = normalized.toLowerCase();
        return !lower.equals("/měsíc")
                && !lower.equals("/mesic")
                && !lower.equals("měsíc")
                && !lower.equals("mesic")
                && !lower.equals("kč")
                && !lower.equals("kc");
    }

    private String localityFromDetailLink(String link) {
        if (link == null || link.isBlank()) {
            return "";
        }

        String[] parts = link.split("/");
        if (parts.length < 2) {
            return "";
        }

        String locality = parts[parts.length - 2]
                .replace('-', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        if (locality.matches("\\d+\\+.*")) {
            return "";
        }

        return locality;
    }

    private String extractHtmlPhotoUrl(Element linkElement) {
        Element current = linkElement;
        while (current != null) {
            Element img = current.selectFirst("img[src], img[data-src]");
            if (img != null) {
                String src = img.hasAttr("src") ? img.absUrl("src") : img.absUrl("data-src");
                if (src != null && !src.isBlank()) {
                    return src;
                }
            }
            current = current.parent();
        }

        return null;
    }

    private int extractPrice(JsonNode estate) {
        JsonNode priceCzkNode = estate.path("price_czk");

        if (priceCzkNode.isInt() || priceCzkNode.isLong()) {
            return priceCzkNode.asInt();
        }

        JsonNode valueRaw = priceCzkNode.path("value_raw");
        if (valueRaw.isInt() || valueRaw.isLong()) {
            return valueRaw.asInt();
        }

        JsonNode price = estate.path("price");
        if (price.isInt() || price.isLong()) {
            return price.asInt();
        }

        return 0;
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

    private String buildLink(JsonNode estate) {
        String hash = estate.path("hash_id").asText("");
        String locality = estate.path("seo").path("locality").asText("");
        String categorySubCb = estate.path("seo").path("category_sub_cb").asText("");

        if (hash.isBlank()) {
            return null;
        }

        if (!categorySubCb.isBlank() && !locality.isBlank()) {
            return "https://www.sreality.cz/detail/pronajem/byt/"
                    + categorySubCb + "/"
                    + locality + "/"
                    + hash;
        }

        if (!locality.isBlank()) {
            return "https://www.sreality.cz/detail/pronajem/byt/"
                    + locality + "/"
                    + hash;
        }

        return "https://www.sreality.cz/detail/pronajem/byt/" + hash;
    }

    private String extractLayout(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }

        String lower = title.toLowerCase();

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

        Matcher m = LAYOUT_PATTERN.matcher(title);
        if (!m.find()) {
            return null;
        }

        return m.group(1).toLowerCase().replaceAll("\\s+", "");
    }

    private String extractPhotoUrl(JsonNode estate) {
        JsonNode links = estate.path("_links");

        if (links.isObject()) {
            JsonNode images = links.path("images");

            if (images.isArray() && !images.isEmpty()) {
                String href = images.get(0).path("href").asText("");

                if (!href.isBlank()) {
                    return href;
                }
            }
        }

        JsonNode embedded = estate.path("_embedded");

        if (embedded.isObject()) {
            JsonNode images = embedded.path("images");

            if (images.isArray() && !images.isEmpty()) {
                String href = images.get(0).path("href").asText("");

                if (!href.isBlank()) {
                    return href;
                }
            }
        }

        return null;
    }

    private static final Map<String, String> SREALITY_SLUGS = Map.ofEntries(
            Map.entry("BENESOV", "benesov"),
            Map.entry("BEROUN", "beroun"),
            Map.entry("BLANSKO", "blansko"),
            Map.entry("BRNO", "brno"),
            Map.entry("BRNO_VENKOV", "brno-venkov"),
            Map.entry("BRUNTAL", "bruntal"),
            Map.entry("BRECLAV", "breclav"),
            Map.entry("CESKA_LIPA", "ceska-lipa"),
            Map.entry("CESKE_BUDEJOVICE", "ceske-budejovice"),
            Map.entry("CESKY_KRUMLOV", "cesky-krumlov"),
            Map.entry("DECIN", "decin"),
            Map.entry("DOMAZLICE", "domazlice"),
            Map.entry("FRYDEK_MISTEK", "frydek-mistek"),
            Map.entry("HAVLICKUV_BROD", "havlickuv-brod"),
            Map.entry("HODONIN", "hodonin"),
            Map.entry("HRADEC_KRALOVE", "hradec-kralove"),
            Map.entry("CHEB", "cheb"),
            Map.entry("CHOMUTOV", "chomutov"),
            Map.entry("CHRUDIM", "chrudim"),
            Map.entry("JABLONEC_NAD_NISOU", "jablonec-nad-nisou"),
            Map.entry("JABLONEC", "jablonec-nad-nisou"),
            Map.entry("JESENIK", "jesenik"),
            Map.entry("JICIN", "jicin"),
            Map.entry("JIHLAVA", "jihlava"),
            Map.entry("JINDRICHUV_HRADEC", "jindrichuv-hradec"),
            Map.entry("KARLOVY_VARY", "karlovy-vary"),
            Map.entry("KARVINA", "karvina"),
            Map.entry("KLADNO", "kladno"),
            Map.entry("KLATOVY", "klatovy"),
            Map.entry("KOLIN", "kolin"),
            Map.entry("KROMERIZ", "kromeriz"),
            Map.entry("KUTNA_HORA", "kutna-hora"),
            Map.entry("LIBEREC", "liberec"),
            Map.entry("LITOMERICE", "litomerice"),
            Map.entry("LOUNY", "louny"),
            Map.entry("MELNIK", "melnik"),
            Map.entry("MLADA_BOLESLAV", "mlada-boleslav"),
            Map.entry("MOST", "most"),
            Map.entry("NACHOD", "nachod"),
            Map.entry("NOVY_JICIN", "novy-jicin"),
            Map.entry("NYMBURK", "nymburk"),
            Map.entry("OLOMOUC", "olomouc"),
            Map.entry("OPAVA", "opava"),
            Map.entry("OSTRAVA", "ostrava"),
            Map.entry("PARDUBICE", "pardubice"),
            Map.entry("PELHRIMOV", "pelhrimov"),
            Map.entry("PISEK", "pisek"),
            Map.entry("PLZEN", "plzen"),
            Map.entry("PLZEN_JIH", "plzen-jih"),
            Map.entry("PLZEN_SEVER", "plzen-sever"),
            Map.entry("PRAHA", "praha"),
            Map.entry("PRAHA_1", "praha-1"),
            Map.entry("PRAHA_2", "praha-2"),
            Map.entry("PRAHA_3", "praha-3"),
            Map.entry("PRAHA_4", "praha-4"),
            Map.entry("PRAHA_5", "praha-5"),
            Map.entry("PRAHA_6", "praha-6"),
            Map.entry("PRAHA_7", "praha-7"),
            Map.entry("PRAHA_8", "praha-8"),
            Map.entry("PRAHA_9", "praha-9"),
            Map.entry("PRAHA_10", "praha-10"),
            Map.entry("PRAHA_VYCHOD", "praha-vychod"),
            Map.entry("PRAHA_ZAPAD", "praha-zapad"),
            Map.entry("PRACHATICE", "prachatice"),
            Map.entry("PROSTEJOV", "prostejov"),
            Map.entry("PREROV", "prerov"),
            Map.entry("PRIBRAM", "pribram"),
            Map.entry("RAKOVNIK", "rakovnik"),
            Map.entry("ROKYCANY", "rokycany"),
            Map.entry("RYCHNOV_NAD_KNEZNOU", "rychnov-nad-kneznou"),
            Map.entry("SEMILY", "semily"),
            Map.entry("SOKOLOV", "sokolov"),
            Map.entry("STRAKONICE", "strakonice"),
            Map.entry("SVITAVY", "svitavy"),
            Map.entry("SUMPERK", "sumperk"),
            Map.entry("TABOR", "tabor"),
            Map.entry("TACHOV", "tachov"),
            Map.entry("TEPLICE", "teplice"),
            Map.entry("TRUTNOV", "trutnov"),
            Map.entry("TREBIC", "trebic"),
            Map.entry("UHERSKE_HRADISTE", "uherske-hradiste"),
            Map.entry("USTI_NAD_LABEM", "usti-nad-labem"),
            Map.entry("USTI_NAD_ORLICI", "usti-nad-orlici"),
            Map.entry("VSETIN", "vsetin"),
            Map.entry("VYSKOV", "vyskov"),
            Map.entry("ZLIN", "zlin"),
            Map.entry("ZNOJMO", "znojmo"),
            Map.entry("ZDAR_NAD_SAZAVOU", "zdar-nad-sazavou")
    );

    private static final Map<String, Integer> SREALITY_REGION_IDS = Map.ofEntries(
            Map.entry("BENESOV", 11),
            Map.entry("BEROUN", 11),
            Map.entry("BLANSKO", 1),
            Map.entry("BRNO", 1),
            Map.entry("BRNO_VENKOV", 1),
            Map.entry("BRUNTAL", 7),
            Map.entry("BRECLAV", 1),
            Map.entry("CESKA_LIPA", 4),
            Map.entry("CESKE_BUDEJOVICE", 13),
            Map.entry("CESKY_KRUMLOV", 13),
            Map.entry("DECIN", 9),
            Map.entry("DOMAZLICE", 5),
            Map.entry("FRYDEK_MISTEK", 7),
            Map.entry("HAVLICKUV_BROD", 14),
            Map.entry("HODONIN", 1),
            Map.entry("HRADEC_KRALOVE", 3),
            Map.entry("CHEB", 2),
            Map.entry("CHOMUTOV", 9),
            Map.entry("CHRUDIM", 6),
            Map.entry("JABLONEC_NAD_NISOU", 4),
            Map.entry("JABLONEC", 4),
            Map.entry("JESENIK", 12),
            Map.entry("JICIN", 3),
            Map.entry("JIHLAVA", 14),
            Map.entry("JINDRICHUV_HRADEC", 13),
            Map.entry("KARLOVY_VARY", 2),
            Map.entry("KARVINA", 7),
            Map.entry("KLADNO", 11),
            Map.entry("KLATOVY", 5),
            Map.entry("KOLIN", 11),
            Map.entry("KROMERIZ", 15),
            Map.entry("KUTNA_HORA", 11),
            Map.entry("LIBEREC", 4),
            Map.entry("LITOMERICE", 9),
            Map.entry("LOUNY", 9),
            Map.entry("MELNIK", 11),
            Map.entry("MLADA_BOLESLAV", 11),
            Map.entry("MOST", 9),
            Map.entry("NACHOD", 3),
            Map.entry("NOVY_JICIN", 7),
            Map.entry("NYMBURK", 11),
            Map.entry("OLOMOUC", 12),
            Map.entry("OPAVA", 7),
            Map.entry("OSTRAVA", 7),
            Map.entry("PARDUBICE", 6),
            Map.entry("PELHRIMOV", 14),
            Map.entry("PISEK", 13),
            Map.entry("PLZEN", 5),
            Map.entry("PLZEN_JIH", 5),
            Map.entry("PLZEN_SEVER", 5),
            Map.entry("PRAHA", 10),
            Map.entry("PRAHA_1", 10),
            Map.entry("PRAHA_2", 10),
            Map.entry("PRAHA_3", 10),
            Map.entry("PRAHA_4", 10),
            Map.entry("PRAHA_5", 10),
            Map.entry("PRAHA_6", 10),
            Map.entry("PRAHA_7", 10),
            Map.entry("PRAHA_8", 10),
            Map.entry("PRAHA_9", 10),
            Map.entry("PRAHA_10", 10),
            Map.entry("PRAHA_VYCHOD", 11),
            Map.entry("PRAHA_ZAPAD", 11),
            Map.entry("PRACHATICE", 13),
            Map.entry("PROSTEJOV", 12),
            Map.entry("PREROV", 12),
            Map.entry("PRIBRAM", 11),
            Map.entry("RAKOVNIK", 11),
            Map.entry("ROKYCANY", 5),
            Map.entry("RYCHNOV_NAD_KNEZNOU", 3),
            Map.entry("SEMILY", 4),
            Map.entry("SOKOLOV", 2),
            Map.entry("STRAKONICE", 13),
            Map.entry("SVITAVY", 6),
            Map.entry("SUMPERK", 12),
            Map.entry("TABOR", 13),
            Map.entry("TACHOV", 5),
            Map.entry("TEPLICE", 9),
            Map.entry("TRUTNOV", 3),
            Map.entry("TREBIC", 14),
            Map.entry("UHERSKE_HRADISTE", 15),
            Map.entry("USTI_NAD_LABEM", 9),
            Map.entry("USTI_NAD_ORLICI", 6),
            Map.entry("VSETIN", 15),
            Map.entry("VYSKOV", 1),
            Map.entry("ZLIN", 15),
            Map.entry("ZNOJMO", 1),
            Map.entry("ZDAR_NAD_SAZAVOU", 14)
    );

    private static final Map<String, Integer> SREALITY_DISTRICT_IDS = Map.ofEntries(
            Map.entry("BENESOV", 48),
            Map.entry("BEROUN", 49),
            Map.entry("BLANSKO", 71),
            Map.entry("BRNO", 72),
            Map.entry("BRNO_VENKOV", 73),
            Map.entry("BRUNTAL", 60),
            Map.entry("BRECLAV", 74),
            Map.entry("CESKA_LIPA", 18),
            Map.entry("CESKE_BUDEJOVICE", 1),
            Map.entry("CESKY_KRUMLOV", 2),
            Map.entry("DECIN", 19),
            Map.entry("DOMAZLICE", 8),
            Map.entry("FRYDEK_MISTEK", 61),
            Map.entry("HAVLICKUV_BROD", 66),
            Map.entry("HODONIN", 75),
            Map.entry("HRADEC_KRALOVE", 28),
            Map.entry("CHEB", 9),
            Map.entry("CHOMUTOV", 20),
            Map.entry("CHRUDIM", 29),
            Map.entry("JABLONEC_NAD_NISOU", 21),
            Map.entry("JABLONEC", 21),
            Map.entry("JESENIK", 46),
            Map.entry("JICIN", 30),
            Map.entry("JIHLAVA", 67),
            Map.entry("JINDRICHUV_HRADEC", 3),
            Map.entry("KARLOVY_VARY", 10),
            Map.entry("KARVINA", 62),
            Map.entry("KLADNO", 50),
            Map.entry("KLATOVY", 11),
            Map.entry("KOLIN", 51),
            Map.entry("KROMERIZ", 39),
            Map.entry("KUTNA_HORA", 52),
            Map.entry("LIBEREC", 22),
            Map.entry("LITOMERICE", 23),
            Map.entry("LOUNY", 24),
            Map.entry("MELNIK", 54),
            Map.entry("MLADA_BOLESLAV", 53),
            Map.entry("MOST", 25),
            Map.entry("NACHOD", 31),
            Map.entry("NOVY_JICIN", 63),
            Map.entry("NYMBURK", 55),
            Map.entry("OLOMOUC", 42),
            Map.entry("OPAVA", 64),
            Map.entry("OSTRAVA", 65),
            Map.entry("PARDUBICE", 32),
            Map.entry("PELHRIMOV", 68),
            Map.entry("PISEK", 4),
            Map.entry("PLZEN", 12),
            Map.entry("PLZEN_JIH", 13),
            Map.entry("PLZEN_SEVER", 14),
            Map.entry("PRAHA", 10),
            Map.entry("PRAHA_1", 5001),
            Map.entry("PRAHA_2", 5002),
            Map.entry("PRAHA_3", 5003),
            Map.entry("PRAHA_4", 5004),
            Map.entry("PRAHA_5", 5005),
            Map.entry("PRAHA_6", 5006),
            Map.entry("PRAHA_7", 5007),
            Map.entry("PRAHA_8", 5008),
            Map.entry("PRAHA_9", 5009),
            Map.entry("PRAHA_10", 5010),
            Map.entry("PRAHA_VYCHOD", 56),
            Map.entry("PRAHA_ZAPAD", 57),
            Map.entry("PRACHATICE", 5),
            Map.entry("PROSTEJOV", 40),
            Map.entry("PREROV", 43),
            Map.entry("PRIBRAM", 58),
            Map.entry("RAKOVNIK", 59),
            Map.entry("ROKYCANY", 15),
            Map.entry("RYCHNOV_NAD_KNEZNOU", 33),
            Map.entry("SEMILY", 34),
            Map.entry("SOKOLOV", 16),
            Map.entry("STRAKONICE", 6),
            Map.entry("SVITAVY", 35),
            Map.entry("SUMPERK", 44),
            Map.entry("TABOR", 7),
            Map.entry("TACHOV", 17),
            Map.entry("TEPLICE", 26),
            Map.entry("TRUTNOV", 36),
            Map.entry("TREBIC", 69),
            Map.entry("UHERSKE_HRADISTE", 41),
            Map.entry("USTI_NAD_LABEM", 27),
            Map.entry("USTI_NAD_ORLICI", 37),
            Map.entry("VSETIN", 45),
            Map.entry("VYSKOV", 76),
            Map.entry("ZLIN", 38),
            Map.entry("ZNOJMO", 77),
            Map.entry("ZDAR_NAD_SAZAVOU", 70)
    );
}
