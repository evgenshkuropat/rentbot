package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RealFreeParser {
    private static final Logger log = LoggerFactory.getLogger(RealFreeParser.class);
    private static final String BASE = "https://realfree.cz";
    private static final int MAX_PAGES = 3;
    private static final Set<String> CITIES = Set.of("PRAHA", "BRNO", "OSTRAVA", "PLZEN",
            "OLOMOUC", "PARDUBICE", "LIBEREC", "ZLIN", "CESKE_BUDEJOVICE", "USTI_NAD_LABEM",
            "HRADEC_KRALOVE", "KARLOVY_VARY", "JIHLAVA");
    private static final Pattern PRICE = Pattern.compile("^(\\d{1,3}(?:[ .]\\d{3})+|\\d+)\\s*(?:Kč|CZK)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LAYOUT = Pattern.compile("\\b(\\d+)\\s*\\+\\s*(kk|\\d+)\\b");
    private static final Pattern ROOM = Pattern.compile("\\b(spolubydleni|pronajem (?:samostatneho )?pokoje|samostatny pokoj|pokoj k pronajmu)\\b|^pokoj\\b");

    public List<ListingDto> fetchListings(Region region) {
        String code = region == null || region.getCode() == null ? "PRAHA"
                : region.getCode().trim().toUpperCase(Locale.ROOT);
        if (!CITIES.contains(code)) {
            log.warn("RealFree: unsupported region {}", code);
            return List.of();
        }
        String city = code.toLowerCase(Locale.ROOT).replace('_', ' ');
        String catalog = BASE + "/pronajem-bytu-" + city.replace(' ', '-');
        String url = catalog;
        List<ListingDto> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int page = 1; url != null && page <= MAX_PAGES; page++) {
            try {
                Document doc = Jsoup.connect(url).userAgent("RentBot/1.0")
                        .timeout(15_000).get();
                if (!URI.create(catalog).getPath().equals(URI.create(doc.location()).getPath())) {
                    log.warn("RealFree: unexpected redirect from {} to {}", url, doc.location());
                    break;
                }
                List<ListingDto> rows = parsePage(doc, city, seen);
                result.addAll(rows);
                log.info("RealFree: {} -> {} listings", url, rows.size());
                String next = catalog + "?page=" + (page + 1);
                url = doc.select("a[href]").stream().anyMatch(a -> a.absUrl("href").equals(next)) ? next : null;
            } catch (IOException e) {
                log.warn("RealFree: failed to load {}", url, e);
                break;
            }
        }
        return result;
    }

    List<ListingDto> parsePage(Document doc, String city, Set<String> seen) {
        List<ListingDto> result = new ArrayList<>();
        for (Element card : doc.select("a.rf-card[href^=/inzerat/]")) {
            String locality = text(card, "p:has(i.fa-map-marker-alt)");
            // Городская выдача RealFree может включать соседние населённые пункты.
            String place = fold(locality).replaceFirst("^hlavni mesto ", "");
            if (!(place.equals(city) || place.startsWith(city + " - ")
                    || place.matches(Pattern.quote(city) + "\\s+\\d+\\b.*")
                    || place.startsWith(city + "-"))) continue;
            String title = text(card, "h2");
            String link = card.absUrl("href");
            if (title.isBlank() || link.isBlank() || !seen.add(link)) continue;
            String label = fold(title);
            Matcher layout = LAYOUT.matcher(label);
            String disposition = ROOM.matcher(label).find() ? "ROOM"
                    : layout.find() ? layout.group(1) + "+" + layout.group(2) : null;
            Element img = card.selectFirst("img[src]");
            result.add(new ListingDto(title, price(text(card, "div.font-bold")), link,
                    disposition, locality, img == null ? "" : img.absUrl("src"),
                    OwnerSignalDetector.sourceWithPossibleOwnerSignal("RealFree", card.text()),
                    LocalDateTime.now()));
        }
        return result;
    }

    private static int price(String text) {
        Matcher m = PRICE.matcher(text);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1).replace(" ", "").replace(".", "")); }
            catch (NumberFormatException ignored) { /* Не помещается в int у ListingDto. */ }
        }
        return 0; // Неизвестная цена или другая валюта — не выдавать евро за кроны.
    }

    private static String text(Element card, String selector) {
        Element e = card.selectFirst(selector);
        return e == null ? "" : e.text().replaceAll("[\\p{Z}\\s]+", " ").trim();
    }

    private static String fold(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }
}