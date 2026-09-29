package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.SearchStatusEvent;
import com.yourapp.rentbot.repo.SearchStatusEventRepo;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SearchStatusMetricsService {
    private final SearchStatusEventRepo events;

    public SearchStatusMetricsService(SearchStatusEventRepo events) {
        this.events = events;
    }

    public void record(Long userId, SearchStatusEvent.Type type) {
        SearchStatusEvent event = new SearchStatusEvent();
        event.setTelegramUserId(userId);
        event.setType(type);
        event.setCreatedAt(Instant.now());
        events.save(event);
    }

    public SearchStatusMetrics since(Instant cutoff) {
        return new SearchStatusMetrics(
                events.countByTypeAndCreatedAtAfter(SearchStatusEvent.Type.SENT, cutoff),
                events.countDistinctUsersByTypeSince(SearchStatusEvent.Type.SENT, cutoff),
                events.countByTypeAndCreatedAtAfter(SearchStatusEvent.Type.VIEWED, cutoff),
                events.countDistinctUsersByTypeSince(SearchStatusEvent.Type.VIEWED, cutoff),
                events.countByTypeAndCreatedAtAfter(SearchStatusEvent.Type.EDIT_OPENED, cutoff),
                events.countDistinctUsersByTypeSince(SearchStatusEvent.Type.EDIT_OPENED, cutoff)
        );
    }

    public record SearchStatusMetrics(long sent, long uniqueSentUsers,
                                      long viewed, long uniqueViewedUsers,
                                      long editOpened, long uniqueEditOpenedUsers) { }
}
