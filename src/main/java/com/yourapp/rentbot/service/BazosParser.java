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
        if (m.find()) {
            String raw = m.group(1).replaceAll("\\s+", "");
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
            }
        }

        return 0;
    }

    private String extractLocality(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String lower = text.toLowerCase();

        String[] knownPlaces = {
                "praha", "brno", "ostrava", "plzen", "plzeň", "pardubice", "olomouc",
                "liberec", "zlin", "zlín", "most", "kladno", "kolin", "kolín",
                "kutna hora", "kutná hora"
        };

        String[] lines = text.split("\\R");
        for (String line : lines) {
            String s = line.trim();
            if (s.isBlank()) continue;
            if (s.length() > 80) continue;
            if (s.matches(".*\\d+\\s*Kč.*")) continue;

            String normalized = s.toLowerCase();
            for (String place : knownPlaces) {
                if (normalized.contains(place)) {
                    return s;
                }
            }
        }

        for (String place : knownPlaces) {
            if (lower.contains(place)) {
                return place;
            }
        }

        return "";
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
}