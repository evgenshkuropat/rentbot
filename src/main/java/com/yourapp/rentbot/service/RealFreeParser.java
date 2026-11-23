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
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RealFreeParser {
    private static final Logger log = LoggerFactory.getLogger(RealFreeParser.class);
    private static final String BASE = "https://realfree.cz";
    private static final int MAX_PAGES = 3;
    private static final long CACHE_TTL_MS = Duration.ofMinutes(10).toMillis();
    private static final long COOLDOWN_MS = Duration.ofMinutes(15).toMillis();
    private static final Pattern PRICE = Pattern.compile(
            "^(\\d{1,3}(?:[ .]\\d{3})+|\\d+)\\s*(?:Kč|CZK)(?!\\p{L})", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAYOUT = Pattern.compile("\\b(\\d+)\\s*\\+\\s*(kk|\\d+)\\b");
    private static final Pattern ROOM = Pattern.compile(
            "\\b(spolubydleni|pronajem (?:samostatneho )?pokoje|samostatny pokoj|pokoj k pronajmu)\\b|^pokoj\\b");
    private final Map<String, CacheEntry> cache = new HashMap<>();
    private long blockedUntil;

    public synchronized List<ListingDto> fetchListings(Region region) {
        if (region == null || region.getCode() == null || region.getCode().isBlank()) return List.of();
        String city = fold(region.getCode().replace('_', ' ')).trim();
        if (!city.matches("[a-z]+(?:[ -][a-z]+)*")) return List.of();
        long now = System.currentTimeMillis();
        CacheEntry previous = cache.get(city);
        if (previous != null && now - previous.loadedAt() < CACHE_TTL_MS) return previous.listings();
        if (now < blockedUntil) return recentCache(previous, now);
        String catalog = BASE + "/pronajem-bytu-" + city.replace(' ', '-');
        String url = catalog;
        List<ListingDto> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean failed = false;
        for (int page = 1; url != null && page <= MAX_PAGES; page++) {
            try {
                Document doc = loadDocument(url);
                if (!sameCatalog(catalog, doc.location())) {
                    log.warn("RealFree: unexpected redirect from {} to {}", url, doc.location());
                    // An unsupported city must not pause requests for other cities.
                    break;
                }
                result.addAll(parsePage(doc, city, seen));
                String next = catalog + "?page=" + (page + 1);
                url = doc.select("a[href]").stream().anyMatch(a -> a.absUrl("href").equals(next)) ? next : null;
            } catch (IOException e) {
                if (e instanceof org.jsoup.HttpStatusException status
                        && (status.getStatusCode() == 404 || status.getStatusCode() == 410)) {
                    break;
                }
                log.warn("RealFree: failed to load {}: {}", url, e.getMessage());
                failed = true;
                break;
            }
        }
        if (failed) {
            blockedUntil = System.currentTimeMillis() + COOLDOWN_MS;
            if (result.isEmpty()) return recentCache(previous, now);
            List<ListingDto> partial = List.copyOf(result);
            cache.put(city, new CacheEntry(partial, now - CACHE_TTL_MS));
            return partial;
        }
        List<ListingDto> snapshot = List.copyOf(result);
        cache.put(city, new CacheEntry(snapshot, System.currentTimeMillis()));
        log.info("RealFree listings region={} count={}", region.getCode(), snapshot.size());
        return snapshot;
    }

    Document loadDocument(String url) throws IOException {
        return Jsoup.connect(url).userAgent("RentBot/1.0").timeout(15_000).get();
    }

    private List<ListingDto> recentCache(CacheEntry entry, long now) {
        return entry != null && now - entry.loadedAt() < Duration.ofHours(1).toMillis()
                ? entry.listings() : List.of();
    }

    private boolean sameCatalog(String expected, String actual) {
        URI uri = URI.create(actual);
        return "realfree.cz".equalsIgnoreCase(uri.getHost())
                && URI.create(expected).getPath().equals(uri.getPath().replaceFirst("/$", ""));
    }

    List<ListingDto> parsePage(Document doc, String city, Set<String> seen) {
        List<ListingDto> result = new ArrayList<>();
        for (Element card : doc.select("a.rf-card[href]")) {
            String locality = text(card, "p:has(i.fa-map-marker-alt)");
            if (!matchesCity(locality, city)) continue;
            String title = text(card, "h2, h3");
            String link = card.absUrl("href").replaceFirst("[?#].*$", "");
            URI uri;
            try { uri = URI.create(link); } catch (IllegalArgumentException e) { continue; }
            if (!"realfree.cz".equalsIgnoreCase(uri.getHost()) || !uri.getPath().startsWith("/inzerat/")
                    || !Set.of("http", "https").contains(uri.getScheme()) || title.isBlank()) continue;
            String label = fold(title);
            Matcher layout = LAYOUT.matcher(label);
            String disposition = ROOM.matcher(label).find() ? "ROOM"
                    : layout.find() ? layout.group(1) + "+" + layout.group(2)
                    : label.contains("garson") ? "1+kk" : null;
            int price = price(text(card, "div.font-bold"));
            if (disposition == null || price <= 0 || !seen.add(link)) continue;
            Element img = card.selectFirst("img");
            String photo = img == null ? "" : img.absUrl("data-src");
            if (photo.isBlank() && img != null) photo = img.absUrl("src");
            result.add(new ListingDto(title, price, link, disposition, locality, photo,
                    OwnerSignalDetector.sourceWithPossibleOwnerSignal("RealFree", card.text()), LocalDateTime.now()));
        }
        return result;
    }

    private boolean matchesCity(String locality, String city) {
        String place = fold(locality).replaceFirst("^hlavni mesto ", "")
                .replaceAll("[–—]", "-").replaceAll("\\s+", " ").trim();
        String expected = fold(city).replace('_', ' ').trim();
        if ("praha".equals(expected) && place.matches("praha\\s*-?\\s*(vychod|zapad)\\b.*")) return false;
        return place.equals(expected) || place.startsWith(expected + ",") || place.startsWith(expected + " -")
                || place.startsWith(expected + "-") || place.matches(Pattern.quote(expected) + "\\s+\\d+\\b.*");
    }

    private static int price(String text) {
        Matcher m = PRICE.matcher(text);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1).replace(" ", "").replace(".", "")); }
            catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }

    private static String text(Element card, String selector) {
        Element e = card.selectFirst(selector);
        return e == null ? "" : e.text().replaceAll("[\\p{Z}\\s]+", " ").trim();
    }

    private static String fold(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private record CacheEntry(List<ListingDto> listings, long loadedAt) { }
}
