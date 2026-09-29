package com.yourapp.rentbot.service;

import com.yourapp.rentbot.repo.SentLogRepo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class SentLogCleanupService {

    private final SentLogRepo sentLogRepo;
    private final int retentionDays;

    public SentLogCleanupService(
            SentLogRepo sentLogRepo,
            @Value("${rentbot.sent-log.retention-days:90}") int retentionDays
    ) {
        this.sentLogRepo = sentLogRepo;
        this.retentionDays = Math.max(30, retentionDays);
    }

    // каждый день в 03:00
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOldSentLogs() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        long deleted = sentLogRepo.deleteBySentAtBefore(cutoff);

        System.out.println("SentLog cleanup done. Retention days: " + retentionDays + ". Deleted rows: " + deleted);
    }
}
