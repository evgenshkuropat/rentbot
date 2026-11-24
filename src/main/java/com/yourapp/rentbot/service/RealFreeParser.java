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
    private static final int MAX_DETAIL_REQUESTS = 3;
    private static final long CACHE_TTL_MS = Duration.ofMinutes(10).toMillis();
    private static final long COOLDOWN_MS = Duration.ofMinutes(15).toMillis();
    private static final Pattern PRICE = Pattern.compile(
            "^(\\d{1,3}(?:[ .]\\d{3})+|\\d+)\\s*(?:Kč|CZK)(?!\\p{L})", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAYOUT = Pattern.compile("\\b(\\d+)\\s*\\+\\s*(kk|\\d+)\\b");
    private static final Pattern COMPACT_LAYOUT = Pattern.compile("\\b(\\d+)\\s*kk\\b");
    private static final Pattern ROOM = Pattern.compile(
            "\\b(spolubydleni|pronajem (?:samostatneho )?pokoje|samostatny pokoj|pokoj k pronajmu)\\b|^pokoj\\b");
    private final Map<String, CacheEntry> cache = new HashMap<>();
    private long blockedUntil;
    private final Map<String, DetailEntry> detailCache = new HashMap<>();

    public synchronized List<ListingDto> fetchListings(Region region) {
        if (region == null || region.getCode() == null || region.getCode().isBlank()) {
            log.info("RealFree skipped reason=missing_region");
            return List.of();
        }
        String city = fold(region.getCode().replace('_', ' ')).trim();
        if (!city.matches("[a-z]+(?:[ -][a-z]+)*")) {
            log.warn("RealFree skipped reason=invalid_region region={}", ParserDiagnostics.text(region.getCode()));
            return List.of();
        }
        long now = System.currentTimeMillis();
        CacheEntry previous = cache.get(city);
        if (previous != null && now - previous.loadedAt() < CACHE_TTL_MS) {
            log.info("RealFree cache region={} mode=fresh ageSeconds={} count={}", city,
                    (now - previous.loadedAt()) / 1000, previous.listings().size());
            return previous.listings();
        }
        if (now < blockedUntil) {
            var fallback = recentCache(previous, now);
            log.info("RealFree skipped region={} reason=cooldown remainingSeconds={} cachedCount={}", city,
                    (blockedUntil - now + 999) / 1000, fallback.size());
            return fallback;
        }
        String catalog = BASE + "/pronajem-bytu-" + city.replace(' ', '-');
        String url = catalog;
        List<ListingDto> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean failed = false;
        DetailBudget details = new DetailBudget(MAX_DETAIL_REQUESTS);
        for (int page = 1; url != null && page <= MAX_PAGES; page++) {
            try {
                if (page > 1 && System.currentTimeMillis() < blockedUntil) { failed = true; break; }
                log.info("RealFree page started region={} page={} url={}", city, page, url);
                Document doc = loadDocument(url);
                if (!sameCatalog(catalog, doc.location())) {
                    log.warn("RealFree skipped reason=unexpected_redirect url={} finalUrl={}", ParserDiagnostics.url(url), ParserDiagnostics.url(doc.location()));
                    // An unsupported city must not pause requests for other cities.
                    break;
                }
                result.addAll(parsePage(doc, city, seen, details));
                String next = catalog + "?page=" + (page + 1);
                url = doc.select("a[href]").stream().anyMatch(a -> a.absUrl("href").equals(next)) ? next : null;
            } catch (IOException e) {
                if (e instanceof org.jsoup.HttpStatusException status
                        && (status.getStatusCode() == 404 || status.getStatusCode() == 410)) {
                    log.warn("RealFree page stopped region={} page={} status={} reason=catalog_not_found url={} partialCount={}",
                            city, page, status.getStatusCode(), ParserDiagnostics.url(url), result.size());
                    break;
                }
                log.warn("RealFree request failed region={} page={} url={} errorType={} partialCount={}",
                        city, page, ParserDiagnostics.url(url), e.getClass().getSimpleName(), result.size());
                failed = true;
                break;
            }
        }
        if (failed) {
            blockedUntil = System.currentTimeMillis() + COOLDOWN_MS;
            if (result.isEmpty()) {
                var fallback = recentCache(previous, now);
                log.warn("RealFree fallback region={} reason=request_failed cooldownSeconds={} cachedCount={}",
                        city, COOLDOWN_MS / 1000, fallback.size());
                return fallback;
            }
            log.warn("RealFree partial region={} count={} cooldownSeconds={}", city, result.size(), COOLDOWN_MS / 1000);
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
        long started = System.nanoTime();
        var response = executeRequest(url);
        int status = response.statusCode();
        log.info("RealFree HTTP url={} finalUrl={} status={} reason={} contentType={} durationMs={}",
                ParserDiagnostics.url(url), ParserDiagnostics.url(response.url().toString()), status,
                ParserDiagnostics.httpReason(status), ParserDiagnostics.text(response.contentType()),
                (System.nanoTime() - started) / 1_000_000);
        if (status >= 400) throw new org.jsoup.HttpStatusException("RealFree HTTP " + status, status,
                ParserDiagnostics.url(response.url().toString()));
        try (var stream = response.bodyStream()) {
            return Jsoup.parse(stream, "UTF-8", response.url().toString());
        }
    }

    org.jsoup.Connection.Response executeRequest(String url) throws IOException {
        return Jsoup.connect(url).userAgent("RentBot/1.0").timeout(15_000).ignoreHttpErrors(true).execute();
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
        return parsePage(doc, city, seen, new DetailBudget(0));
    }

    private List<ListingDto> parsePage(Document doc, String city, Set<String> seen, DetailBudget details) {
        List<ListingDto> result = new ArrayList<>();
        var cards = doc.select("a.rf-card[href]");
        int rejectedCity = 0, rejectedLink = 0, rejectedTitle = 0, rejectedLayout = 0, rejectedPrice = 0, duplicates = 0;
        int navigationCards = 0, listingCards = 0, rejectedInactive = 0;
        String firstRejectedCity = "", firstRejectedPrice = "", firstRejectedLayout = "";
        for (Element card : cards) {
            String link = card.absUrl("href").replaceFirst("[?#].*$", "");
            URI uri;
            try { uri = URI.create(link); } catch (IllegalArgumentException e) { rejectedLink++; continue; }
            if (!"realfree.cz".equalsIgnoreCase(uri.getHost())
                    || !Set.of("http", "https").contains(uri.getScheme())) { rejectedLink++; continue; }
            if (!uri.getPath().startsWith("/inzerat/")) { navigationCards++; continue; }
            listingCards++;
            String locality = text(card, "p:has(i.fa-map-marker-alt)");
            if (!matchesCity(locality, city)) {
                rejectedCity++;
                if (firstRejectedCity.isEmpty()) firstRejectedCity = "locality=" + ParserDiagnostics.text(locality);
                continue;
            }
            String title = text(card, "h2, h3");
            if (title.isBlank()) { rejectedTitle++; continue; }
            String disposition = extractLayout(title);
            String priceText = text(card, "div.font-bold");
            int price = price(priceText);
            if (price <= 0) {
                rejectedPrice++;
                if (firstRejectedPrice.isEmpty()) firstRejectedPrice = "priceText=" + ParserDiagnostics.text(priceText);
                continue;
            }
            if (seen.contains(link)) { duplicates++; continue; }
            if (disposition == null) {
                DetailEntry detail = detailFor(link, details);
                if (detail != null && detail.inactive()) { rejectedInactive++; continue; }
                if (detail != null) disposition = detail.layout();
            }
            if (disposition == null) {
                rejectedLayout++;
                if (firstRejectedLayout.isEmpty()) firstRejectedLayout = ParserDiagnostics.text(title);
                continue;
            }
            if (!seen.add(link)) { duplicates++; continue; }
            Element img = card.selectFirst("img");
            String photo = img == null ? "" : img.absUrl("data-src");
            if (photo.isBlank() && img != null) photo = img.absUrl("src");
            result.add(new ListingDto(title, price, link, disposition, locality, photo,
                    OwnerSignalDetector.sourceWithPossibleOwnerSignal("RealFree", card.text()), LocalDateTime.now()));
        }
        log.info("RealFree page summary region={} url={} cards={} accepted={} rejectedCity={} rejectedLink={} rejectedTitle={} rejectedLayout={} rejectedPrice={} duplicates={} listingCards={} navigationCards={} rejectedInactive={}",
                city, ParserDiagnostics.url(doc.location()), cards.size(), result.size(), rejectedCity, rejectedLink,
                rejectedTitle, rejectedLayout, rejectedPrice, duplicates, listingCards, navigationCards, rejectedInactive);
        if (listingCards == 0) {
            log.warn("RealFree empty page region={} reason=no_matching_cards url={} title={} listingLinks={}", city,
                    ParserDiagnostics.url(doc.location()), ParserDiagnostics.text(doc.title()), doc.select("a[href*=/inzerat/]").size());
        } else if (result.isEmpty()) {
            log.warn("RealFree all cards rejected region={} citySample={} priceSample={} layoutSample={}",
                    city, firstRejectedCity, firstRejectedPrice, firstRejectedLayout);
        }
        return result;
    }

    static String extractLayout(String value) {
        String label = fold(value);
        if (ROOM.matcher(label).find()) return "ROOM";
        Matcher layout = LAYOUT.matcher(label);
        if (layout.find()) return layout.group(1) + "+" + layout.group(2);
        Matcher compact = COMPACT_LAYOUT.matcher(label);
        if (compact.find()) return compact.group(1) + "+kk";
        return label.contains("garson") ? "1+kk" : null;
    }

    private DetailEntry detailFor(String link, DetailBudget budget) {
        long now = System.currentTimeMillis();
        DetailEntry cached = detailCache.get(link);
        if (cached != null && now - cached.loadedAt() < Duration.ofHours(1).toMillis()) {
            log.info("RealFree detail cache url={} layout={} inactive={}", link, cached.layout(), cached.inactive());
            return cached;
        }
        if (budget.remaining <= 0) {
            log.info("RealFree detail skipped reason=detail_budget_or_paused url={}", link);
            return null;
        }
        budget.remaining--;
        try {
            Document detail = loadDocument(link);
            if (!sameCatalog(link, detail.location())) {
                log.warn("RealFree detail skipped reason=unexpected_redirect url={} finalUrl={}", link, ParserDiagnostics.url(detail.location()));
                return null;
            }
            DetailEntry parsed = parseDetail(detail, now);
            detailCache.put(link, parsed);
            log.info("RealFree detail url={} layout={} inactive={}", link, parsed.layout(), parsed.inactive());
            return parsed;
        } catch (IOException e) {
            if (e instanceof org.jsoup.HttpStatusException status && (status.getStatusCode() == 403 || status.getStatusCode() == 429)) {
                budget.remaining = 0;
                blockedUntil = System.currentTimeMillis() + COOLDOWN_MS;
            }
            log.warn("RealFree detail failed url={} errorType={}", link, e.getClass().getSimpleName());
            return null;
        }
    }

    private DetailEntry parseDetail(Document doc, long now) {
        boolean inactive = doc.select("h3").stream().anyMatch(h -> fold(h.text()).contains("inzerat jiz neni aktualni"));
        String layout = null;
        for (Element item : doc.select(".param-item")) {
            if ("dispozice".equals(fold(text(item, ".param-label")))) {
                layout = extractLayout(text(item, ".param-value"));
                break;
            }
        }
        if (layout == null) layout = extractLayout(text(doc, "h1"));
        return new DetailEntry(layout, inactive, now);
    }

    private static class DetailBudget {
        private int remaining;
        private DetailBudget(int remaining) { this.remaining = remaining; }
    }

    private record DetailEntry(String layout, boolean inactive, long loadedAt) { }

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
