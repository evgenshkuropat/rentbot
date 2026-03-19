package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.flow.FlowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserFilterRepo extends JpaRepository<UserFilter, Long> {

    List<UserFilter> findAllByActiveTrue();

    @Query("""
        select uf
        from UserFilter uf
        left join fetch uf.region
        left join fetch uf.regionGroup
        where uf.telegramUserId = :id
    """)
    Optional<UserFilter> findFullById(@Param("id") Long id);

    long countBy();

    long countByActiveTrue();

    long countByOnboardedTrue();

    long countByOnboardedFalse();

    long countByLayoutIsNotNull();

    long countByMaxPriceIsNotNull();

    long countByLayout(String layout);

    long countByStep(FlowStep step);

    long countByUpdatedAtAfter(Instant instant);

    @Query("select avg(uf.maxPrice) from UserFilter uf where uf.maxPrice is not null")
    Double findAverageMaxPrice();
}