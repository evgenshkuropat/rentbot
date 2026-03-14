package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.RegionGroup;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        try {
            all.addAll(srealityParser.fetchListings(srealityRegionId));
        } catch (Exception e) {
            System.out.println("Sreality parser failed: " + e.getMessage());
        }

        try {
            all.addAll(idnesParser.fetchListings(region, regionGroup));
        } catch (Exception e) {
            System.out.println("Idnes parser failed: " + e.getMessage());
        }

        try {
            all.addAll(bezrealitkyParser.fetchListings(region));
        } catch (Exception e) {
            System.out.println("Bezrealitky parser failed: " + e.getMessage());
        }

        try {
            all.addAll(bazosParser.fetchListings(region));
        } catch (Exception e) {
            System.out.println("Bazos parser failed: " + e.getMessage());
        }

        all = dedupeByLink(all);

        System.out.println("ALL LISTINGS FROM ALL PARSERS = " + all.size());
        System.out.println("FILTER layout = " + needLayout + ", maxPrice = " + maxPrice + ", group = " + groupCode);

        List<ListingDto> filtered = all.stream()
                .filter(x -> needLayout == null || needLayout.equals(normalizeLayout(x.layout())))
                .filter(x -> maxPrice == null || maxPrice == 0 || (x.priceCzk() > 0 && x.priceCzk() <= maxPrice))
                .filter(x -> matchesRegionGroup(x.locality(), groupCode))
                .sorted(Comparator.comparingInt(x -> x.priceCzk() == 0 ? Integer.MAX_VALUE : x.priceCzk()))
                .limit(20)
                .toList();

        System.out.println("FILTERED LISTINGS = " + filtered.size());

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

        // 1) Сначала пробуем найти прямой номер района
        for (int i = 1; i <= 22; i++) {
            if (lower.contains("praha " + i) || lower.contains("praha-" + i)) {
                return i;
            }
        }

        // 2) Потом пробуем по микрорайонам / частям Праги
        if (lower.contains("staré město")) return 1;
        if (lower.contains("nové město")) return 1;
        if (lower.contains("malá strana")) return 1;

        if (lower.contains("vinohrady")) return 2;
        if (lower.contains("vyšehrad")) return 2;

        if (lower.contains("žižkov")) return 3;
        if (lower.contains("jarov")) return 3;

        if (lower.contains("modřany")) return 4;
        if (lower.contains("kamýk")) return 4;
        if (lower.contains("braník")) return 4;
        if (lower.contains("krč")) return 4;
        if (lower.contains("michle")) return 4;
        if (lower.contains("nusle")) return 4;
        if (lower.contains("záběhlice")) return 4;

        if (lower.contains("smíchov")) return 5;
        if (lower.contains("stodůlky")) return 5;
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

        if (lower.contains("modřany")) return 12;
        if (lower.contains("kamýk")) return 12;

        if (lower.contains("stodůlky")) return 13;

        if (lower.contains("černý most")) return 14;
        if (lower.contains("hostavice")) return 14;

        if (lower.contains("hostivař")) return 15;
        if (lower.contains("horní měcholupy")) return 15;
        if (lower.contains("dolní měcholupy")) return 15;

        return -1;
    }

    private String normalizeLayout(String s) {

        if (s == null || s.isBlank()) {
            return null;
        }

        return s.toLowerCase().replaceAll("\\s+", "");
    }
}