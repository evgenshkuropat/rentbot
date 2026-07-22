package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.OwnerListing;
import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.repo.OwnerListingRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class OwnerListingService {

    private final OwnerListingRepo ownerListingRepo;

    public OwnerListingService(OwnerListingRepo ownerListingRepo) {
        this.ownerListingRepo = ownerListingRepo;
    }

    public OwnerListing saveApproved(OwnerListing listing) {
        listing.setStatus(OwnerListing.Status.APPROVED);
        if (listing.getApprovedAt() == null) {
            listing.setApprovedAt(Instant.now());
        }
        if (listing.getCreatedAt() == null) {
            listing.setCreatedAt(Instant.now());
        }
        return ownerListingRepo.save(listing);
    }

    public OwnerListing savePending(OwnerListing listing) {
        listing.setStatus(OwnerListing.Status.PENDING);
        if (listing.getCreatedAt() == null) {
            listing.setCreatedAt(Instant.now());
        }
        listing.setApprovedAt(null);
        return ownerListingRepo.save(listing);
    }

    public Optional<OwnerListing> findPending(Long id) {
        return ownerListingRepo.findByIdAndStatus(id, OwnerListing.Status.PENDING);
    }

    public OwnerListing approve(OwnerListing listing) {
        listing.setStatus(OwnerListing.Status.APPROVED);
        listing.setApprovedAt(Instant.now());
        return ownerListingRepo.save(listing);
    }

    public OwnerListing archive(OwnerListing listing) {
        listing.setStatus(OwnerListing.Status.ARCHIVED);
        return ownerListingRepo.save(listing);
    }

    public List<ListingDto> fetchApprovedListings(Region region) {
        List<OwnerListing> listings = region == null
                ? ownerListingRepo.findByStatus(OwnerListing.Status.APPROVED)
                : ownerListingRepo.findByRegionAndStatus(region, OwnerListing.Status.APPROVED);

        return listings.stream()
                .map(this::toDto)
                .toList();
    }

    private ListingDto toDto(OwnerListing listing) {
        String description = listing.getDescription() == null || listing.getDescription().isBlank()
                ? ""
                : "\n" + listing.getDescription().trim();

        String title = listing.getTitle() + description + "\nКонтакт: " + listing.getContact();

        return new ListingDto(
                title,
                listing.getPriceCzk() == null ? 0 : listing.getPriceCzk(),
                "owner:" + listing.getId(),
                listing.getLayout(),
                listing.getLocality(),
                listing.getPhotoFileId(),
                "Власник",
                LocalDateTime.ofInstant(
                        listing.getApprovedAt() == null ? Instant.now() : listing.getApprovedAt(),
                        ZoneId.systemDefault())
        );
    }
}
