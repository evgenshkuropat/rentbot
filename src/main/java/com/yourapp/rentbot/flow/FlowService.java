package com.yourapp.rentbot.flow;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.RegionRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class FlowService {

    private final UserFilterRepo repo;
    private final RegionRepo regionRepo;

    public FlowService(UserFilterRepo repo, RegionRepo regionRepo) {
        this.repo = repo;
        this.regionRepo = regionRepo;
    }

    public UserFilter getOrCreate(long userId) {
        return repo.findFullById(userId).orElseGet(() -> {
            UserFilter f = new UserFilter();
            f.setTelegramUserId(userId);
            f.setRegion(defaultRegion());
            f.setStep(FlowStep.CITY);
            f.setUpdatedAt(Instant.now());
            return repo.save(f);
        });
    }

    public UserFilter save(UserFilter f) {
        f.setUpdatedAt(Instant.now());
        return repo.save(f);
    }

    public UserFilter reset(long userId) {
        UserFilter f = getOrCreate(userId);

        f.setActive(false);
        f.setRegion(defaultRegion());
        f.setRegionGroup(null);
        f.setLayout(null);
        f.setMaxPrice(null);
        f.setStep(FlowStep.CITY);

        return save(f);
    }

    private Region defaultRegion() {
        return regionRepo.findByCode("PRAHA")
                .orElseThrow(() -> new IllegalStateException("Region PRAHA not found in DB"));
    }

    public String pretty(UserFilter f) {
        String regionTitle = f.getRegion() == null ? "—" : f.getRegion().getTitle();
        String groupTitle = f.getRegionGroup() == null ? "—" : f.getRegionGroup().getTitle();

        return """
        🧾 Ваші налаштування:
        🏙 Місто: %s
        📍 Райони: %s
        🏠 Тип: %s
        💰 Ціна до: %s Kč
        🔔 Сповіщення: %s
        """.formatted(
                regionTitle,
                groupTitle,
                prettyLayout(f.getLayout()),
                f.getMaxPrice() == null ? "—" : f.getMaxPrice(),
                f.isActive() ? "увімкнено" : "вимкнено"
        );
    }

    private String prettyLayout(String layout) {
        if (layout == null || layout.isBlank()) {
            return "—";
        }

        return switch (layout) {
            case "1" -> "1 кімната";
            case "2" -> "2 кімнати";
            case "3" -> "3 кімнати";
            case "4+" -> "4+ кімнати";
            default -> layout;
        };
    }
}