package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BazosParser {

    private static final String BASE_URL = "https://reality.bazos.cz";

    private static final Pattern LAYOUT_PATTERN =
            Pattern.compile("(\\d+\\s*\\+\\s*(kk|\\d+))", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRICE_PATTERN =
            Pattern.compile("(\\d[\\d\\s]{2,})\\s*Kč", Pattern.CASE_INSENSITIVE);

    public List<ListingDto> fetchListings(Region region) throws IOException {
        String url = buildUrl(region);

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .get();

        List<ListingDto> result = new ArrayList<>();

        Elements links = doc.select("a[href*='/inzerat/']");

        for (Element linkEl : links) {
            String title = linkEl.text().trim();
            if (title.isBlank()) {
                continue;
            }

            Element container = findReasonableContainer(linkEl);
            String containerText = container != null ? container.text() : "";
            String fullText = title + "\n" + containerText;

            String link = extractLink(linkEl);
            String layout = extractLayout(fullText);
            int price = extractPrice(fullText);
            String locality = extractLocality(fullText);
            String photoUrl = extractPhoto(container);

            if (link.isBlank()) {
                continue;
            }

            result.add(new ListingDto(
                    title,
                    price,
                    link,
                    layout,
                    locality,
                    photoUrl,
                    "Bazoš"
            ));
        }

        return dedupeByLink(result);
    }

    private String buildUrl(Region region) {
        if (region == null || region.getCode() == null) {
            return BASE_URL + "/pronajmu/byt/";
        }

        String code = region.getCode().toUpperCase();

        return switch (code) {
            case "PRAHA" -> BASE_URL + "/pronajmu/byt/?hlokalita=10000&humkreis=25";
            case "BRNO" -> BASE_URL + "/pronajmu/byt/?hlokalita=60200&humkreis=20";
            case "OSTRAVA" -> BASE_URL + "/pronajmu/byt/?hlokalita=70030&humkreis=20";
            case "PLZEN" -> BASE_URL + "/pronajmu/byt/?hlokalita=30100&humkreis=20";
            case "PARDUBICE" -> BASE_URL + "/pronajmu/byt/?hlokalita=53002&humkreis=20";
            case "OLOMOUC" -> BASE_URL + "/pronajmu/byt/?hlokalita=77900&humkreis=20";
            case "LIBEREC" -> BASE_URL + "/pronajmu/byt/?hlokalita=46001&humkreis=20";
            case "KOLIN" -> BASE_URL + "/pronajmu/byt/?hlokalita=28002&humkreis=20";
            case "KUTNA_HORA" -> BASE_URL + "/pronajmu/byt/?hlokalita=28401&humkreis=20";
            default -> BASE_URL + "/pronajmu/byt/";
        };
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

    private int extractPrice(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        Matcher m = PRICE_PATTERN.matcher(text);
        while (m.find()) {
            String raw = m.group(1).replaceAll("\\s+", "");
            try {
                int value = Integer.parseInt(raw);
                if (value >= 3000 && value <= 200000) {
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

        String normalized = text.replace('\u00A0', ' ').trim();

        String[] knownPlaces = {
                "Praha", "Brno", "Ostrava", "Plzeň", "Plzen", "Pardubice", "Olomouc",
                "Liberec", "Zlín", "Zlin", "Most", "Kladno", "Kolín", "Kolin",
                "Kutná Hora", "Kutna Hora", "Ústí nad Labem", "Usti nad Labem",
                "Hradec Králové", "Hradec Kralove", "Jihlava", "Karlovy Vary",
                "Mladá Boleslav", "Mlada Boleslav", "České Budějovice", "Ceske Budejovice"
        };

        String[] lines = normalized.split("\\R");

        for (String line : lines) {
            String s = line.trim();
            if (s.isBlank()) continue;

            String extracted = extractKnownPlaceFragment(s, knownPlaces);
            if (!extracted.isBlank()) {
                return extracted;
            }

            int dashIdx = Math.max(s.lastIndexOf(" - "), s.lastIndexOf(" – "));
            if (dashIdx >= 0 && dashIdx + 3 < s.length()) {
                String tail = s.substring(dashIdx + 3).trim();
                extracted = extractKnownPlaceFragment(tail, knownPlaces);
                if (!extracted.isBlank()) {
                    return extracted;
                }
            }
        }

        String extracted = extractKnownPlaceFragment(normalized, knownPlaces);
        return extracted == null ? "" : extracted;
    }

    private String extractLayout(String text) {
        if (text == null || text.isBlank()) {
            return null;
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

    private boolean containsIgnoreCase(String text, String part) {
        return text != null && part != null &&
                text.toLowerCase().contains(part.toLowerCase());
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

    int stop = -1;

    if (comma >= 0) stop = comma;
    if (pipe >= 0 && (stop == -1 || pipe < stop)) stop = pipe;
    if (semicolon >= 0 && (stop == -1 || semicolon < stop)) stop = semicolon;
    if (kc >= 0 && (stop == -1 || kc < stop)) stop = kc;
    if (m2 >= 0 && (stop == -1 || m2 < stop)) stop = m2;
    if (m2alt >= 0 && (stop == -1 || m2alt < stop)) stop = m2alt;

    return stop;
}

    private String cleanupLocality(String s) {
        if (s == null) {
            return "";
        }

        return s.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\-–,\\s]+", "")
                .replaceAll("[\\-–,\\s]+$", "")
                .trim();
    }

private String extractKnownPlaceFragment(String text, String[] knownPlaces) {
    if (text == null || text.isBlank()) {
        return "";
    }

    for (String place : knownPlaces) {
        int idx = indexOfIgnoreCase(text, place);
        if (idx >= 0) {
            int end = Math.min(text.length(), idx + place.length() + 30);
            String candidate = text.substring(idx, end);

            int stop = findFirstStop(candidate);
            if (stop > 0) {
                candidate = candidate.substring(0, stop);
            }

            return cleanupLocality(candidate);
        }
    }

    return "";
}
}