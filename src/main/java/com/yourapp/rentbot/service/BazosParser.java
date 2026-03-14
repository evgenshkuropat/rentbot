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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BazosParser {

    private static final String BASE_URL = "https://reality.bazos.cz";
    private static final Pattern LAYOUT_PATTERN =
            Pattern.compile("(\\d+\\s*\\+\\s*(kk|\\d+))", Pattern.CASE_INSENSITIVE);

    public List<ListingDto> fetchListings(Region region) throws IOException {
        String url = buildUrl(region);

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .get();

        List<ListingDto> result = new ArrayList<>();

        // Bazoš сейчас показывает заголовки объявлений как h2-секции в списке. :contentReference[oaicite:1]{index=1}
        Elements headings = doc.select("h2");

        for (Element h2 : headings) {
            String title = h2.text().trim();
            if (title.isBlank()) {
                continue;
            }

            Element container = findReasonableContainer(h2);
            String blockText = container != null ? container.text() : title;

            String link = extractLink(h2, container);
            int price = extractPrice(blockText);
            String locality = extractLocality(blockText);
            String layout = extractLayout(title);
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
                    photoUrl
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
            default -> BASE_URL + "/pronajmu/byt/";
        };
    }

    private Element findReasonableContainer(Element h2) {
        Element current = h2;
        for (int i = 0; i < 4 && current != null; i++) {
            current = current.parent();
            if (current != null && current.text().length() > 80) {
                return current;
            }
        }
        return h2.parent();
    }

    private String extractLink(Element h2, Element container) {
        Element linkEl = h2.selectFirst("a[href]");
        if (linkEl == null && container != null) {
            linkEl = container.selectFirst("a[href]");
        }
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

        // ищем строку вида "19 990 Kč"
        Matcher m = Pattern.compile("(\\d[\\d\\s]{2,})\\s*Kč").matcher(text);
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

        // Часто у Bazoš локалита идёт отдельной строкой, как "Praha 5", "Bruntál", "Ostrava". :contentReference[oaicite:2]{index=2}
        String[] lines = text.split("\\R");
        for (String line : lines) {
            String s = line.trim();
            if (s.isBlank()) continue;
            if (s.matches(".*\\d+\\s*Kč.*")) continue;
            if (s.matches("\\d{3}\\s?\\d{2}")) continue; // PSČ
            if (s.length() > 1 && s.length() < 40 && !s.equalsIgnoreCase("TOP")) {
                if (s.toLowerCase().contains("praha")
                        || s.toLowerCase().contains("brno")
                        || s.toLowerCase().contains("ostrava")
                        || s.toLowerCase().contains("plze")
                        || s.toLowerCase().contains("pardubice")
                        || s.toLowerCase().contains("olomouc")
                        || s.toLowerCase().contains("liberec")
                        || s.toLowerCase().contains("zlín")
                        || s.toLowerCase().contains("most")
                        || s.toLowerCase().contains("kladno")
                        || s.toLowerCase().contains("kolín")
                        || s.toLowerCase().contains("kutná hora")) {
                    return s;
                }
            }
        }

        return "";
    }

    private String extractLayout(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }

        Matcher m = LAYOUT_PATTERN.matcher(title);
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
        List<ListingDto> result = new ArrayList<>();
        List<String> seen = new ArrayList<>();

        for (ListingDto dto : input) {
            if (dto.link() == null || dto.link().isBlank()) {
                continue;
            }
            if (seen.contains(dto.link())) {
                continue;
            }
            seen.add(dto.link());
            result.add(dto);
        }

        return result;
    }
}