package com.yourapp.rentbot.config;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.RegionGroup;
import com.yourapp.rentbot.repo.RegionGroupRepo;
import com.yourapp.rentbot.repo.RegionRepo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RegionRepo regionRepo;
    private final RegionGroupRepo regionGroupRepo;

    public DataInitializer(RegionRepo regionRepo, RegionGroupRepo regionGroupRepo) {
        this.regionRepo = regionRepo;
        this.regionGroupRepo = regionGroupRepo;
    }

    @Override
    public void run(String... args) {
        if (regionRepo.findByCode("PRAHA").isPresent()) {
            return;
        }

        Region praha = saveRegion("PRAHA", "Praha", true, 10);
        saveRegion("BRNO", "Brno", false, 14);
        saveRegion("OSTRAVA", "Ostrava", false, 78);
        saveRegion("PLZEN", "Plzeň", false, 43);
        saveRegion("LIBEREC", "Liberec", false, 51);
        saveRegion("OLOMOUC", "Olomouc", false, 71);
        saveRegion("HRADEC_KRALOVE", "Hradec Králové", false, 60);
        saveRegion("PARDUBICE", "Pardubice", false, 86);
        saveRegion("CESKE_BUDEJOVICE", "České Budějovice", false, 27);
        saveRegion("ZLIN", "Zlín", false, 99);
        saveRegion("JIHLAVA", "Jihlava", false, 53);
        saveRegion("USTI_NAD_LABEM", "Ústí nad Labem", false, 94);
        saveRegion("KARLOVY_VARY", "Karlovy Vary", false, 39);
        saveRegion("MLADA_BOLESLAV", "Mladá Boleslav", false, 57);
        saveRegion("KOLIN", "Kolín", false, 64);
        saveRegion("KUTNA_HORA", "Kutná Hora", false, 67);

        saveGroup(praha, "PRAHA_ALL", "Всі райони");
        saveGroup(praha, "PRAHA_1_3", "Praha 1-3");
        saveGroup(praha, "PRAHA_4_6", "Praha 4-6");
        saveGroup(praha, "PRAHA_7_10", "Praha 7-10");
        saveGroup(praha, "PRAHA_11_15", "Praha 11-15");

        System.out.println("✅ Regions and Praha groups initialized");
    }

    private Region saveRegion(String code, String title, boolean hasDistricts, Integer srealityRegionId) {
        Region region = new Region();
        region.setCode(code);
        region.setTitle(title);
        region.setHasDistricts(hasDistricts);
        region.setSrealityRegionId(srealityRegionId);
        return regionRepo.save(region);
    }

    private void saveGroup(Region region, String code, String title) {
        RegionGroup group = new RegionGroup();
        group.setRegion(region);
        group.setCode(code);
        group.setTitle(title);
        regionGroupRepo.save(group);
    }
}