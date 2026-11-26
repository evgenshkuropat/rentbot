package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParserServiceSourcesTest {
    @Mock SrealityParser sreality;
    @Mock IdnesParser idnes;
    @Mock RealFreeParser realfree;
    @Mock BezrealitkyParser bezrealitky;
    @Mock BazosParser bazos;
    @Mock DigiRealityParser digi;
    @Mock OwnerListingService owners;
    @Mock UserFilterRepo users;
    @Mock PremiumService premium;

    @Test
    void includesBothSourcesAndRecordsRealFreeCount() throws Exception {
        UserFilter filter = filter("PRAHA", "Praha");
        ListingDto first = row("iDNES", "Praha, Dlouha", 15000);
        ListingDto second = row("RealFree", "Praha, Kratka", 17000);
        when(idnes.fetchListings(filter.getRegion(), null)).thenReturn(List.of(first));
        when(realfree.fetchListings(filter.getRegion())).thenReturn(List.of(second));
        ParserService service = service(true, true);
        assertThat(service.fetchListingsForFilter(filter)).contains(first, second);
        assertThat(service.getLastRunStats().realfreeRaw()).isEqualTo(1);
        assertThat(service.getLastRunStats().idnesRaw()).isEqualTo(1);
    }

    @Test
    void idnesFailureDoesNotDiscardRealFreeListings() throws Exception {
        UserFilter filter = filter("PRAHA", "Praha");
        ListingDto listing = row("RealFree", "Praha", 15000);
        when(idnes.fetchListings(filter.getRegion(), null)).thenThrow(new IOException("Unavailable"));
        when(realfree.fetchListings(filter.getRegion())).thenReturn(List.of(listing));
        assertThat(service(true, true).fetchListingsForFilter(filter)).contains(listing);
    }

    @Test
    void respectsBothDisableFlags() throws Exception {
        service(false, false).fetchListingsForFilter(filter("PRAHA", "Praha"));
        verifyNoInteractions(idnes, realfree);
    }

    @Test
    void possibleOwnerMarkerDoesNotBypassCityFilter() {
        UserFilter filter = filter("KOLIN", "Kolin");
        assertThat(service(true, true).filterForUser(
                List.of(row("RealFree|possible-owner", "Brno", 15000)), filter)).isEmpty();
    }

    @Test
    void appliesSameCityScopeToEverySourceIncludingOwners() {
        for (String source : List.of("Sreality", "iDNES", "Bezrealitky", "Bazos", "DigiReality", "RealFree", "Owner", "Sreality|possible-owner")) {
            UserFilter filter = filter("HODONIN", "Hodonin");
            ParserService parser = service(true, true);
            assertThat(parser.filterForUser(List.of(row(source, "Zdanice", 15000)), filter)).as(source).isEmpty();
            assertThat(parser.filterForUser(List.of(row(source, "Hodonin, Dlouha", 15000)), filter)).as(source).hasSize(1);
            assertThat(parser.filterForUser(List.of(row(source, "Zdanice, okres Hodonin", 15000)), filter)).as(source).isEmpty();
        }
    }

    @Test
    void repeatedRegionalCollectionsShareExternalResultsButRefreshOwnerListings() throws Exception {
        UserFilter filter = filter("PRAHA", "Praha");
        ParserService parser = service(true, true);
        parser.fetchListingsForFilter(filter);
        parser.fetchListingsForFilter(filter);
        verify(sreality, times(1)).fetchListings(eq("PRAHA"), any());
        verify(idnes, times(1)).fetchListings(filter.getRegion(), null);
        verify(bezrealitky, times(1)).fetchListings(filter.getRegion());
        verify(realfree, times(1)).fetchListings(filter.getRegion());
        verify(bazos, times(1)).fetchListings(filter.getRegion());
        verify(owners, times(2)).fetchApprovedListings(filter.getRegion());
    }

    private ParserService service(boolean idnesEnabled, boolean realfreeEnabled) {
        return new ParserService(sreality, idnes, realfree, bezrealitky, bazos, digi, owners,
                users, premium, idnesEnabled, true, realfreeEnabled);
    }

    private static ListingDto row(String source, String locality, int price) {
        return new ListingDto("Byt 2+kk", price, "https://example.invalid/" + source, "2+kk", locality,
                "", source, LocalDateTime.now());
    }

    private static UserFilter filter(String code, String title) {
        Region region = new Region(); region.setCode(code); region.setTitle(title);
        UserFilter filter = new UserFilter(); filter.setRegion(region); return filter;
    }
}
