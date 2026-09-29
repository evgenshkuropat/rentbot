package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.PremiumSearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface PremiumSearchRepo extends JpaRepository<PremiumSearch, Long> {
    Optional<PremiumSearch> findByTelegramUserId(Long telegramUserId);
    Optional<PremiumSearch> findByTelegramUserIdAndActiveTrue(Long telegramUserId);
    List<PremiumSearch> findByActiveTrue();

    @Query("""
        select count(search) from PremiumSearch search
        where search.active = true
          and exists (select user from UserFilter user
                      where user.telegramUserId = search.telegramUserId and user.premiumUntil > :now)
    """)
    long countActiveForPremiumUsers(@Param("now") java.time.Instant now);
}
