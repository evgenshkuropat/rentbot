package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;

@Service
public class BezrealitkyParser {

    private static final Logger log = LoggerFactory.getLogger(BezrealitkyParser.class);

    private static final String BASE_URL = "https://www.bezrealitky.cz";
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_PAGES = 3;

    private static final Pattern PRICE_PATTERN =
            Pattern.compile("(\\d{1,3}(?:[\\s\\u00A0]\\d{3})+)\\s*Kč", Pattern.CASE_INSENSITIVE);

    private static final Pattern LAYOUT_PATTERN =
            Pattern.compile("\\b(\\d+\\+(?:kk|1))\\b", Pattern.CASE_INSENSITIVE);

    public List<ListingDto> fetchListings(Region region) {
        String regionSlug = mapRegionToSlug(region);

        log.info("Bezrealitky slug = {}", regionSlug);

        if (regionSlug == null) {
            return List.of();
        }

        List<String> searchUrls = List.of(
                BASE_URL + "/vypis/nabidka-pronajem/byt/" + regionSlug
        );

        List<ListingDto> result = new ArrayList<>();
        Set<String> seenLinks = new HashSet<>();

        for (String baseSearchUrl : searchUrls) {
            log.info("Bezrealitky URL = {}", baseSearchUrl);

            for (int page = 1; page <= MAX_PAGES; page++) {
                String pageUrl = page == 1 ? baseSearchUrl : baseSearchUrl + "?page=" + page;

                try {
                    byte[] bytes = Jsoup.connect(pageUrl)
                            .userAgent("Mozilla/5.0")
                            .referrer("https://www.google.com/")
                            .timeout(TIMEOUT_MS)
                            .ignoreHttpErrors(true)
                            .execute()
                            .bodyAsBytes();

                    Document doc = Jsoup.parse(
                            new String(bytes, StandardCharsets.UTF_8),
                            pageUrl
                    );

                    List<ListingDto> pageListings = parsePage(doc, seenLinks);

                    log.info("Bezrealitky page {} parsed {} listings", pageUrl, pageListings.size());

                    if (pageListings.isEmpty()) {
                        break;
                    }

                    result.addAll(pageListings);

                } catch (SocketTimeoutException e) {
                    log.warn("Bezrealitky timeout for page {}", pageUrl, e);
                    break;
                } catch (Exception e) {
                    log.warn("Bezrealitky parsing failed for page {}", pageUrl, e);
                    break;
                }
            }
        }

        return result;
    }

    private List<ListingDto> parsePage(Document doc, Set<String> seenLinks) {
        List<ListingDto> result = new ArrayList<>();

        Elements articles = doc.select("article");

        for (Element article : articles) {
            Element linkEl = article.selectFirst("a[href]");
            if (linkEl == null) {
                continue;
            }

            String link = toAbsoluteBezrealitkyUrl(linkEl.attr("href"));
            if (link.isBlank() || !isListingLink(link)) {
                continue;
            }

            if (!seenLinks.add(link)) {
                continue;
            }

            String title = extractTitle(article);
            String fullText = normalizeWhitespace(article.text());

            int price = extractPrice(fullText);
            String layout = extractLayout(title + " " + fullText);
            String locality = extractLocality(title, fullText);
            String photo = extractPhoto(article);

            if (title.isBlank()) {
                title = fallbackTitleFromText(fullText);
            }

            result.add(new ListingDto(
                    title,
                    price,
                    link,
                    layout,
                    locality,
                    photo,
                    "Bezrealitky",
                    LocalDateTime.now()
            ));
        }

        return result;
    }

    private String extractTitle(Element article) {
        String[] selectors = {"h1", "h2", "h3", "[class*=title]", "[class*=headline]"};

        for (String selector : selectors) {
            Element el = article.selectFirst(selector);
            if (el != null) {
                String text = normalizeWhitespace(el.text());
                text = normalizeBezrealitkyTitle(text);

                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        return "";
    }

    private String normalizeBezrealitkyTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }

        return title
                .replaceAll("(?i)(Pronájem bytu)(\\S)", "$1 $2")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String extractPhoto(Element article) {
        Element img = article.selectFirst("img[src]");
        if (img != null) {
            return img.attr("abs:src");
        }

        Element source = article.selectFirst("source[srcset]");
        if (source != null) {
            String srcset = source.attr("srcset");
            if (srcset != null && !srcset.isBlank()) {
                return srcset.split(",")[0].trim().split("\\s+")[0];
            }
        }

        return "";
    }

    private int extractPrice(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        Matcher matcher = PRICE_PATTERN.matcher(text);
        if (matcher.find()) {
            String raw = matcher.group(1).replace("\u00A0", " ").replaceAll("\\s+", "");
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
            }
        }

        return 0;
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

        Matcher matcher = LAYOUT_PATTERN.matcher(lower);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase(Locale.ROOT);
        }

        return null;
    }

    private String extractLocality(String title, String fullText) {
        String source = (title != null && !title.isBlank()) ? title : fullText;
        if (source == null || source.isBlank()) {
            return "";
        }

        int commaIndex = source.indexOf(',');
        if (commaIndex >= 0 && commaIndex + 1 < source.length()) {
            return source.substring(commaIndex + 1).trim();
        }

        return source;
    }

    private String fallbackTitleFromText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }

    private boolean isListingLink(String link) {
        return link.contains("/nemovitosti-byty-domy/")
                || link.contains("/nemovitosti/")
                || link.contains("/vypis/")
                || link.contains("/spolubydleni/")
                || link.contains("/pokoje/");
    }

    private String toAbsoluteBezrealitkyUrl(String href) {
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

    private String normalizeWhitespace(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String mapRegionToSlug(Region region) {

        if (region == null || region.getCode() == null) {
            return "praha";
        }

        return switch (region.getCode().toUpperCase()) {

            case "PRAHA" -> "praha";

            case "KOLIN" -> "okres-kolin";
            case "KUTNA_HORA" -> "okres-kutna-hora";

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
}