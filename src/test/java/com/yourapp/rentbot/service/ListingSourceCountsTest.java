package com.yourapp.rentbot.service;

import com.yourapp.rentbot.service.dto.ListingDto;
import com.yourapp.rentbot.service.dto.ListingSourceCounts;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ListingSourceCountsTest {
    @Test
    void separatesSourcesVerifiedOwnersAndOverlappingPossibleOwnerSignals() {
        var counts = ListingSourceCounts.from(List.of(row("Owner"), row("Owner:42"),
                row("Sreality|possible-owner"), row("iDNES"), row("Bezrealitky"), row("Bazoš|possible-owner"),
                row("DigiReality Owner"), row("RealFree|possible-owner"), row("Unknown owner portal"), row(null)));
        assertThat(counts.total()).isEqualTo(10);
        assertThat(counts.verifiedOwner()).isEqualTo(2);
        assertThat(counts.possibleOwner()).isEqualTo(3);
        assertThat(counts.realfree()).isEqualTo(1);
        assertThat(counts.digireality()).isEqualTo(1);
        assertThat(counts.bazos()).isEqualTo(1);
        assertThat(counts.other()).isEqualTo(2);
        assertThat(counts.plus(counts).total()).isEqualTo(20);
        assertThat(counts.plus(counts).possibleOwner()).isEqualTo(6);
    }

    static ListingDto row(String source) {
        return new ListingDto("Byt", 15000, "https://example.invalid/" + source,
                "2+kk", "Praha", "", source, null);
    }
}
