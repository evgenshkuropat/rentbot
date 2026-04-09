package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegionRepo extends JpaRepository<Region, Long> {
    Optional<Region> findByCode(String code);
}