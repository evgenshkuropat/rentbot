package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.OwnerListing;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Keeps state-changing owner-listing moderation rules outside the Telegram update handler. */
@Service
public class OwnerListingModerationService {

    private final OwnerListingService ownerListingService;

    public OwnerListingModerationService(OwnerListingService ownerListingService) {
        this.ownerListingService = ownerListingService;
    }

    public Optional<OwnerListing> approvePending(Long listingId) {
        if (listingId == null) {
            return Optional.empty();
        }
        return ownerListingService.findPending(listingId).map(ownerListingService::approve);
    }

    public Optional<OwnerListing> rejectPending(Long listingId) {
        if (listingId == null) {
            return Optional.empty();
        }
        return ownerListingService.findPending(listingId).map(ownerListingService::archive);
    }

    public Optional<OwnerListing> archive(Long listingId) {
        if (listingId == null) {
            return Optional.empty();
        }
        return ownerListingService.findById(listingId).map(ownerListingService::archive);
    }
}
