package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.PremiumEvent;
import com.yourapp.rentbot.repo.PremiumEventRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.repo.PremiumSearchRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PremiumMetricsServiceTest {

    @Mock private PremiumEventRepo events;
    @Mock private UserFilterRepo users;
    @Mock private PremiumSearchRepo searches;

    @Test
    void buildsPremiumFunnelAndExpiryMetrics() {
        Instant now = Instant.parse("2026-09-29T10:00:00Z");
        Instant cutoff = now.minusSeconds(30L * 24 * 60 * 60);
        Instant expiry = now.plusSeconds(7L * 24 * 60 * 60);
        PremiumMetricsService service = new PremiumMetricsService(events, users, searches);

        when(events.countDistinctUsersByTypeSince(PremiumEvent.Type.OPENED, cutoff)).thenReturn(11L);
        when(events.countDistinctUsersByTypeSince(PremiumEvent.Type.CONTEXTUAL_OFFER_SHOWN, cutoff)).thenReturn(8L);
        when(events.countDistinctUsersByTypeSince(PremiumEvent.Type.CONTEXTUAL_OFFER_CLICKED, cutoff)).thenReturn(5L);
        when(events.countDistinctUsersByTypeSince(PremiumEvent.Type.PAYMENT_METHOD_SELECTED, cutoff)).thenReturn(7L);
        when(events.countDistinctUsersByTypeSince(PremiumEvent.Type.REQUEST_SUBMITTED, cutoff)).thenReturn(4L);
        when(events.countDistinctUsersByTypeSince(PremiumEvent.Type.APPROVED, cutoff)).thenReturn(3L);
        when(events.countDistinctUsersByTypeSince(PremiumEvent.Type.REJECTED, cutoff)).thenReturn(1L);
        when(users.countByPremiumUntilAfter(now)).thenReturn(9L);
        when(users.countByPremiumUntilAfterAndPremiumUntilLessThanEqual(now, expiry)).thenReturn(2L);
        when(searches.countActiveForPremiumUsers(now)).thenReturn(6L);

        assertThat(service.since(cutoff, now, expiry)).isEqualTo(
                new PremiumMetricsService.PremiumMetrics(11, 8, 5, 7, 4, 3, 1, 9, 2, 6));
    }
}
