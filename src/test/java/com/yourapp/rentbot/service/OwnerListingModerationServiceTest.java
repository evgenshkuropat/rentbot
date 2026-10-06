package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.OwnerListing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerListingModerationServiceTest {

    @Mock
    private OwnerListingService ownerListings;

    @Test
    void approvesOnlyPendingListing() {
        OwnerListing pending = new OwnerListing();
        OwnerListing approved = new OwnerListing();
        OwnerListingModerationService service = new OwnerListingModerationService(ownerListings);
        when(ownerListings.findPending(7L)).thenReturn(Optional.of(pending));
        when(ownerListings.approve(pending)).thenReturn(approved);

        assertThat(service.approvePending(7L)).contains(approved);
        verify(ownerListings).approve(pending);
    }

    @Test
    void neverChangesMissingOrInvalidPendingListing() {
        OwnerListingModerationService service = new OwnerListingModerationService(ownerListings);
        when(ownerListings.findPending(8L)).thenReturn(Optional.empty());

        assertThat(service.rejectPending(null)).isEmpty();
        assertThat(service.rejectPending(8L)).isEmpty();
        verify(ownerListings, never()).archive(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void archivesExistingListing() {
        OwnerListing listing = new OwnerListing();
        OwnerListing archived = new OwnerListing();
        OwnerListingModerationService service = new OwnerListingModerationService(ownerListings);
        when(ownerListings.findById(9L)).thenReturn(Optional.of(listing));
        when(ownerListings.archive(listing)).thenReturn(archived);

        assertThat(service.archive(9L)).contains(archived);
        verify(ownerListings).archive(listing);
    }
}
