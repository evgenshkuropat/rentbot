package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.flow.FlowStep;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserFilterRepo extends JpaRepository<UserFilter, Long> {

    @Query("""
        select distinct uf
        from UserFilter uf
        left join fetch uf.region
        left join fetch uf.regionGroup
        where uf.active = true
    """)
    List<UserFilter> findAllActiveFull();

    @Query("""
        select uf
        from UserFilter uf
        left join fetch uf.region
        left join fetch uf.regionGroup
        where uf.telegramUserId = :id
    """)
    Optional<UserFilter> findFullById(@Param("id") Long id);

    long countByActiveTrue();

    long countByOnboardedTrue();

    long countByOnboardedFalse();

    long countByLayoutIsNotNull();

    long countByMaxPriceIsNotNull();

    long countByLayout(String layout);

    long countByStep(FlowStep step);

    long countByStepAndActiveTrue(FlowStep step);

    long countByUpdatedAtAfter(Instant instant);

    @Query("select avg(uf.maxPrice) from UserFilter uf where uf.maxPrice is not null")
    Double findAverageMaxPrice();

    @Query("""
        select uf
        from UserFilter uf
        left join fetch uf.region
        left join fetch uf.regionGroup
        where uf.active = true
          and (
              uf.updatedAt is null
              or uf.updatedAt < :staleBefore
          )
          and (
              uf.reactivationSentAt is null
              or uf.reactivationSentAt < :canSendAgainBefore
          )
        order by uf.updatedAt asc
    """)
    List<UserFilter> findReactivationCandidates(@Param("staleBefore") Instant staleBefore,
                                                @Param("canSendAgainBefore") Instant canSendAgainBefore,
                                                Pageable pageable);

    @Query("""
        select uf
        from UserFilter uf
        left join fetch uf.region
        left join fetch uf.regionGroup
        where uf.active = false
          and uf.onboarded = true
          and uf.region is not null
          and uf.layout is not null
          and (uf.premiumUntil is null or uf.premiumUntil <= :now)
          and (uf.updatedAt is null or uf.updatedAt < :staleBefore)
          and (
              uf.inactiveReactivationSentAt is null
              or uf.inactiveReactivationSentAt < :canSendAgainBefore
          )
        order by uf.updatedAt asc
    """)
    List<UserFilter> findInactiveReactivationCandidates(@Param("now") Instant now,
                                                        @Param("staleBefore") Instant staleBefore,
                                                        @Param("canSendAgainBefore") Instant canSendAgainBefore,
                                                        Pageable pageable);

    @Query("""
        select uf
        from UserFilter uf
        left join fetch uf.region
        left join fetch uf.regionGroup
        where uf.active = true
          and uf.onboarded = true
          and uf.region is not null
          and uf.layout is not null
          and (
              uf.searchStatusSentAt is null
              or uf.searchStatusSentAt < :canSendAgainBefore
          )
        order by uf.searchStatusSentAt asc nulls first
    """)
    List<UserFilter> findSearchStatusCandidates(@Param("canSendAgainBefore") Instant canSendAgainBefore,
                                                Pageable pageable);

    long countBySearchStatusSentAtAfter(Instant cutoff);

    @Query("""
        select uf
        from UserFilter uf
        where uf.premiumUntil > :from
          and uf.premiumUntil <= :to
          and (uf.premiumExpiryReminderSentAt is null or uf.premiumExpiryReminderSentAt < uf.premiumUntil)
        order by uf.premiumUntil asc
    """)
    List<UserFilter> findPremiumExpiryReminderCandidates(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
        select uf
        from UserFilter uf
        where uf.active = true
          and uf.milestone1500SentAt is null
          and uf.telegramUserId is not null
        order by uf.updatedAt desc
    """)
    List<UserFilter> findMilestone1500Candidates(Pageable pageable);
}
