package com.yourapp.rentbot.service;

import com.yourapp.rentbot.service.dto.ListingDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerSignalDetectorTest {

    @Test
    void detectsDirectOwnerPhrasesEvenWithCzechAccents() {
        assertThat(OwnerSignalDetector.hasPossibleOwnerSignal(
                "Pronájem bytu přímo od majitele, bez realitky"))
                .isTrue();
    }

    @Test
    void doesNotTreatAgencyListingAsPossibleOwner() {
        assertThat(OwnerSignalDetector.hasPossibleOwnerSignal(
                "Realitní kancelář zprostředkuje pronájem přímo od majitele"))
                .isFalse();
    }

    @Test
    void keepsVerifiedAndPossibleOwnerListingsSeparate() {
        ListingDto possible = listing("Bazoš|possible-owner");
        ListingDto verified = listing("Owner");

        assertThat(OwnerSignalDetector.isPossibleOwnerListing(possible)).isTrue();
        assertThat(OwnerSignalDetector.isVerifiedOwnerListing(possible)).isFalse();
        assertThat(OwnerSignalDetector.isVerifiedOwnerListing(verified)).isTrue();
    }

    private static ListingDto listing(String source) {
        return new ListingDto("Pronájem bytu", 20_000, "https://example.test/1", "2", "Praha", null, source, LocalDateTime.now());
    }
}
