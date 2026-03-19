package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.SentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface SentLogRepo extends JpaRepository<SentLog, Long> {
    boolean existsByTelegramUserIdAndListingKey(Long telegramUserId, String listingKey);

    @Transactional
    long deleteBySentAtBefore(Instant cutoff);
    long countBy();
}