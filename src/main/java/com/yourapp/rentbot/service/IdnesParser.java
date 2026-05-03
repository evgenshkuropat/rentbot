package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.RegionGroup;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IdnesParser {

    private static final String BASE_URL = "https://reality.idnes.cz";

    private static final Pattern LAYOUT_PATTERN =
            Pattern.compile("(\\d+\\s*\\+\\s*(kk|\\d+))", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRICE_PATTERN =
            Pattern.compile("(\\d[\\d\\s]*)\\s*Kč", Pattern.CASE_INSENSITIVE);

    private static final String[] KNOWN_PLACES = {
            "Praha", "Brno", "Ostrava", "Plzeň", "Plzen", "Pardubice", "Olomouc",
            "Liberec", "Zlín", "Zlin", "Kolín", "Kolin", "Kutná Hora", "Kutna Hora",
            "Ústí nad Labem", "Usti nad Labem", "Hradec Králové", "Hradec Kralove",
            "Jihlava", "Karlovy Vary", "České Budějovice", "Ceske Budejovice",
            "Mladá Boleslav", "Mlada Boleslav"
    };

    public List<ListingDto> fetchListings(Region region, RegionGroup regionGroup) throws IOException {
        String url = buildSearchUrl(region, regionGroup);

        var response = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .ignoreHttpErrors(true)
                .execute();

        Document doc = Jsoup.parse(
                response.bodyStream(),
                "UTF-8",
                url
        );

        List<ListingDto> result = new ArrayList<>();

        Elements links = doc.select("a[href*='/detail/'], a[href*='/s/detail/'], a[href*='/s/pronajem/']");

        for (Element linkEl : links) {
            String href = linkEl.attr("href");
            if (href == null || href.isBlank()) {
                continue;
            }

            String absoluteLink = href.startsWith("http") ? href : BASE_URL + href;

            Element card = findCardContainer(linkEl);
            if (card == null) {
                continue;
            }

            String wholeText = card.text();
            if (wholeText == null || wholeText.isBlank()) {
                continue;
            }

            String title = extractTitle(card, wholeText);
            int priceCzk = extractPrice(wholeText);
            String locality = extractLocality(wholeText);

            if (locality.isBlank()) {
                locality = extractLocality(title);
            }

            String layout = extractLayout(title);
            if (layout == null) {
                layout = extractLayout(wholeText);
            }

            String photoUrl = extractPhoto(card);

            if (title == null || title.isBlank()) {
                continue;
            }

            String lowerTitle = title.toLowerCase(Locale.ROOT);
            String lowerWholeText = wholeText.toLowerCase(Locale.ROOT);

            boolean isRoom = lowerTitle.contains("pokoj")
                    || lowerWholeText.contains("pokoj")
                    || lowerWholeText.contains("spolubydlení")
                    || lowerWholeText.contains("spolubydleni");

            if (!lowerTitle.contains("byt") && !isRoom) {
                continue;
            }

            result.add(new ListingDto(
                    title,
                    priceCzk,
                    absoluteLink,
                    layout,
                    locality,
                    photoUrl,
                    "iDNES"
            ));
        }

        return dedupeByLink(result);
    }

    private String buildSearchUrl(Region region, RegionGroup regionGroup) {
        if (region == null || region.getCode() == null) {
            return BASE_URL + "/s/pronajem/byty/";
        }

        String code = region.getCode();

        if ("PRAHA".equals(code)) {
            if (regionGroup == null || regionGroup.getCode() == null || "PRAHA_ALL".equals(regionGroup.getCode())) {
                return BASE_URL + "/s/pronajem/byty/praha/";
            }

            return switch (regionGroup.getCode()) {
                case "PRAHA_1_3" -> BASE_URL + "/s/pronajem/byty/praha-1/";
                case "PRAHA_4_6" -> BASE_URL + "/s/pronajem/byty/praha-4/";
                case "PRAHA_7_10" -> BASE_URL + "/s/pronajem/byty/praha-7/";
                case "PRAHA_11_15" -> BASE_URL + "/s/pronajem/byty/praha-10/";
                default -> BASE_URL + "/s/pronajem/byty/praha/";
            };
        }

        String citySlug = regionCodeToIdnesSlug(code);
        return BASE_URL + "/s/pronajem/byty/" + citySlug + "/";
    }

    private String regionCodeToIdnesSlug(String code) {
        return switch (code) {
            case "BRNO" -> "brno";
            case "OSTRAVA" -> "ostrava";
            case "PLZEN" -> "plzen";
            case "LIBEREC" -> "liberec";
            case "OLOMOUC" -> "olomouc";
            case "HRADEC_KRALOVE" -> "hradec-kralove";
            case "PARDUBICE" -> "pardubice";
            case "CESKE_BUDEJOVICE" -> "ceske-budejovice";
            case "ZLIN" -> "zlin";
            case "JIHLAVA" -> "jihlava";
            case "USTI_NAD_LABEM" -> "usti-nad-labem";
            case "KARLOVY_VARY" -> "karlovy-vary";
            case "MLADA_BOLESLAV" -> "mlada-boleslav";
            case "KOLIN" -> "kolin";
            case "KUTNA_HORA" -> "kutna-hora";
            default -> "";
        };
    }

    private Element findCardContainer(Element element) {
        Element current = element;
        for (int i = 0; i < 6 && current != null; i++) {
            String cls = current.className() == null ? "" : current.className().toLowerCase(Locale.ROOT);
            if ("article".equalsIgnoreCase(current.tagName())
                    || cls.contains("card")
                    || cls.contains("item")
                    || cls.contains("offer")) {
                return current;
            }
            current = current.parent();
        }
        return element.parent();
    }

    private String extractTitle(Element card, String wholeText) {
        Element h = card.selectFirst("h2, h3, .c-list-products__title, .c-list-products__name");
        if (h != null && !h.text().isBlank()) {
            return cleanupTitle(h.text());
        }

        String normalized = normalizeText(wholeText);

        String[] lines = normalized.split("\\R");
        for (String line : lines) {
            String trimmed = cleanupTitle(line);
            if (trimmed.toLowerCase(Locale.ROOT).contains("byt")) {
                return trimmed;
            }
        }

        return cleanupTitle(normalized.length() > 120 ? normalized.substring(0, 120) : normalized);
    }

    private String cleanupTitle(String s) {
        if (s == null) {
            return "";
        }

        String cleaned = normalizeText(s)
                .replaceAll("(?i)^nov[ýe]?\\s+", "")
                .replaceAll("(?i)^zlevn[eě]no\\s+", "")
                .replaceAll("(?i)^rezervov[aá]no\\s+", "")
                .trim();

        return cleaned;
    }

    private int extractPrice(String text) {
        Matcher m = PRICE_PATTERN.matcher(text.replace('\u00A0', ' '));
        if (!m.find()) {
            return 0;
        }

        String raw = m.group(1).replaceAll("\\s+", "");
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractLocality(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = normalizeText(text);

        String best = extractKnownPlaceFragment(normalized);
        if (!best.isBlank()) {
            return best;
        }

        String[] lines = text.split("\\R");
        for (String line : lines) {
            String candidate = extractKnownPlaceFragment(normalizeText(line));
            if (!candidate.isBlank()) {
                return candidate;
            }
        }

        return "";
    }

    private String extractKnownPlaceFragment(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String best = "";

        for (String place : KNOWN_PLACES) {
            int idx = indexOfIgnoreCase(text, place);
            if (idx < 0) {
                continue;
            }

            String tail = text.substring(idx);

            int stop = findFirstHardStop(tail);
            if (stop > 0) {
                tail = tail.substring(0, stop);
            }

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

    private int findFirstHardStop(String s) {
        String lower = s.toLowerCase(Locale.ROOT);

        int stop = -1;

        String[] stopWords = {
                " Kč", " Kc", " m²", " m2",
                "pronájem bytu", "pronajem bytu",
                "nový pronájem", "novy pronajem",
                "zlevněno", "zlevneno",
                "rezervováno", "rezervovano",
                "byt ",
                "detail",
                "|"
        };

        for (String stopWord : stopWords) {
            int idx = lower.indexOf(stopWord.toLowerCase(Locale.ROOT));
            if (idx >= 0 && (stop == -1 || idx < stop)) {
                stop = idx;
            }
        }

        int comma = s.indexOf(',');
        if (comma >= 0 && (stop == -1 || comma < stop)) {
            stop = comma;
        }

        return stop;
    }

    private String extractLocalityWindow(String tail) {
        if (tail == null || tail.isBlank()) {
            return "";
        }

        Pattern p = Pattern.compile(
                "^([A-Za-zÀ-ž0-9\\-\\.]+(?:\\s+[A-Za-zÀ-ž0-9\\-\\.]+){0,6}(?:\\s*[-–]\\s*[A-Za-zÀ-ž0-9\\-\\.]+(?:\\s+[A-Za-zÀ-ž0-9\\-\\.]+){0,4})?)"
        );

        Matcher m = p.matcher(tail.trim());
        if (m.find()) {
            return m.group(1);
        }

        return tail;
    }

    private boolean looksLikeLocality(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }

        String lower = s.toLowerCase(Locale.ROOT);

        return lower.contains("praha")
                || lower.contains("brno")
                || lower.contains("ostrava")
                || lower.contains("plze")
                || lower.contains("liberec")
                || lower.contains("olomouc")
                || lower.contains("pardubice")
                || lower.contains("kolín")
                || lower.contains("kolin")
                || lower.contains("kutná hora")
                || lower.contains("kutna hora")
                || lower.contains("ústí nad labem")
                || lower.contains("usti nad labem")
                || lower.contains("hradec králové")
                || lower.contains("hradec kralove")
                || lower.contains("jihlava")
                || lower.contains("karlovy vary")
                || lower.contains("české budějovice")
                || lower.contains("ceske budejovice")
                || lower.contains("zlín")
                || lower.contains("zlin")
                || lower.contains("mladá boleslav")
                || lower.contains("mlada boleslav");
    }

    private String cleanupLocality(String s) {
        if (s == null) {
            return "";
        }

        String cleaned = normalizeText(s)
                .replaceAll("(?i)^nov[ýe]?\\s+", "")
                .replaceAll("(?i)^pron[aá]jem bytu\\s+", "")
                .replaceAll("(?i)^zlevn[eě]no\\s+", "")
                .replaceAll("(?i)^rezervov[aá]no\\s+", "")
                .replaceAll("(?i)\\b\\d+\\+kk\\b", " ")
                .replaceAll("(?i)\\b\\d+\\+1\\b", " ")
                .replaceAll("(?i)\\b\\d+\\s*m²\\b", " ")
                .replaceAll("(?i)\\b\\d+\\s*m2\\b", " ")
                .replaceAll("(?i)\\b\\d{4,6}\\s*kč.*$", " ")
                .replaceAll("\\b\\d{1,3}(?:\\s\\d{3})\\b$", "")
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\-–,.;:\\s]+", "")
                .replaceAll("[\\-–,.;:\\s]+$", "")
                .trim();

        if (!looksLikeLocality(cleaned)) {
            return "";
        }

        return cleaned;
    }

    private String extractLayout(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String lower = text.toLowerCase(Locale.ROOT);

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

        return m.group(1).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String extractPhoto(Element card) {
        Element img = card.selectFirst("img");
        if (img == null) {
            return null;
        }

        String src = img.hasAttr("src") ? img.attr("src") : img.attr("data-src");
        if (src == null || src.isBlank()) {
            return null;
        }

        if (src.startsWith("//")) {
            return "https:" + src;
        }
        if (src.startsWith("/")) {
            return BASE_URL + src;
        }
        return src;
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

    private String normalizeText(String s) {
        if (s == null) {
            return "";
        }

        return s.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int indexOfIgnoreCase(String text, String part) {
        if (text == null || part == null) {
            return -1;
        }
        return text.toLowerCase(Locale.ROOT).indexOf(part.toLowerCase(Locale.ROOT));
    }
}