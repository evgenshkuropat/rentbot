package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.PremiumSearch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PremiumSearchRepo extends JpaRepository<PremiumSearch, Long> {
    Optional<PremiumSearch> findByTelegramUserId(Long telegramUserId);
    Optional<PremiumSearch> findByTelegramUserIdAndActiveTrue(Long telegramUserId);
}
