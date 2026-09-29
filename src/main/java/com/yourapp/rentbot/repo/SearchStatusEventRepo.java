package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.SearchStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SearchStatusEventRepo extends JpaRepository<SearchStatusEvent, Long> {
    long countByTypeAndCreatedAtAfter(SearchStatusEvent.Type type, Instant cutoff);

    @Query("select count(distinct event.telegramUserId) from SearchStatusEvent event "
            + "where event.type = :type and event.createdAt >= :cutoff")
    long countDistinctUsersByTypeSince(@Param("type") SearchStatusEvent.Type type, @Param("cutoff") Instant cutoff);
}
