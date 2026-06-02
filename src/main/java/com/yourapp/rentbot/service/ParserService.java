package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.RegionGroup;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import com.yourapp.rentbot.service.dto.ParserRunStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ParserService {

    private static final Logger log = LoggerFactory.getLogger(ParserService.class);

    private static final long CACHE_TTL_MILLIS = 55_000;
    private static final Set<String> ADDRESS_STOP_WORDS = Set.of(
            "www", "http", "https", "cz", "detail", "reality", "sreality", "idnes",
            "pronajem", "pronajmu", "nabidka", "byt", "bytu", "m2", "kc", "mesic", "mesicne",
            "okres", "kraj", "ulice", "ul", "cena", "dispozice", "plocha",
            "kolin", "praha", "brno", "ostrava", "plzen", "liberec", "olomouc", "zlin",
            "pardubice", "jihlava", "kladno", "nymburk", "tabor", "cheb", "most",
            "iv", "iii", "ii", "i"
    );

    private final Object fetchLock = new Object();
    private volatile List<ListingDto> cachedListings = List.of();
    private volatile long cachedAtMillis = 0;

    private final SrealityParser srealityParser;
    private final IdnesParser idnesParser;
    private final BezrealitkyParser bezrealitkyParser;
    private final BazosParser bazosParser;
    private final UserFilterRepo userFilterRepo;

    private final AtomicReference<ParserRunStats> lastRunStats =
            new AtomicReference<>(new ParserRunStats(
                    0, 0, 0, 0,
                    0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0
            ));

    public ParserService(SrealityParser srealityParser,
                         IdnesParser idnesParser,
                         BezrealitkyParser bezrealitkyParser,
                         BazosParser bazosParser,
                         UserFilterRepo userFilterRepo) {
        this.srealityParser = srealityParser;
        this.idnesParser = idnesParser;
        this.bezrealitkyParser = bezrealitkyParser;
        this.bazosParser = bazosParser;
        this.userFilterRepo = userFilterRepo;
    }

    @Transactional(readOnly = true)
    public List<ListingDto> findNewListings(Long telegramUserId) throws IOException {
        UserFilter filter = userFilterRepo.findFullById(telegramUserId)
                .orElseThrow(() -> new IllegalArgumentException("UserFilter not found: " + telegramUserId));

        List<ListingDto> all = fetchListingsForFilter(filter);
        return filterForUser(all, filter);
    }

    public List<ListingDto> fetchListingsForFilter(UserFilter filter) throws IOException {

        Region region = filter != null ? filter.getRegion() : null;
        RegionGroup group = filter != null ? filter.getRegionGroup() : null;

        Integer srealityDistrictId = null;

        if (region != null && region.getCode() != null) {
            srealityDistrictId = SrealityParser.getDistrictId(region.getCode());
        }

        List<ListingDto> all = new ArrayList<>();
        int srealityRaw = 0;
        int idnesRaw = 0;
        int bezrealitkyRaw = 0;
        int bazosRaw = 0;

        try {

            if (srealityDistrictId != null) {

                List<ListingDto> sreality =
                        srealityParser.fetchListings(region.getCode(), srealityDistrictId);
                srealityRaw = sreality.size();

                log.info("Sreality listings region={} count={}", regionTitle(region), sreality.size());

                all.addAll(sreality);

            } else {

                log.info("Sreality skipped region={} reason=district_id_is_null", regionTitle(region));
            }

        } catch (Exception e) {
            log.warn("Sreality parser failed region={} error={}", regionTitle(region), e.getMessage());
        }

        try {
            List<ListingDto> idnes = idnesParser.fetchListings(region, null);
            idnesRaw = idnes.size();

            log.info("iDNES listings region={} count={}", regionTitle(region), idnes.size());

            all.addAll(idnes);

        } catch (Exception e) {
            log.warn("iDNES parser failed region={} error={}", regionTitle(region), e.getMessage());
        }

        try {
            List<ListingDto> bezrealitky =
                    bezrealitkyParser.fetchListings(region);
            bezrealitkyRaw = bezrealitky.size();

            log.info("Bezrealitky listings region={} count={}", regionTitle(region), bezrealitky.size());

            all.addAll(bezrealitky);

        } catch (Exception e) {
            log.warn("Bezrealitky parser failed region={} error={}", regionTitle(region), e.getMessage());
        }

        try {
            if (bazosParser.isRateLimitedForCurrentCycle()) {
                log.debug(
                        "Bazos skipped region={} reason={} cooldownRemainingSeconds={}",
                        regionTitle(region),
                        bazosParser.currentSkipReason(),
                        bazosParser.currentSkipRemainingSeconds()
                );
            } else {
                List<ListingDto> bazos = bazosParser.fetchListings(region);
                bazosRaw = bazos.size();

                log.info("Bazos listings region={} count={}", regionTitle(region), bazos.size());

                all.addAll(bazos);
            }

        } catch (Exception e) {
            log.warn("Bazos parser failed region={} error={}", regionTitle(region), e.getMessage());
        }

        all = dedupeByLink(all);
        int afterDedupeByLink = all.size();

        all = dedupeBySignature(all);
        int afterDedupeBySignature = all.size();

        lastRunStats.set(new ParserRunStats(
                srealityRaw,
                idnesRaw,
                bezrealitkyRaw,
                bazosRaw,
                afterDedupeByLink,
                afterDedupeBySignature,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0
        ));

        log.info("All listings region={} count={}", regionTitle(region), all.size());

        return all;
    }

    public List<ListingDto> fetchAllListingsOnce() throws IOException {
        long now = System.currentTimeMillis();

        if (!cachedListings.isEmpty() && now - cachedAtMillis < CACHE_TTL_MILLIS) {
            log.info("Using cached listings count={}", cachedListings.size());
            return cachedListings;
        }

        synchronized (fetchLock) {
            now = System.currentTimeMillis();

            if (!cachedListings.isEmpty() && now - cachedAtMillis < CACHE_TTL_MILLIS) {
                log.info("Using cached listings count={}", cachedListings.size());
                return cachedListings;
            }

            Region defaultRegion = null;
            RegionGroup defaultGroup = null;
            Integer defaultSrealityRegionId = 10;

            List<ListingDto> all = new ArrayList<>();

            int srealityRaw = 0;
            int idnesRaw = 0;
            int bezrealitkyRaw = 0;
            int bazosRaw = 0;

            try {
                List<ListingDto> sreality = srealityParser.fetchListings(defaultSrealityRegionId);
                srealityRaw = sreality.size();
                log.info("Sreality listings count={}", srealityRaw);
                all.addAll(sreality);
            } catch (Exception e) {
                log.warn("Sreality parser failed error={}", e.getMessage());
            }

            try {
                List<ListingDto> idnes = idnesParser.fetchListings(defaultRegion, defaultGroup);
                idnesRaw = idnes.size();
                log.info("iDNES listings count={}", idnesRaw);
                all.addAll(idnes);
            } catch (Exception e) {
                log.warn("iDNES parser failed error={}", e.getMessage());
            }

            try {
                List<ListingDto> bezrealitky = bezrealitkyParser.fetchListings(defaultRegion);
                bezrealitkyRaw = bezrealitky.size();
                log.info("Bezrealitky listings count={}", bezrealitkyRaw);
                all.addAll(bezrealitky);
            } catch (Exception e) {
                log.warn("Bezrealitky parser failed error={}", e.getMessage());
            }

            try {
                if (bazosParser.isRateLimitedForCurrentCycle()) {
                    log.debug(
                            "Bazos skipped reason={} cooldownRemainingSeconds={}",
                            bazosParser.currentSkipReason(),
                            bazosParser.currentSkipRemainingSeconds()
                    );
                } else {
                    List<ListingDto> bazos = bazosParser.fetchListings(defaultRegion);
                    bazosRaw = bazos.size();
                    log.info("Bazos listings count={}", bazosRaw);
                    all.addAll(bazos);
                }
            } catch (Exception e) {
                log.warn("Bazos parser failed error={}", e.getMessage());
            }

            all = dedupeByLink(all);
            int afterDedupeByLink = all.size();
            log.info("After dedupe by link count={}", afterDedupeByLink);

            all = dedupeBySignature(all);
            int afterDedupeBySignature = all.size();
            log.info("After dedupe by signature count={}", afterDedupeBySignature);

            log.info("All listings from all parsers count={}", all.size());

            lastRunStats.set(new ParserRunStats(
                    srealityRaw,
                    idnesRaw,
                    bezrealitkyRaw,
                    bazosRaw,
                    afterDedupeByLink,
                    afterDedupeBySignature,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0
            ));

            cachedListings = all;
            cachedAtMillis = System.currentTimeMillis();

            return all;
        }
    }

    public List<ListingDto> filterForUser(List<ListingDto> all, UserFilter filter) {
        if (all == null || all.isEmpty() || filter == null) {
            return List.of();
        }

        Region region = filter.getRegion();
        RegionGroup regionGroup = filter.getRegionGroup();

        String regionTitle = region == null ? null : region.getTitle();
        String needLayout = normalizeLayout(filter.getLayout());
        Integer maxPrice = filter.getMaxPrice();
        String groupCode = regionGroup == null ? null : regionGroup.getCode();

        logFilterDiagnosticsBySource(all, filter, regionTitle, needLayout, maxPrice, groupCode);

        List<ListingDto> filteredBase = all.stream()
                .filter(x -> needLayout == null || layoutMatches(needLayout, x.layout()))
                .filter(x -> maxPrice == null || maxPrice == 0 || (x.priceCzk() > 0 && x.priceCzk() <= maxPrice))
                .filter(x -> matchesRegion(x.locality(), regionTitle))
                .filter(x -> matchesRegionGroup(x.locality(), groupCode))
                .filter(x -> x.priceCzk() == 0 || x.priceCzk() >= 3000)
                .sorted(Comparator.comparingInt((ListingDto x) -> listingScore(x, filter)).reversed())
                .toList();

        int filteredBaseTotal = filteredBase.size();

        int filteredBaseSreality = 0;
        int filteredBaseIdnes = 0;
        int filteredBaseBezrealitky = 0;
        int filteredBaseBazos = 0;

        for (ListingDto x : filteredBase) {
            String source = x.source() == null ? "" : x.source().toLowerCase();

            if (source.contains("sreality")) filteredBaseSreality++;
            else if (source.contains("idnes")) filteredBaseIdnes++;
            else if (source.contains("bezrealitky")) filteredBaseBezrealitky++;
            else if (source.contains("bazo")) filteredBaseBazos++;
        }

        List<ListingDto> filtered = diversifyBySource(filteredBase, 4, 20);

        int finalFiltered = filtered.size();

        int finalSreality = 0;
        int finalIdnes = 0;
        int finalBezrealitky = 0;
        int finalBazos = 0;

        for (ListingDto x : filtered) {
            String source = x.source() == null ? "" : x.source().toLowerCase();

            if (source.contains("sreality")) finalSreality++;
            else if (source.contains("idnes")) finalIdnes++;
            else if (source.contains("bezrealitky")) finalBezrealitky++;
            else if (source.contains("bazo")) finalBazos++;
        }

        ParserRunStats previous = lastRunStats.get();

        lastRunStats.set(new ParserRunStats(
                previous.srealityRaw(),
                previous.idnesRaw(),
                previous.bezrealitkyRaw(),
                previous.bazosRaw(),
                previous.afterDedupeByLink(),
                previous.afterDedupeBySignature(),

                filteredBaseTotal,
                filteredBaseSreality,
                filteredBaseIdnes,
                filteredBaseBezrealitky,
                filteredBaseBazos,

                finalFiltered,
                finalSreality,
                finalIdnes,
                finalBezrealitky,
                finalBazos
        ));

        if (filtered.isEmpty()) {
            return filtered;
        }

        return filtered;
    }

    private void logFilterDiagnosticsBySource(List<ListingDto> all,
                                              UserFilter filter,
                                              String regionTitle,
                                              String needLayout,
                                              Integer maxPrice,
                                              String groupCode) {
        Map<String, FilterDiagnostics> diagnosticsBySource = new LinkedHashMap<>();

        for (ListingDto x : all) {
            if (x == null) {
                continue;
            }

            String source = x.source() == null || x.source().isBlank() ? "UNKNOWN" : x.source();
            FilterDiagnostics diagnostics = diagnosticsBySource.computeIfAbsent(source, ignored -> new FilterDiagnostics());

            diagnostics.total++;

            if (x.priceCzk() <= 0) {
                diagnostics.zeroPrice++;
            }
            if (x.locality() == null || x.locality().isBlank()) {
                diagnostics.blankLocality++;
            }

            if (needLayout != null && !layoutMatches(needLayout, x.layout())) {
                diagnostics.rejectedLayout++;
                continue;
            }

            if (maxPrice != null && maxPrice > 0 && (x.priceCzk() <= 0 || x.priceCzk() > maxPrice)) {
                diagnostics.rejectedMaxPrice++;
                continue;
            }

            if (!matchesRegion(x.locality(), regionTitle)) {
                diagnostics.rejectedRegion++;
                if (diagnostics.firstRejectedRegionSample.isBlank()) {
                    diagnostics.firstRejectedRegionSample = listingLogSample(x);
                }
                continue;
            }

            if (!matchesRegionGroup(x.locality(), groupCode)) {
                diagnostics.rejectedRegionGroup++;
                continue;
            }

            if (x.priceCzk() > 0 && x.priceCzk() < 3000) {
                diagnostics.rejectedMinPrice++;
                continue;
            }

            diagnostics.passed++;
        }

        diagnosticsBySource.forEach((source, d) -> log.info(
                "Filter diagnostics user={} source={} region={} group={} needLayout={} maxPrice={} "
                        + "total={} passed={} rejectedLayout={} rejectedMaxPrice={} rejectedRegion={} "
                        + "rejectedRegionGroup={} rejectedMinPrice={} zeroPrice={} blankLocality={} firstRejectedRegion={}",
                filter.getTelegramUserId(),
                source,
                regionTitle,
                groupCode,
                needLayout,
                maxPrice,
                d.total,
                d.passed,
                d.rejectedLayout,
                d.rejectedMaxPrice,
                d.rejectedRegion,
                d.rejectedRegionGroup,
                d.rejectedMinPrice,
                d.zeroPrice,
                d.blankLocality,
                d.firstRejectedRegionSample
        ));
    }

    private static class FilterDiagnostics {
        private int total;
        private int passed;
        private int rejectedLayout;
        private int rejectedMaxPrice;
        private int rejectedRegion;
        private int rejectedRegionGroup;
        private int rejectedMinPrice;
        private int zeroPrice;
        private int blankLocality;
        private String firstRejectedRegionSample = "";
    }

    private String listingLogSample(ListingDto dto) {
        if (dto == null) {
            return "";
        }

        String sample = "title=" + firstNonBlank(dto.title(), "")
                + ", locality=" + firstNonBlank(dto.locality(), "")
                + ", layout=" + firstNonBlank(dto.layout(), "")
                + ", price=" + dto.priceCzk()
                + ", link=" + firstNonBlank(dto.link(), "");

        return sample.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean matchesRegion(String locality, String regionTitle) {
        if (regionTitle == null || regionTitle.isBlank()) return true;
        if (locality == null || locality.isBlank()) return false;

        String loc = normalizeLocality(locality);
        String reg = normalizeLocality(regionTitle);

        if (reg.equals("praha")) {
            return isPrahaListing(locality);
        }

        return loc.contains(reg);
    }

    public void resetBazosRateLimitCycle() {
        bazosParser.resetRateLimitCycle();
    }

    public void resetSrealityTemporaryUnavailableCycle() {
        srealityParser.resetTemporaryUnavailableCycle();
    }

    private String regionTitle(Region region) {
        return region != null ? region.getTitle() : "default";
    }

    private List<ListingDto> dedupeByLink(List<ListingDto> input) {
        Map<String, ListingDto> map = new LinkedHashMap<>();

        for (ListingDto dto : input) {
            if (dto == null || dto.link() == null || dto.link().isBlank()) continue;
            map.putIfAbsent(dto.link(), dto);
        }

        return new ArrayList<>(map.values());
    }

    private List<ListingDto> dedupeBySignature(List<ListingDto> input) {
        Map<String, ListingDto> map = new LinkedHashMap<>();

        for (ListingDto dto : input) {
            if (dto == null) continue;

            String layout = normalizeLayout(dto.layout());
            int price = dto.priceCzk();
            String addressCore = extractAddressCore(dto);

            if (layout == null || addressCore == null || addressCore.isBlank() || price <= 0) {
                String fallbackKey = "LINK:" + (dto.link() == null ? "" : dto.link());
                map.putIfAbsent(fallbackKey, dto);
                continue;
            }

            String key = layout + "|" + price + "|" + addressCore;
            ListingDto existing = map.get(key);

            if (existing == null) {
                map.put(key, dto);
            } else {
                map.put(key, pickBetterListing(existing, dto));
            }
        }

        return new ArrayList<>(map.values());
    }

    private boolean matchesRegionGroup(String locality, String groupCode) {
        if (groupCode == null || groupCode.isBlank()) return true;

        if ("PRAHA_ALL".equals(groupCode)) {
            return isPrahaListing(locality);
        }

        if (groupCode.startsWith("PRAHA_") && !isPrahaListing(locality)) {
            return false;
        }

        int district = extractPrahaDistrict(locality);

        if (district == -1) return false;

        return switch (groupCode) {
            case "PRAHA_1_3" -> district >= 1 && district <= 3;
            case "PRAHA_4_6" -> district >= 4 && district <= 6;
            case "PRAHA_7_10" -> district >= 7 && district <= 10;
            case "PRAHA_11_15" -> district >= 11 && district <= 15;
            default -> true;
        };
    }

    private boolean isPrahaListing(String locality) {
        if (locality == null || locality.isBlank()) return false;

        String lower = normalizeLocality(locality);

        return lower.contains("praha")
                || lower.contains("zizkov")
                || lower.contains("vinohrady")
                || lower.contains("vrsovice")
                || lower.contains("nusle")
                || lower.contains("letnany")
                || lower.contains("holesovice")
                || lower.contains("smichov")
                || lower.contains("chodov")
                || lower.contains("stodulky")
                || lower.contains("dejvice")
                || lower.contains("karlin")
                || lower.contains("liben")
                || lower.contains("kobylisy")
                || lower.contains("prosek")
                || lower.contains("vysocany")
                || lower.contains("troja")
                || lower.contains("michle")
                || lower.contains("branik")
                || lower.contains("modrany")
                || lower.contains("krc")
                || lower.contains("hlubocepy")
                || lower.contains("bubenec")
                || lower.contains("ruzyne")
                || lower.contains("stresovice")
                || lower.contains("vokovice")
                || lower.contains("suchdol")
                || lower.contains("brevnov")
                || lower.contains("bohnice")
                || lower.contains("cimice")
                || lower.contains("hrdlorezy")
                || lower.contains("strasnice")
                || lower.contains("zabehlice")
                || lower.contains("haje")
                || lower.contains("cerny most")
                || lower.contains("hostavice")
                || lower.contains("hostivar")
                || lower.contains("mecholupy")
                || lower.contains("podoli")
                || lower.contains("zlicin")
                || lower.contains("kunratice")
                || lower.contains("dablice");
    }

    private int extractPrahaDistrict(String locality) {
        if (locality == null || locality.isBlank()) return -1;

        String lower = normalizeLocality(locality);

        for (int i = 22; i >= 1; i--) {
            if (lower.matches(".*\\bpraha\\s+" + i + "\\b.*")
                    || lower.matches(".*\\bpraha-" + i + "\\b.*")
                    || lower.endsWith("praha " + i)
                    || lower.endsWith("praha-" + i)) {
                return i;
            }
        }

        if (lower.contains("stare mesto")) return 1;
        if (lower.contains("nove mesto")) return 1;
        if (lower.contains("mala strana")) return 1;
        if (lower.contains("vinohrady")) return 2;
        if (lower.contains("vysehrad")) return 2;
        if (lower.contains("zizkov")) return 3;
        if (lower.contains("jarov")) return 3;
        if (lower.contains("modrany")) return 4;
        if (lower.contains("branik")) return 4;
        if (lower.contains("krc")) return 4;
        if (lower.contains("michle")) return 4;
        if (lower.contains("nusle")) return 4;
        if (lower.contains("podoli")) return 4;
        if (lower.contains("smichov")) return 5;
        if (lower.contains("jinonice")) return 5;
        if (lower.contains("hlubocepy")) return 5;
        if (lower.contains("zlicin")) return 5;
        if (lower.contains("dejvice")) return 6;
        if (lower.contains("bubenec")) return 6;
        if (lower.contains("ruzyne")) return 6;
        if (lower.contains("stresovice")) return 6;
        if (lower.contains("vokovice")) return 6;
        if (lower.contains("suchdol")) return 6;
        if (lower.contains("brevnov")) return 6;
        if (lower.contains("holesovice")) return 7;
        if (lower.contains("troja")) return 7;
        if (lower.contains("liben")) return 8;
        if (lower.contains("karlin")) return 8;
        if (lower.contains("bohnice")) return 8;
        if (lower.contains("cimice")) return 8;
        if (lower.contains("kobylisy")) return 8;
        if (lower.contains("dablice")) return 8;
        if (lower.contains("vysocany")) return 9;
        if (lower.contains("letnany")) return 9;
        if (lower.contains("prosek")) return 9;
        if (lower.contains("hloubetin")) return 9;
        if (lower.contains("hrdlorezy")) return 9;
        if (lower.contains("vrsovice")) return 10;
        if (lower.contains("strasnice")) return 10;
        if (lower.contains("zabehlice")) return 10;
        if (lower.contains("chodov")) return 11;
        if (lower.contains("haje")) return 11;
        if (lower.contains("kamyk")) return 12;
        if (lower.contains("stodulky")) return 13;
        if (lower.contains("cerny most")) return 14;
        if (lower.contains("hostavice")) return 14;
        if (lower.contains("hostivar")) return 15;
        if (lower.contains("horni mecholupy")) return 15;
        if (lower.contains("dolni mecholupy")) return 15;
        if (lower.contains("mecholupy")) return 15;

        return -1;
    }

    private boolean layoutMatches(String needLayout, String listingLayout) {
        String normalizedNeed = normalizeLayout(needLayout);
        String normalizedListing = normalizeLayout(listingLayout);

        if (normalizedNeed == null) return true;
        if (normalizedListing == null) return false;

        if ("room".equals(normalizedNeed) || "pokoj".equals(normalizedNeed)) {
            return normalizedListing.equals("room") || normalizedListing.equals("pokoj");
        }

        if ("1".equals(normalizedNeed) || "1+kk".equals(normalizedNeed) || "1+1".equals(normalizedNeed)) {
            return normalizedListing.equals("1+kk") || normalizedListing.equals("1+1");
        }

        if ("2".equals(normalizedNeed) || "2+kk".equals(normalizedNeed) || "2+1".equals(normalizedNeed)) {
            return normalizedListing.equals("2+kk") || normalizedListing.equals("2+1");
        }

        if ("3".equals(normalizedNeed) || "3+kk".equals(normalizedNeed) || "3+1".equals(normalizedNeed)) {
            return normalizedListing.equals("3+kk") || normalizedListing.equals("3+1");
        }

        if ("4".equals(normalizedNeed)
                || "4+".equals(normalizedNeed)
                || "4+kk".equals(normalizedNeed)
                || "4+1".equals(normalizedNeed)) {
            return normalizedListing.startsWith("4+")
                    || normalizedListing.startsWith("5+")
                    || normalizedListing.startsWith("6+")
                    || normalizedListing.startsWith("7+")
                    || normalizedListing.startsWith("8+");
        }

        return false;
    }

    private String normalizeLayout(String s) {
        if (s == null || s.isBlank()) return null;

        String normalized = s.toLowerCase().replaceAll("\\s+", "");

        if ("ROOM".equalsIgnoreCase(s) || "pokoj".equals(normalized)) {
            return "room";
        }

        return normalized;
    }

    private String normalizeLocality(String s) {
        if (s == null || s.isBlank()) return null;

        return s.toLowerCase()
                .replace('ě', 'e')
                .replace('š', 's')
                .replace('č', 'c')
                .replace('ř', 'r')
                .replace('ž', 'z')
                .replace('ý', 'y')
                .replace('á', 'a')
                .replace('í', 'i')
                .replace('é', 'e')
                .replace('ů', 'u')
                .replace('ú', 'u')
                .replace('ň', 'n')
                .replace('ď', 'd')
                .replace('ť', 't')
                .replace('ľ', 'l')
                .replace('ĺ', 'l')
                .replace('ó', 'o')
                .replace('?', ' ')
                .replaceAll("[^a-z0-9\\s,-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String extractAddressCore(ListingDto dto) {
        String raw = combineAddressInputs(dto);
        if (raw == null || raw.isBlank()) return null;

        String normalized = normalizeLocality(raw);

        normalized = normalized
                .replaceAll("\\bnovy pronajem bytu\\b", " ")
                .replaceAll("\\bpronajem bytu\\b", " ")
                .replaceAll("\\bzlevneno\\b", " ")
                .replaceAll("\\brezervovano\\b", " ")
                .replaceAll("\\bbyt\\b", " ")
                .replaceAll("\\bm2\\b", " ")
                .replaceAll("\\bkc\\b", " ")
                .replaceAll("\\bmesic\\b", " ")
                .replaceAll("\\bmesicne\\b", " ")
                .replaceAll("\\bdetail\\b", " ")
                .replaceAll("\\breality\\b", " ")
                .replaceAll("\\bidnes\\b", " ")
                .replaceAll("\\bsreality\\b", " ")
                .replaceAll("\\bpronajem\\b", " ")
                .replaceAll("\\bnove\\b", " ")
                .replaceAll("\\bnovy\\b", " ");

        normalized = normalized
                .replaceAll("\\b\\d+\\+kk\\b", " ")
                .replaceAll("\\b\\d+\\+1\\b", " ")
                .replaceAll("\\b\\d{1,3}\\s*m\\b", " ")
                .replaceAll("\\b\\d{1,3}\\b", " ")
                .replaceAll("\\b\\d{4,6}\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();

        String street = extractStreetCore(normalized);

        if (street != null && !street.isBlank()) {
            return street;
        }

        return normalized;
    }

    private String extractStreetCore(String normalized) {
        if (normalized == null || normalized.isBlank()) return null;

        String[] parts = normalized.split(",");

        for (String part : parts) {
            String candidate = cleanupAddressCandidate(part);
            if (isStrongAddressCandidate(candidate)) {
                return candidate;
            }
        }

        String candidate = cleanupAddressCandidate(normalized);
        if (isStrongAddressCandidate(candidate)) {
            return candidate;
        }

        return null;
    }

    private String combineAddressInputs(ListingDto dto) {
        if (dto == null) return null;

        StringBuilder sb = new StringBuilder();
        appendAddressInput(sb, dto.locality());
        appendAddressInput(sb, dto.title());
        appendAddressInput(sb, dto.link());

        return sb.toString();
    }

    private void appendAddressInput(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        if (!sb.isEmpty()) {
            sb.append(", ");
        }
        sb.append(value);
    }

    private String cleanupAddressCandidate(String value) {
        if (value == null || value.isBlank()) return "";

        String normalized = value
                .replace('-', ' ')
                .replace('/', ' ')
                .replaceAll("\\b[a-f0-9]{12,}\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();

        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (token.isBlank()) continue;
            if (ADDRESS_STOP_WORDS.contains(token)) continue;
            if (token.length() <= 1) continue;
            tokens.add(token);
        }

        return String.join(" ", tokens).trim();
    }

    private boolean isStrongAddressCandidate(String candidate) {
        if (candidate == null || candidate.isBlank()) return false;

        String[] tokens = candidate.split("\\s+");
        if (tokens.length == 0 || tokens.length > 4) return false;

        for (String token : tokens) {
            if (token.length() >= 4) {
                return true;
            }
        }

        return false;
    }

    private ListingDto pickBetterListing(ListingDto a, ListingDto b) {
        return scoreListing(b) > scoreListing(a) ? b : a;
    }

    private int scoreListing(ListingDto dto) {
        int score = 0;
        if (dto == null) return score;

        if (dto.photoUrl() != null && !dto.photoUrl().isBlank()) score += 3;
        if (dto.locality() != null && dto.locality().length() < 80) score += 2;
        if (dto.link() != null && !dto.link().isBlank()) score += 1;

        String source = dto.source() == null ? "" : dto.source().toLowerCase();

        if (source.contains("bezrealitky")) score += 5;
        else if (source.contains("sreality")) score += 4;
        else if (source.contains("idnes")) score += 3;
        else if (source.contains("bazo")) score += 2;

        return score;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;

        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }

        return null;
    }

    private List<ListingDto> diversifyBySource(List<ListingDto> input, int maxPerSource, int totalLimit) {
        Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        List<ListingDto> result = new ArrayList<>();

        for (ListingDto dto : input) {
            if (dto == null) continue;

            String source = dto.source();
            if (source == null || source.isBlank()) source = "UNKNOWN";

            int used = sourceCounts.getOrDefault(source, 0);

            if (used >= maxPerSource) continue;

            result.add(dto);
            sourceCounts.put(source, used + 1);

            if (result.size() >= totalLimit) break;
        }

        if (result.size() < totalLimit) {
            for (ListingDto dto : input) {
                if (dto == null || result.contains(dto)) continue;

                result.add(dto);

                if (result.size() >= totalLimit) break;
            }
        }

        return result;
    }

    private int listingScore(ListingDto dto, UserFilter filter) {
        if (dto == null) {
            return 0;
        }

        int score = 0;
        int price = dto.priceCzk();

        Integer maxPrice = filter == null ? null : filter.getMaxPrice();

        // Насколько цена хороша относительно бюджета пользователя
        if (price > 0) {
            if (maxPrice != null && maxPrice > 0) {
                double ratio = (double) price / maxPrice;

                if (ratio <= 0.70) score += 60;
                else if (ratio <= 0.80) score += 50;
                else if (ratio <= 0.90) score += 40;
                else if (ratio <= 1.00) score += 30;
            } else {
                if (price <= 12000) score += 60;
                else if (price <= 15000) score += 50;
                else if (price <= 18000) score += 40;
                else if (price <= 20000) score += 30;
                else if (price <= 25000) score += 20;
                else if (price <= 30000) score += 10;
            }
        } else {
            score -= 30;
        }

        if (dto.photoUrl() != null && !dto.photoUrl().isBlank()) score += 15;
        if (dto.locality() != null && !dto.locality().isBlank() && dto.locality().length() <= 80) score += 10;
        if (dto.layout() != null && !dto.layout().isBlank()) score += 10;

        String source = dto.source() == null ? "" : dto.source().toLowerCase();

        if (source.contains("bezrealitky")) score += 25;
        else if (source.contains("sreality")) score += 20;
        else if (source.contains("idnes")) score += 15;
        else if (source.contains("bazo")) score += 8;

        // Бонус, если объявление точно попало в выбранную группу Праги
        RegionGroup group = filter == null ? null : filter.getRegionGroup();
        String groupCode = group == null ? null : group.getCode();

        if (groupCode != null && !groupCode.isBlank() && matchesRegionGroup(dto.locality(), groupCode)) {
            score += 20;
        }

        return score;
    }

    public ParserRunStats getLastRunStats() {
        return lastRunStats.get();
    }
}
