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

@Service
public class BezrealitkyParser {

    public List<ListingDto> fetchListings(Region region) throws IOException {
        String citySlug = mapRegionToCitySlug(region);

        if (citySlug == null) {
            return List.of();
        }

        String url = "https://www.bezrealitky.cz/vypis/nabidka-pronajem/byt/" + citySlug;

        List<ListingDto> result = new ArrayList<>();

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .get();

        Elements listings = doc.select("article");

        if (listings.isEmpty()) {
            return List.of();
        }

        for (Element e : listings) {
            String title = e.select("h2").text().trim();
            if (title.isBlank()) {
                continue;
            }

            String priceText = e.text().replaceAll("[^0-9]", " ").trim();
            int price = extractFirstReasonablePrice(priceText);

            Element linkEl = e.selectFirst("a[href]");
            String link = linkEl != null ? toAbsoluteBezrealitkyUrl(linkEl.attr("href")) : "";

            Element imgEl = e.selectFirst("img[src]");
            String photo = imgEl != null ? imgEl.attr("src") : "";

            String locality = extractLocality(title, e.text());
            String layout = extractLayout(title);

            if (link.isBlank()) {
                continue;
            }

            result.add(new ListingDto(
                    title,
                    price,
                    link,
                    layout,
                    locality,
                    photo,
                    "Bezrealitky"
            ));
        }

        return result;
    }

    private String mapRegionToCitySlug(Region region) {
        if (region == null || region.getCode() == null) {
            return "praha";
        }

        return switch (region.getCode().toUpperCase()) {
            case "PRAHA" -> "praha";
            case "BRNO" -> "brno";
            case "OSTRAVA" -> "ostrava";
            case "PLZEN" -> "plzen";
            case "OLOMOUC" -> "olomouc";
            case "PARDUBICE" -> "pardubice";
            case "LIBEREC" -> "liberec";
            case "ZLIN" -> "zlin";
            case "CESKE_BUDEJOVICE" -> "ceske-budejovice";
            case "USTI_NAD_LABEM" -> "usti-nad-labem";
            case "HRADEC_KRALOVE" -> "hradec-kralove";
            case "KARLOVY_VARY" -> "karlovy-vary";
            case "JIHLAVA" -> "jihlava";
            default -> null;
        };
    }

    private String toAbsoluteBezrealitkyUrl(String href) {
        if (href == null || href.isBlank()) {
            return "";
        }
        if (href.startsWith("http")) {
            return href;
        }
        return "https://www.bezrealitky.cz" + href;
    }

    private int extractFirstReasonablePrice(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        String[] parts = text.split("\\s+");

        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value >= 1000) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return 0;
    }

    private String extractLayout(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }

        String lower = title.toLowerCase();

        for (int rooms = 1; rooms <= 10; rooms++) {
            String kk = rooms + "+kk";
            String one = rooms + "+1";

            if (lower.contains(kk)) {
                return kk;
            }
            if (lower.contains(one)) {
                return one;
            }
        }

        return null;
    }

    private String extractLocality(String title, String fallbackText) {
        if (title != null && !title.isBlank()) {
            return title;
        }
        return fallbackText == null ? "" : fallbackText;
    }
}