package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.SupportEvent;
import com.yourapp.rentbot.repo.SupportEventRepo;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SupportMetricsService {
    private final SupportEventRepo supportEventRepo;

    public SupportMetricsService(SupportEventRepo supportEventRepo) {
        this.supportEventRepo = supportEventRepo;
    }

    public void record(Long userId, SupportEvent.Type type) {
        SupportEvent event = new SupportEvent();
        event.setTelegramUserId(userId);
        event.setType(type);
        event.setCreatedAt(Instant.now());
        supportEventRepo.save(event);
    }

    public SupportMetrics since(Instant cutoff) {
        return new SupportMetrics(
                supportEventRepo.countByTypeAndCreatedAtAfter(SupportEvent.Type.OPENED, cutoff),
                supportEventRepo.countDistinctUsersByTypeSince(SupportEvent.Type.OPENED, cutoff),
                supportEventRepo.countByTypeAndCreatedAtAfter(SupportEvent.Type.RAIFFEISEN, cutoff),
                supportEventRepo.countByTypeAndCreatedAtAfter(SupportEvent.Type.PRIVATBANK, cutoff),
                supportEventRepo.countByTypeAndCreatedAtAfter(SupportEvent.Type.PAYPAL, cutoff),
                supportEventRepo.countByTypeAndCreatedAtAfter(SupportEvent.Type.REVOLUT, cutoff)
        );
    }

    public record SupportMetrics(long opened, long uniqueOpenedUsers, long raiffeisen,
                                 long privatBank, long paypal, long revolut) { }
}
