package com.yourapp.rentbot.service;

import com.yourapp.rentbot.repo.SentLogRepo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class SentLogCleanupService {

    private final SentLogRepo sentLogRepo;

    public SentLogCleanupService(SentLogRepo sentLogRepo) {
        this.sentLogRepo = sentLogRepo;
    }

    // каждый день в 03:00
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOldSentLogs() {
        Instant cutoff = Instant.now().minus(14, ChronoUnit.DAYS);
        long deleted = sentLogRepo.deleteBySentAtBefore(cutoff);

        System.out.println("SentLog cleanup done. Deleted rows: " + deleted);
    }
}