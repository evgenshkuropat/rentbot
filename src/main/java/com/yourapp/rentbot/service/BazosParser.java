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

    private static final String[] KNOWN_PLACES = {
            "Praha", "Praha-východ", "Praha-západ", "Praha vychod", "Praha zapad",
            "Brno", "Ostrava", "Plzeň", "Plzen", "Pardubice", "Olomouc",
            "Liberec", "Zlín", "Zlin", "Most", "Kladno", "Kolín", "Kolin",
            "Kutná Hora", "Kutna Hora", "Ústí nad Labem", "Usti nad Labem",
            "Hradec Králové", "Hradec Kralove", "Jihlava", "Karlovy Vary",
            "Mladá Boleslav", "Mlada Boleslav", "České Budějovice", "Ceske Budejovice",
            "Český Brod", "Cesky Brod", "Nymburk", "Poděbrady", "Podebrady"
    };

    public List<ListingDto> fetchListings(Region region) throws IOException {
        List<ListingDto> result = new ArrayList<>();

        for (String url : buildUrls(region)) {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(15000)
                    .get();

            doc.outputSettings().charset("UTF-8");

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

                if (link.isBlank()) {
                    continue;
                }

                String layout = extractLayout(fullText);
                int price = extractPrice(linkEl, container, fullText);

                String locality = extractLocality(fullText);
                if (locality.isBlank() && region != null && region.getTitle() != null) {
                    locality = region.getTitle();
                }

                String photoUrl = extractPhoto(container);

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
        }

        return dedupeByLink(result);
    }

    private List<String> buildUrls(Region region) {
        String flatUrl;
        String roomUrl;

        if (region == null || region.getCode() == null) {
            flatUrl = BASE_URL + "/pronajmu/byt/";
            roomUrl = BASE_URL + "/pronajmu/pokoj/";
            return List.of(flatUrl, roomUrl);
        }

        String code = region.getCode().toUpperCase();

        flatUrl = switch (code) {
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

        roomUrl = switch (code) {
            case "PRAHA" -> BASE_URL + "/pronajmu/pokoj/?hlokalita=10000&humkreis=25";
            case "BRNO" -> BASE_URL + "/pronajmu/pokoj/?hlokalita=60200&humkreis=20";
            case "OSTRAVA" -> BASE_URL + "/pronajmu/pokoj/?hlokalita=70030&humkreis=20";
            case "PLZEN" -> BASE_URL + "/pronajmu/pokoj/?hlokalita=30100&humkreis=20";
            case "PARDUBICE" -> BASE_URL + "/pronajmu/pokoj/?hlokalita=53002&humkreis=20";
            case "OLOMOUC" -> BASE_URL + "/pronajmu/pokoj/?hlokalita=77900&humkreis=20";
            case "LIBEREC" -> BASE_URL + "/pronajmu/pokoj/?hlokalita=46001&humkreis=20";
            case "KOLIN" -> BASE_URL + "/pronajmu/pokoj/?hlokalita=28002&humkreis=20";
            case "KUTNA_HORA" -> BASE_URL + "/pronajmu/pokoj/?hlokalita=28401&humkreis=20";
            default -> BASE_URL + "/pronajmu/pokoj/";
        };

        return List.of(flatUrl, roomUrl);
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
                "(\\d{1,3}(?:\\s\\d{3})+|\\d{4,6})\\s*(?:Kč|kc)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher strictMatcher = strict.matcher(normalized);
        while (strictMatcher.find()) {
            String raw = strictMatcher.group(1).replaceAll("\\s+", "");
            try {
                int value = Integer.parseInt(raw);
                if (value >= 3000 && value <= 200000) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        Pattern fallback = Pattern.compile(
                "(\\d{1,3}(?:\\s\\d{3})+|\\d{4,6})",
                Pattern.CASE_INSENSITIVE
        );

        Matcher fallbackMatcher = fallback.matcher(normalized);
        while (fallbackMatcher.find()) {
            String raw = fallbackMatcher.group(1).replaceAll("\\s+", "");
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
}