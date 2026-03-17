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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IdnesParser {

    private static final String BASE_URL = "https://reality.idnes.cz";
    private static final Pattern LAYOUT_PATTERN =
            Pattern.compile("(\\d+\\s*\\+\\s*(kk|\\d+))", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_PATTERN =
            Pattern.compile("(\\d[\\d\\s]*)\\s*Kč");

    public List<ListingDto> fetchListings(Region region, RegionGroup regionGroup) throws IOException {
        String url = buildSearchUrl(region, regionGroup);

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .get();

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
                locality = title;
            }

            String layout = extractLayout(title);
            String photoUrl = extractPhoto(card);

            if (title == null || title.isBlank()) {
                continue;
            }

            if (!title.toLowerCase(Locale.ROOT).contains("byt")) {
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
            if ("article".equalsIgnoreCase(current.tagName())
                    || current.className().toLowerCase(Locale.ROOT).contains("card")
                    || current.className().toLowerCase(Locale.ROOT).contains("item")
                    || current.className().toLowerCase(Locale.ROOT).contains("offer")) {
                return current;
            }
            current = current.parent();
        }
        return element.parent();
    }

    private String extractTitle(Element card, String wholeText) {
        Element h = card.selectFirst("h2, h3, .c-list-products__title, .c-list-products__name");
        if (h != null && !h.text().isBlank()) {
            return h.text().trim();
        }

        for (String line : wholeText.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ROOT).contains("byt")) {
                return trimmed;
            }
        }

        return wholeText.length() > 120 ? wholeText.substring(0, 120) : wholeText;
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

        String normalized = text.replace('\u00A0', ' ').trim();
        String[] lines = normalized.split("\\R");

        for (String line : lines) {
            String candidate = cleanupLocality(line);
            if (candidate.isBlank()) {
                continue;
            }

            if (looksLikeLocality(candidate)) {
                return candidate;
            }
        }

        String whole = cleanupLocality(normalized);
        if (looksLikeLocality(whole)) {
            return whole;
        }

        return "";
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

        return s.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String extractLayout(String title) {
        if (title == null) {
            return null;
        }

        Matcher m = LAYOUT_PATTERN.matcher(title);
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