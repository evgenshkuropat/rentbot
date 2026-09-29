package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.PremiumEvent;
import com.yourapp.rentbot.repo.PremiumEventRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.repo.PremiumSearchRepo;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class PremiumMetricsService {
    private final PremiumEventRepo events;
    private final UserFilterRepo users;
    private final PremiumSearchRepo searches;

    public PremiumMetricsService(PremiumEventRepo events, UserFilterRepo users, PremiumSearchRepo searches) {
        this.events = events;
        this.users = users;
        this.searches = searches;
    }

    public void record(Long userId, PremiumEvent.Type type) {
        if (userId == null) return;
        PremiumEvent event = new PremiumEvent();
        event.setTelegramUserId(userId);
        event.setType(type);
        event.setCreatedAt(Instant.now());
        events.save(event);
    }

    public PremiumMetrics since(Instant cutoff, Instant now, Instant expiringBefore) {
        return new PremiumMetrics(
                count(cutoff, PremiumEvent.Type.OPENED),
                count(cutoff, PremiumEvent.Type.PAYMENT_METHOD_SELECTED),
                count(cutoff, PremiumEvent.Type.REQUEST_SUBMITTED),
                count(cutoff, PremiumEvent.Type.APPROVED),
                count(cutoff, PremiumEvent.Type.REJECTED),
                users.countByPremiumUntilAfter(now),
                users.countByPremiumUntilAfterAndPremiumUntilLessThanEqual(now, expiringBefore),
                searches.countActiveForPremiumUsers(now)
        );
    }

    private long count(Instant cutoff, PremiumEvent.Type type) {
        return events.countDistinctUsersByTypeSince(type, cutoff);
    }

    public record PremiumMetrics(long opened, long paymentMethodSelected, long requestSubmitted,
                                 long approved, long rejected, long activeNow, long expiringWithin7Days,
                                 long secondSearchConfigured) { }
}
