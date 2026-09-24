package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.PremiumSearch;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.PremiumSearchRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class PremiumService {
    private final UserFilterRepo userFilterRepo;
    private final PremiumSearchRepo premiumSearchRepo;

    public PremiumService(UserFilterRepo userFilterRepo, PremiumSearchRepo premiumSearchRepo) {
        this.userFilterRepo = userFilterRepo;
        this.premiumSearchRepo = premiumSearchRepo;
    }

    public boolean isActive(UserFilter user) {
        return user != null && user.getPremiumUntil() != null && user.getPremiumUntil().isAfter(Instant.now());
    }

    public UserFilter activate(UserFilter user, int days) {
        user.setPremiumUntil(Instant.now().plus(Math.max(1, days), ChronoUnit.DAYS));
        return userFilterRepo.save(user);
    }

    public Optional<PremiumSearch> findActiveSearch(Long userId) {
        return premiumSearchRepo.findByTelegramUserIdAndActiveTrue(userId);
    }

    public PremiumSearch getOrCreateSearch(UserFilter user) {
        return premiumSearchRepo.findByTelegramUserId(user.getTelegramUserId()).orElseGet(() -> {
            PremiumSearch search = new PremiumSearch();
            search.setTelegramUserId(user.getTelegramUserId());
            search.setActive(false);
            search.setUpdatedAt(Instant.now());
            return premiumSearchRepo.save(search);
        });
    }

    public PremiumSearch save(PremiumSearch search) {
        search.setUpdatedAt(Instant.now());
        return premiumSearchRepo.save(search);
    }

    public UserFilter asFilter(UserFilter user, PremiumSearch search) {
        UserFilter filter = new UserFilter();
        filter.setTelegramUserId(user.getTelegramUserId());
        filter.setLanguage(user.getLanguage());
        filter.setActive(true);
        filter.setOnboarded(true);
        filter.setRegion(search.getRegion());
        filter.setRegionGroup(search.getRegionGroup());
        filter.setLayout(search.getLayout());
        filter.setMaxPrice(search.getMaxPrice());
        return filter;
    }
}
