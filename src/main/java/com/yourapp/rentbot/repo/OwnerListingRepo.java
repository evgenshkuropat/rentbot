package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.OwnerListing;
import com.yourapp.rentbot.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OwnerListingRepo extends JpaRepository<OwnerListing, Long> {
    List<OwnerListing> findByStatus(OwnerListing.Status status);

    List<OwnerListing> findByRegionAndStatus(Region region, OwnerListing.Status status);

    Optional<OwnerListing> findByIdAndStatus(Long id, OwnerListing.Status status);
}
