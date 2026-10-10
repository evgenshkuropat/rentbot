package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.PremiumEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface PremiumEventRepo extends JpaRepository<PremiumEvent, Long> {
    boolean existsByTelegramUserIdAndType(Long telegramUserId, PremiumEvent.Type type);

    @Query("select count(distinct event.telegramUserId) from PremiumEvent event where event.type = :type and event.createdAt >= :cutoff")
    long countDistinctUsersByTypeSince(@Param("type") PremiumEvent.Type type, @Param("cutoff") Instant cutoff);
}
