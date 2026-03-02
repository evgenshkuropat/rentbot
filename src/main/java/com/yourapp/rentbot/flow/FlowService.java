package com.yourapp.rentbot.flow;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.RegionRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class FlowService {

    private final UserFilterRepo repo;
    private final RegionRepo regionRepo;

    public FlowService(UserFilterRepo repo, RegionRepo regionRepo) {
        this.repo = repo;
        this.regionRepo = regionRepo;
    }

    @Transactional
    public UserFilter getOrCreate(long userId) {
        return repo.findById(userId).orElseGet(() -> {
            UserFilter f = new UserFilter();
            f.setTelegramUserId(userId);

            // дефолтный регион (на случай, если пользователь еще не выбрал)
            f.setRegion(defaultRegion());

            f.setStep(FlowStep.CITY);
            f.setUpdatedAt(Instant.now());
            return repo.save(f);
        });
    }

    @Transactional
    public UserFilter save(UserFilter f) {
        f.setUpdatedAt(Instant.now());
        return repo.save(f);
    }

    @Transactional
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

    /**
     * ⚠️ Старый pretty(UserFilter f) может падать из-за LAZY.
     * Используй prettyByUserId(userId) — он загружает все связи внутри транзакции.
     */
    @Transactional(readOnly = true)
    public String prettyByUserId(long userId) {
        UserFilter f = repo.findFullById(userId)
                .orElseThrow(() -> new IllegalStateException("UserFilter not found for userId=" + userId));
        return pretty(f);
    }

    /**
     * Если вдруг надо форматировать уже загруженный объект.
     */
    @Transactional(readOnly = true)
    public String pretty(UserFilter f) {
        // ВАЖНО: если f пришел извне и он "detached", то вытянем полный из БД
        if (f == null) return "—";

        Long id = f.getTelegramUserId();
        if (id == null) return "—";

        UserFilter full = repo.findFullById(id).orElse(f);
        return prettySafe(full);
    }

    private String prettySafe(UserFilter f) {
        String regionTitle = (f.getRegion() == null) ? "—" : nvl(f.getRegion().getTitle());
        String groupTitle = (f.getRegionGroup() == null) ? "—" : nvl(f.getRegionGroup().getTitle());

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
                nvl(f.getLayout()),
                f.getMaxPrice() == null ? "—" : f.getMaxPrice(),
                f.isActive() ? "увімкнено" : "вимкнено"
        );
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
}