package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.PremiumSearch;
import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import com.yourapp.rentbot.service.dto.ListingSourceCounts;
import com.yourapp.rentbot.service.dto.ParserRunStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerStatisticsTest {
    @Mock UserFilterRepo users;
    @Mock ParserService parsers;
    @Mock NotificationService notifications;
    @Mock PremiumService premium;
    @Mock PremiumOfferService offers;

    @Test
    void countsVerifiedOwnerHitsPerUserAndResetsAllCountersOnEmptyCycle() throws Exception {
        UserFilter first = user(1), second = user(2);
        var rows = rows();
        when(users.findAllActiveFull()).thenReturn(List.of(first, second), List.of());
        when(parsers.fetchListingsForFilter(first)).thenReturn(rows);
        when(parsers.filterForScheduler(rows, first)).thenReturn(result(rows));
        when(parsers.filterForScheduler(rows, second)).thenReturn(result(rows));
        SchedulerService scheduler = scheduler();
        scheduler.tick();
        var snapshot = scheduler.getLastRunStats();
        assertThat(snapshot.totalCandidates()).isEqualTo(10);
        assertThat(snapshot.finalSources().total()).isEqualTo(10);
        assertThat(snapshot.filteredBaseSources().total()).isEqualTo(10);
        assertThat(snapshot.ownerMatches()).isEqualTo(2);
        assertThat(snapshot.ownerMatches()).isEqualTo(snapshot.finalSources().verifiedOwner());
        assertThat(snapshot.usersWithOwnerMatches()).isEqualTo(2);
        assertThat(snapshot.finalSources().possibleOwner()).isEqualTo(2);
        assertThat(snapshot.finalSources().realfree()).isEqualTo(2);
        assertThat(snapshot.finalSources().digireality()).isEqualTo(2);
        assertThat(snapshot.parserRuns()).isEqualTo(1);
        assertThat(snapshot.totalSent()).isZero();

        scheduler.tick();
        var empty = scheduler.getLastRunStats();
        assertThat(empty.filteredBaseSources()).isEqualTo(ListingSourceCounts.empty());
        assertThat(empty.finalSources()).isEqualTo(ListingSourceCounts.empty());
        assertThat(empty.ownerMatches()).isZero();
        assertThat(snapshot.ownerMatches()).isEqualTo(2);
    }

    @Test
    void overlappingPremiumSearchesCountEachFinalListingOnlyOnce() throws Exception {
        UserFilter user = user(1);
        PremiumSearch search = new PremiumSearch();
        var rows = rows();
        when(users.findAllActiveFull()).thenReturn(List.of(user));
        when(premium.isActive(user)).thenReturn(true);
        when(premium.findActiveSearch(1L)).thenReturn(Optional.of(search));
        when(premium.asFilter(user, search)).thenReturn(user);
        when(parsers.fetchListingsForFilter(user)).thenReturn(rows);
        when(parsers.filterForScheduler(rows, user)).thenReturn(result(rows));
        SchedulerService scheduler = scheduler();
        scheduler.tick();
        var stats = scheduler.getLastRunStats();
        assertThat(stats.aggregateFilteredBase()).isEqualTo(10);
        assertThat(stats.aggregateFinal()).isEqualTo(5);
        assertThat(stats.finalSources().total()).isEqualTo(stats.totalCandidates());
        assertThat(stats.ownerMatches()).isEqualTo(1);
        assertThat(stats.finalSources().verifiedOwner()).isEqualTo(1);
        assertThat(stats.finalSources().possibleOwner()).isEqualTo(1);
        assertThat(stats.usersWithOwnerMatches()).isEqualTo(1);
    }

    private SchedulerService scheduler() {
        return new SchedulerService(users, parsers, notifications, premium, offers, 5, 10);
    }

    private static UserFilter user(long id) {
        Region region = new Region(); region.setCode("PRAHA"); region.setTitle("Praha");
        UserFilter user = new UserFilter(); user.setTelegramUserId(id); user.setRegion(region); user.setActive(true);
        return user;
    }

    private static List<ListingDto> rows() {
        return List.of(ListingSourceCountsTest.row("Owner"), ListingSourceCountsTest.row("RealFree|possible-owner"),
                ListingSourceCountsTest.row("DigiReality Owner"), ListingSourceCountsTest.row("Sreality"),
                ListingSourceCountsTest.row("Unknown"));
    }

    private static ParserService.FilterResult result(List<ListingDto> rows) {
        var counts = ListingSourceCounts.from(rows);
        return new ParserService.FilterResult(rows, ParserRunStats.empty().withFilterCounts(counts, counts));
    }
}
