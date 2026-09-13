package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.SupportEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SupportEventRepo extends JpaRepository<SupportEvent, Long> {
    long countByTypeAndCreatedAtAfter(SupportEvent.Type type, Instant cutoff);

    @Query("select count(distinct event.telegramUserId) from SupportEvent event "
            + "where event.type = :type and event.createdAt >= :cutoff")
    long countDistinctUsersByTypeSince(@Param("type") SupportEvent.Type type, @Param("cutoff") Instant cutoff);
}
