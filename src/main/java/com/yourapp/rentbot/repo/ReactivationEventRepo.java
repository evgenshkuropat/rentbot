package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.ReactivationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ReactivationEventRepo extends JpaRepository<ReactivationEvent, Long> {
    long countByTypeAndCreatedAtAfter(ReactivationEvent.Type type, Instant cutoff);

    @Query("select count(distinct event.telegramUserId) from ReactivationEvent event "
            + "where event.type = :type and event.createdAt >= :cutoff")
    long countDistinctUsersByTypeSince(@Param("type") ReactivationEvent.Type type,
                                      @Param("cutoff") Instant cutoff);
}
