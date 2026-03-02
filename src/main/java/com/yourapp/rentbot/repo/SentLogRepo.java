package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.SentLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentLogRepo extends JpaRepository<SentLog, Long> {
    boolean existsByTelegramUserIdAndListingKey(Long telegramUserId, String listingKey);
}