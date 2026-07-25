package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.OwnerListing;
import com.yourapp.rentbot.domain.Region;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OwnerListingRepo extends JpaRepository<OwnerListing, Long> {
    List<OwnerListing> findByStatus(OwnerListing.Status status);

    List<OwnerListing> findByRegionAndStatus(Region region, OwnerListing.Status status);

    Optional<OwnerListing> findByIdAndStatus(Long id, OwnerListing.Status status);

    Optional<OwnerListing> findByIdAndStatusAndApprovedAtIsNull(Long id, OwnerListing.Status status);

    @Query("""
        select ol
        from OwnerListing ol
        left join fetch ol.region
        where ol.id = :id
    """)
    Optional<OwnerListing> findFullById(@Param("id") Long id);

    @Query("""
        select ol
        from OwnerListing ol
        left join fetch ol.region
        order by ol.createdAt desc
    """)
    List<OwnerListing> findRecentFull(Pageable pageable);
}
