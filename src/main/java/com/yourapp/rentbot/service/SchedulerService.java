package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

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

    @Scheduled(fixedDelayString = "${rentbot.polling.delay-ms:60000}")
    public void tick() {
        List<UserFilter> users = userFilterRepo.findAllActiveFull();

        if (users.isEmpty()) {
            log.info("Scheduler: no active users");
            return;
        }

        log.info("Scheduler: checking {} users", users.size());

        for (UserFilter user : users) {
            Long userId = user.getTelegramUserId();

            try {
                List<ListingDto> listings = parserService.findNewListings(userId);

                if (listings == null || listings.isEmpty()) {
                    log.debug("User {}: no new listings", userId);
                    continue;
                }

                int processedCount = 0;

                for (ListingDto listing : listings) {
                    try {
                        notificationService.sendIfNotSent(user, listing);
                        processedCount++;
                    } catch (Exception e) {
                        log.error("Error sending listing to user {} link={}", userId, listing.link(), e);
                    }
                }

                log.info("User {}: processed {} listings", userId, processedCount);

            } catch (Exception e) {
                log.error("Scheduler error for user {}", userId, e);
            }
        }
    }
}