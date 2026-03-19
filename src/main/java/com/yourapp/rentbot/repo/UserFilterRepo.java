package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.UserFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserFilterRepo extends JpaRepository<UserFilter, Long> {

    List<UserFilter> findAllByActiveTrue();

    // ✅ Грузим фильтр сразу с Region и RegionGroup (чтобы не было LazyInitializationException)
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
}