package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DigiRealityParser {

    private static final Logger log = LoggerFactory.getLogger(DigiRealityParser.class);

    private static final String RSS_URL = "https://www.digireality.cz/Home/Rss";
    private static final Pattern PRICE_PATTERN = Pattern.compile(
            "(?iu)cena\\s*:\\s*(\\d{1,3}(?:[\\s\\u00A0.]\\d{3})+|\\d+)\\s*k[čc]"
    );
    private static final Pattern LAYOUT_PATTERN = Pattern.compile(
            "(?iu)\\b(\\d+\\s*\\+\\s*(?:kk|1))\\b"
    );

    private static final List<String> OWNER_SIGNALS = List.of(
            "primo od majitele",
            "primo majitel",
            "primo majitelem",
            "primy majitel",
            "primym majitelem",
            "pronajimam svuj",
            "pronajmu svuj",
            "pronajimam vlastni",
            "majitel pronajme",
            "soukromy majitel",
            "jsem majitel",
            "od primeho majitele",
            "bez realitky",
            "bez rk",
            "bez provize"
    );

    private static final List<String> AGENCY_SIGNALS = List.of(
            "provize rk",
            "provize realitni",
            "provize zprostredkovatele",
            "realitni kancelar",
            "realitni makler",
            "kontaktujte maklere",
            "zprostredkujeme",
            "zprostredkuji",
            "v zastoupeni majitele",
            "vyhradnim zastoupeni"
    );

    private final Object cacheLock = new Object();
    private final long cacheTtlMillis;
    private volatile CacheEntry cache = new CacheEntry(List.of(), 0L);

    public DigiRealityParser(
            @Value("${rentbot.digireality.cache-ttl-ms:${RENTBOT_DIGIREALITY_CACHE_TTL_MS:600000}}")
            long cacheTtlMillis) {
        this.cacheTtlMillis = Math.max(Duration.ofMinutes(1).toMillis(), cacheTtlMillis);
    }

    public List<ListingDto> fetchListings(Region region) throws IOException {
        List<ListingDto> result = toListingsForRegion(loadListings(), region);
        if (result.isEmpty()) {
            log.debug("DigiReality owner listings region={} count=0", regionTitle(region));
        } else {
            log.info("DigiReality owner listings region={} count={}", regionTitle(region), result.size());
        }
        return result;
    }

    List<ListingDto> toListingsForRegion(List<RssListing> listings, Region region) {
        List<ListingDto> result = new ArrayList<>();

        for (RssListing listing : listings) {
            String locality = localityForRegion(listing, region);
            if (locality == null) {
                continue;
            }

            result.add(new ListingDto(
                    listing.title(),
                    listing.priceCzk(),
                    listing.link(),
                    listing.layout(),
                    locality,
                    listing.photoUrl(),
                    "DigiReality Owner",
                    listing.foundAt()
            ));
        }

        return result;
    }

    List<RssListing> parseRss(String xml) {
        return parseRssWithDiagnostics(xml).listings();
    }

    private ParseResult parseRssWithDiagnostics(String xml) {
        if (xml == null || xml.isBlank()) {
            return new ParseResult(List.of(), new ParseDiagnostics());
        }

        Document document = Jsoup.parse(xml, RSS_URL, Parser.xmlParser());
        List<RssListing> result = new ArrayList<>();
        ParseDiagnostics diagnostics = new ParseDiagnostics();

        for (Element item : document.select("item")) {
            diagnostics.total++;
            String title = elementText(item, "title");
            String link = elementText(item, "link");
            String descriptionHtml = elementText(item, "description");
            String description = normalizeWhitespace(Jsoup.parse(descriptionHtml).text());
            String searchable = normalizeForMatch(title + " " + description);

            if (link.isBlank()) {
                diagnostics.blankLink++;
                continue;
            }
            if (!isRentalApartment(searchable)) {
                diagnostics.notRentalApartment++;
                continue;
            }
            diagnostics.rentalApartments++;

            if (searchable.contains("bezrealitky")) {
                diagnostics.bezrealitkyDuplicates++;
                continue;
            }
            if (!hasOwnerSignal(searchable)) {
                diagnostics.withoutOwnerSignal++;
                continue;
            }
            diagnostics.withOwnerSignal++;

            if (hasAgencySignal(searchable)) {
                diagnostics.agencySignal++;
                continue;
            }

            int price = extractPrice(title + " " + description);
            String layout = extractLayout(title + " " + description);
            if (price < 3_000 || price > 60_000) {
                diagnostics.invalidPrice++;
                continue;
            }
            if (layout == null) {
                diagnostics.missingLayout++;
                continue;
            }

            result.add(new RssListing(
                    title,
                    link,
                    description,
                    price,
                    layout,
                    extractPhoto(item),
                    extractFoundAt(item)
            ));
            diagnostics.accepted++;
        }

        return new ParseResult(List.copyOf(result), diagnostics);
    }

    private List<RssListing> loadListings() throws IOException {
        long now = System.currentTimeMillis();
        CacheEntry current = cache;
        if (current.loadedAtMillis() > 0 && now - current.loadedAtMillis() < cacheTtlMillis) {
            return current.listings();
        }

        synchronized (cacheLock) {
            current = cache;
            now = System.currentTimeMillis();
            if (current.loadedAtMillis() > 0 && now - current.loadedAtMillis() < cacheTtlMillis) {
                return current.listings();
            }

            try {
                var response = Jsoup.connect(RSS_URL)
                        .userAgent("Mozilla/5.0 (compatible; RentBot/1.0; +https://t.me/)")
                        .header("Accept", "application/rss+xml, application/xml;q=0.9, text/xml;q=0.8")
                        .timeout(15_000)
                        .ignoreContentType(true)
                        .ignoreHttpErrors(true)
                        .execute();

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("DigiReality RSS HTTP " + response.statusCode());
                }

                String xml = new String(response.bodyAsBytes(), StandardCharsets.UTF_8);
                ParseResult parseResult = parseRssWithDiagnostics(xml);
                List<RssListing> parsed = parseResult.listings();
                cache = new CacheEntry(parsed, now);
                ParseDiagnostics d = parseResult.diagnostics();
                log.info(
                        "DigiReality RSS diagnostics total={} rentalApartments={} notRentalApartment={} bezrealitkyDuplicates={} "
                                + "withoutOwnerSignal={} withOwnerSignal={} agencySignal={} invalidPrice={} "
                                + "missingLayout={} blankLink={} accepted={}",
                        d.total,
                        d.rentalApartments,
                        d.notRentalApartment,
                        d.bezrealitkyDuplicates,
                        d.withoutOwnerSignal,
                        d.withOwnerSignal,
                        d.agencySignal,
                        d.invalidPrice,
                        d.missingLayout,
                        d.blankLink,
                        d.accepted
                );
                return parsed;
            } catch (IOException e) {
                if (current.loadedAtMillis() > 0) {
                    log.warn("DigiReality RSS refresh failed; using stale cache: {}", e.getMessage());
                    return current.listings();
                }
                throw e;
            }
        }
    }

    private boolean isRentalApartment(String text) {
        boolean rental = text.contains("pronajem") || text.contains("pronajmu") || text.contains("pronajimam");
        boolean apartment = text.matches(".*\\bbyt(?:u|em|y)?\\b.*")
                || text.matches(".*\\bpokoj(?:e)?\\b.*");
        return rental && apartment && !text.contains("prodej");
    }

    private boolean hasOwnerSignal(String text) {
        return OWNER_SIGNALS.stream().anyMatch(text::contains);
    }

    private boolean hasAgencySignal(String text) {
        String agencyCheckText = text
                .replace("bez provize realitni kancelari", "")
                .replace("bez provize rk", "");
        return AGENCY_SIGNALS.stream().anyMatch(agencyCheckText::contains);
    }

    private String localityForRegion(RssListing listing, Region region) {
        if (region == null || region.getTitle() == null || region.getTitle().isBlank()) {
            return listing.title();
        }

        String normalizedTitle = normalizeForMatch(listing.title());
        String normalizedRegion = normalizeForMatch(region.getTitle());
        if (!normalizedTitle.contains(normalizedRegion)) {
            return null;
        }

        // The title normally contains Prague district/city-part data used by the existing group filter.
        return "PRAHA".equalsIgnoreCase(region.getCode()) ? listing.title() : region.getTitle();
    }

    private int extractPrice(String text) {
        Matcher matcher = PRICE_PATTERN.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1).replaceAll("[\\s\\u00A0.]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractLayout(String text) {
        String normalized = normalizeForMatch(text);
        if (normalized.matches(".*\\b(pronajem|pronajmu|pronajimam)\\s+pokoj(?:e)?\\b.*")) {
            return "ROOM";
        }

        Matcher matcher = LAYOUT_PATTERN.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String extractPhoto(Element item) {
        Element enclosure = item.selectFirst("enclosure[url]");
        if (enclosure != null) {
            return enclosure.attr("url").trim();
        }
        return elementText(item, "image");
    }

    private LocalDateTime extractFoundAt(Element item) {
        String pubDate = elementText(item, "pubDate");
        try {
            return ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDateTime();
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private String elementText(Element parent, String selector) {
        Element element = parent.selectFirst(selector);
        return element == null ? "" : element.text().trim();
    }

    private String normalizeForMatch(String text) {
        return normalizeWhitespace(text)
                .toLowerCase(Locale.ROOT)
                .replace('ě', 'e').replace('š', 's').replace('č', 'c')
                .replace('ř', 'r').replace('ž', 'z').replace('ý', 'y')
                .replace('á', 'a').replace('í', 'i').replace('é', 'e')
                .replace('ů', 'u').replace('ú', 'u').replace('ň', 'n')
                .replace('ď', 'd').replace('ť', 't').replace('ó', 'o');
    }

    private String normalizeWhitespace(String text) {
        return text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String regionTitle(Region region) {
        return region == null || region.getTitle() == null ? "default" : region.getTitle();
    }

    record RssListing(
            String title,
            String link,
            String description,
            int priceCzk,
            String layout,
            String photoUrl,
            LocalDateTime foundAt
    ) {
    }

    private record CacheEntry(List<RssListing> listings, long loadedAtMillis) {
    }

    private record ParseResult(List<RssListing> listings, ParseDiagnostics diagnostics) {
    }

    private static final class ParseDiagnostics {
        private int total;
        private int rentalApartments;
        private int notRentalApartment;
        private int bezrealitkyDuplicates;
        private int withoutOwnerSignal;
        private int withOwnerSignal;
        private int agencySignal;
        private int invalidPrice;
        private int missingLayout;
        private int blankLink;
        private int accepted;
    }
}
