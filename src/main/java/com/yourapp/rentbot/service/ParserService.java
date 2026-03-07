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
    private final UserFilterRepo userFilterRepo;

    public ParserService(SrealityParser srealityParser,
                         IdnesParser idnesParser,
                         UserFilterRepo userFilterRepo) {
        this.srealityParser = srealityParser;
        this.idnesParser = idnesParser;
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

        for (int i = 1; i <= 22; i++) {
            if (lower.contains("praha " + i) || lower.contains("praha-" + i)) {
                return i;
            }
        }

        return -1;
    }

    private String normalizeLayout(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.toLowerCase().replaceAll("\\s+", "");
    }
}