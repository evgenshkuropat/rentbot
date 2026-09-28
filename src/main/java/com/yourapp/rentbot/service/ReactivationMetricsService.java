package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.ReactivationEvent;
import com.yourapp.rentbot.repo.ReactivationEventRepo;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ReactivationMetricsService {
    private final ReactivationEventRepo events;

    public ReactivationMetricsService(ReactivationEventRepo events) {
        this.events = events;
    }

    public void record(Long userId, ReactivationEvent.Type type) {
        ReactivationEvent event = new ReactivationEvent();
        event.setTelegramUserId(userId);
        event.setType(type);
        event.setCreatedAt(Instant.now());
        events.save(event);
    }

    public ReactivationMetrics since(Instant cutoff) {
        return new ReactivationMetrics(
                events.countByTypeAndCreatedAtAfter(ReactivationEvent.Type.SENT, cutoff),
                events.countDistinctUsersByTypeSince(ReactivationEvent.Type.SENT, cutoff),
                events.countByTypeAndCreatedAtAfter(ReactivationEvent.Type.CLICKED, cutoff),
                events.countDistinctUsersByTypeSince(ReactivationEvent.Type.CLICKED, cutoff),
                events.countByTypeAndCreatedAtAfter(ReactivationEvent.Type.ACTIVATED, cutoff),
                events.countDistinctUsersByTypeSince(ReactivationEvent.Type.ACTIVATED, cutoff)
        );
    }

    public record ReactivationMetrics(long sent, long uniqueSentUsers, long clicked,
                                      long uniqueClickedUsers, long activated,
                                      long uniqueActivatedUsers) { }
}
