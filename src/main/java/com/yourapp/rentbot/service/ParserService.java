package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.RegionGroup;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.yourapp.rentbot.service.dto.ParserRunStats;
import java.util.concurrent.atomic.AtomicReference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ParserService {

    private final SrealityParser srealityParser;
    private final IdnesParser idnesParser;
    private final BezrealitkyParser bezrealitkyParser;
    private final BazosParser bazosParser;
    private final UserFilterRepo userFilterRepo;

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

        Region region = filter.getRegion();
        RegionGroup regionGroup = filter.getRegionGroup();

        String needLayout = normalizeLayout(filter.getLayout());
        Integer maxPrice = filter.getMaxPrice();
        String groupCode = regionGroup == null ? null : regionGroup.getCode();
        Integer srealityRegionId = region == null ? 10 : region.getSrealityRegionId();

        List<ListingDto> all = new ArrayList<>();

        int srealityRaw = 0;
        int idnesRaw = 0;
        int bezrealitkyRaw = 0;
        int bazosRaw = 0;

        try {
            List<ListingDto> sreality = srealityParser.fetchListings(srealityRegionId);
            srealityRaw = sreality.size();
            System.out.println("SREALITY LISTINGS = " + srealityRaw);
            all.addAll(sreality);
        } catch (Exception e) {
            System.out.println("Sreality parser failed: " + e.getMessage());
        }

        try {
            List<ListingDto> idnes = idnesParser.fetchListings(region, regionGroup);
            idnesRaw = idnes.size();
            System.out.println("IDNES LISTINGS = " + idnesRaw);
            all.addAll(idnes);
        } catch (Exception e) {
            System.out.println("Idnes parser failed: " + e.getMessage());
        }

        try {
            List<ListingDto> bezrealitky = bezrealitkyParser.fetchListings(region);
            bezrealitkyRaw = bezrealitky.size();
            System.out.println("BEZREALITKY LISTINGS = " + bezrealitkyRaw);
            all.addAll(bezrealitky);
        } catch (Exception e) {
            System.out.println("Bezrealitky parser failed: " + e.getMessage());
        }

        try {
            List<ListingDto> bazos = bazosParser.fetchListings(region);
            bazosRaw = bazos.size();
            System.out.println("BAZOS LISTINGS = " + bazosRaw);
            all.addAll(bazos);
        } catch (Exception e) {
            System.out.println("Bazos parser failed: " + e.getMessage());
        }

        all = dedupeByLink(all);
        int afterDedupeByLink = all.size();
        System.out.println("AFTER DEDUPE BY LINK = " + afterDedupeByLink);

        all = dedupeBySignature(all);
        int afterDedupeBySignature = all.size();
        System.out.println("AFTER DEDUPE BY SIGNATURE = " + afterDedupeBySignature);

        System.out.println("ALL LISTINGS FROM ALL PARSERS = " + all.size());
        System.out.println("FILTER layout = " + needLayout + ", maxPrice = " + maxPrice + ", group = " + groupCode);

        List<ListingDto> filteredBase = all.stream()
                .filter(x -> needLayout == null || layoutMatches(needLayout, x.layout()))
                .filter(x -> maxPrice == null || maxPrice == 0 || (x.priceCzk() > 0 && x.priceCzk() <= maxPrice))
                .filter(x -> matchesRegionGroup(x.locality(), groupCode))
                .filter(x -> x.priceCzk() == 0 || x.priceCzk() >= 3000)
                .sorted(Comparator.comparingInt(x -> x.priceCzk() == 0 ? Integer.MAX_VALUE : x.priceCzk()))
                .toList();

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

        lastRunStats.set(new ParserRunStats(
                srealityRaw,
                idnesRaw,
                bezrealitkyRaw,
                bazosRaw,
                afterDedupeByLink,
                afterDedupeBySignature,
                finalFiltered,
                finalSreality,
                finalIdnes,
                finalBezrealitky,
                finalBazos
        ));

        System.out.println("FILTERED LISTINGS = " + filtered.size());

        for (ListingDto x : filtered) {
            System.out.println("FILTERED -> source=" + x.source()
                    + ", price=" + x.priceCzk()
                    + ", layout=" + x.layout()
                    + ", locality=" + x.locality());
        }

        return filtered;
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

    private List<ListingDto> dedupeBySignature(List<ListingDto> input) {
        Map<String, ListingDto> map = new LinkedHashMap<>();

        for (ListingDto dto : input) {
            if (dto == null) {
                continue;
            }

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
        if (groupCode == null || groupCode.isBlank()) {
            return true;
        }

        if ("PRAHA_ALL".equals(groupCode)) {
            return true;
        }

        int district = extractPrahaDistrict(locality);
        if (district == -1) {
            return false;
        }

        return switch (groupCode) {
            case "PRAHA_1_3" -> district >= 1 && district <= 3;
            case "PRAHA_4_6" -> district >= 4 && district <= 6;
            case "PRAHA_7_10" -> district >= 7 && district <= 10;
            case "PRAHA_11_15" -> district >= 11 && district <= 15;
            default -> true;
        };
    }

    private int extractPrahaDistrict(String locality) {
        if (locality == null || locality.isBlank()) {
            return -1;
        }

        String lower = locality.toLowerCase();

        for (int i = 1; i <= 22; i++) {
            if (lower.contains("praha " + i) || lower.contains("praha-" + i)) {
                return i;
            }
        }

        if (lower.contains("staré město")) return 1;
        if (lower.contains("nové město")) return 1;
        if (lower.contains("malá strana")) return 1;

        if (lower.contains("vinohrady")) return 2;
        if (lower.contains("vyšehrad")) return 2;

        if (lower.contains("žižkov")) return 3;
        if (lower.contains("jarov")) return 3;

        if (lower.contains("modřany")) return 4;
        if (lower.contains("kamýk")) return 12;
        if (lower.contains("braník")) return 4;
        if (lower.contains("krč")) return 4;
        if (lower.contains("michle")) return 4;
        if (lower.contains("nusle")) return 4;
        if (lower.contains("záběhlice")) return 10;

        if (lower.contains("smíchov")) return 5;
        if (lower.contains("jinonice")) return 5;
        if (lower.contains("hlubočepy")) return 5;

        if (lower.contains("dejvice")) return 6;
        if (lower.contains("bubeneč")) return 6;
        if (lower.contains("ruzyně")) return 6;
        if (lower.contains("střešovice")) return 6;
        if (lower.contains("vokovice")) return 6;
        if (lower.contains("suchdol")) return 6;
        if (lower.contains("břevnov")) return 6;

        if (lower.contains("holešovice")) return 7;
        if (lower.contains("troja")) return 7;

        if (lower.contains("libeň")) return 8;
        if (lower.contains("karlín")) return 8;
        if (lower.contains("bohnice")) return 8;
        if (lower.contains("čimice")) return 8;
        if (lower.contains("kobylisy")) return 8;

        if (lower.contains("vysočany")) return 9;
        if (lower.contains("letňany")) return 9;
        if (lower.contains("prosek")) return 9;
        if (lower.contains("hloubětín")) return 9;
        if (lower.contains("hrdlořezy")) return 9;

        if (lower.contains("vršovice")) return 10;
        if (lower.contains("strašnice")) return 10;
        if (lower.contains("pitkovice")) return 10;

        if (lower.contains("chodov")) return 11;
        if (lower.contains("háje")) return 11;

        if (lower.contains("stodůlky")) return 13;

        if (lower.contains("černý most")) return 14;
        if (lower.contains("hostavice")) return 14;

        if (lower.contains("hostivař")) return 15;
        if (lower.contains("horní měcholupy")) return 15;
        if (lower.contains("dolní měcholupy")) return 15;

        return -1;
    }

    private boolean layoutMatches(String needLayout, String listingLayout) {
        if (needLayout == null) {
            return true;
        }

        String normalizedNeed = normalizeLayout(needLayout);
        String normalizedListing = normalizeLayout(listingLayout);

        if (normalizedListing == null) {
            return false;
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

        if ("4+".equals(normalizedNeed)
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
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.toLowerCase().replaceAll("\\s+", "");
    }

    private String normalizeLocality(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }

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
        String raw = firstNonBlank(dto.locality(), dto.title(), dto.link());

        if (raw == null || raw.isBlank()) {
            return null;
        }

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
                .replaceAll("\\bmesic\\b", " ")
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
        if (normalized == null || normalized.isBlank()) {
            return null;
        }

        String[] parts = normalized.split(",");

        if (parts.length > 0) {
            String first = parts[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }

        return normalized;
    }

    private ListingDto pickBetterListing(ListingDto a, ListingDto b) {
        return scoreListing(b) > scoreListing(a) ? b : a;
    }

    private int scoreListing(ListingDto dto) {
        int score = 0;

        if (dto == null) {
            return score;
        }

        if (dto.photoUrl() != null && !dto.photoUrl().isBlank()) {
            score += 3;
        }

        if (dto.locality() != null && dto.locality().length() < 80) {
            score += 2;
        }

        if (dto.link() != null && !dto.link().isBlank()) {
            score += 1;
        }

        String source = dto.source() == null ? "" : dto.source().toLowerCase();

        if (source.contains("bezrealitky")) score += 5;
        else if (source.contains("sreality")) score += 4;
        else if (source.contains("idnes")) score += 3;
        else if (source.contains("bazo")) score += 2;

        return score;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }

        return null;
    }

    private List<ListingDto> diversifyBySource(List<ListingDto> input, int maxPerSource, int totalLimit) {
        Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        List<ListingDto> result = new ArrayList<>();

        for (ListingDto dto : input) {
            if (dto == null) {
                continue;
            }

            String source = dto.source();
            if (source == null || source.isBlank()) {
                source = "UNKNOWN";
            }

            int used = sourceCounts.getOrDefault(source, 0);
            if (used >= maxPerSource) {
                continue;
            }

            result.add(dto);
            sourceCounts.put(source, used + 1);

            if (result.size() >= totalLimit) {
                break;
            }
        }

        if (result.size() < totalLimit) {
            for (ListingDto dto : input) {
                if (dto == null || result.contains(dto)) {
                    continue;
                }

                result.add(dto);

                if (result.size() >= totalLimit) {
                    break;
                }
            }
        }

        return result;
    }

    private final AtomicReference<ParserRunStats> lastRunStats =
            new AtomicReference<>(new ParserRunStats(
                    0, 0, 0, 0,
                    0, 0, 0,
                    0, 0, 0, 0
            ));

    public ParserRunStats getLastRunStats() {
        return lastRunStats.get();
    }
}