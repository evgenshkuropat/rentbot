package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final UserFilterRepo userFilterRepo;
    private final ParserService parserService;
    private final NotificationService notificationService;

    public SchedulerService(UserFilterRepo userFilterRepo,
                            ParserService parserService,
                            NotificationService notificationService) {
        this.userFilterRepo = userFilterRepo;
        this.parserService = parserService;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${rentbot.polling.delay-ms:180000}")
    public void tick() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Scheduler already running, skipping...");
            return;
        }

        try {
            runScheduler();
        } finally {
            running.set(false);
        }
    }

    private void runScheduler() {
        List<UserFilter> users = userFilterRepo.findAllActiveFull();

        if (users.isEmpty()) {
            log.info("Scheduler: no active users");
            return;
        }

        log.info("Scheduler: checking {} users", users.size());

        List<ListingDto> allListings;

        try {
            allListings = parserService.fetchAllListingsOnce();
        } catch (Exception e) {
            log.error("Scheduler: parser run failed", e);
            return;
        }

        if (allListings == null || allListings.isEmpty()) {
            log.info("Scheduler: no listings from parsers");
            return;
        }

        log.info("Scheduler: fetched {} unique listings", allListings.size());

        int usersProcessed = 0;
        int usersWithMatches = 0;
        int totalCandidates = 0;
        int totalSendAttempts = 0;

        for (UserFilter user : users) {
            Long userId = user.getTelegramUserId();

            try {
                usersProcessed++;

                List<ListingDto> listings = parserService.filterForUser(allListings, user);

                if (listings == null || listings.isEmpty()) {
                    log.debug("User {}: no matching listings", userId);
                    continue;
                }

                usersWithMatches++;
                totalCandidates += listings.size();

                for (ListingDto listing : listings) {
                    try {
                        notificationService.sendIfNotSent(user, listing);
                        totalSendAttempts++;
                    } catch (Exception e) {
                        log.error("Error sending listing to user {} link={}", userId, listing.link(), e);
                    }
                }

            } catch (Exception e) {
                log.error("Error processing user {}", userId, e);
            }
        }

        log.info(
                "Scheduler finished: usersProcessed={}, usersWithMatches={}, totalCandidates={}, totalSendAttempts={}",
                usersProcessed,
                usersWithMatches,
                totalCandidates,
                totalSendAttempts
        );
    }
}