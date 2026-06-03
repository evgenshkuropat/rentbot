package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import com.yourapp.rentbot.service.dto.ParserRunStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final UserFilterRepo userFilterRepo;
    private final ParserService parserService;
    private final NotificationService notificationService;
    private final int maxNotificationsPerUserPerCycle;

    public SchedulerService(UserFilterRepo userFilterRepo,
                            ParserService parserService,
                            NotificationService notificationService,
                            @Value("${rentbot.notifications.max-per-user-per-cycle:5}") int maxNotificationsPerUserPerCycle) {
        this.userFilterRepo = userFilterRepo;
        this.parserService = parserService;
        this.notificationService = notificationService;
        this.maxNotificationsPerUserPerCycle = Math.max(1, maxNotificationsPerUserPerCycle);
    }

    @Scheduled(
            fixedDelayString = "${rentbot.polling.delay-ms:180000}",
            initialDelayString = "${rentbot.polling.initial-delay-ms:60000}"
    )
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
        parserService.resetBazosRateLimitCycle();
        parserService.resetSrealityTemporaryUnavailableCycle();

        List<UserFilter> users = userFilterRepo.findAllActiveFull();

        if (users.isEmpty()) {
            log.info("Scheduler: no active users");
            return;
        }

        log.info("Scheduler: checking {} users", users.size());

        Map<String, List<ListingDto>> listingsCache = new HashMap<>();

        int usersProcessed = 0;
        int usersWithMatches = 0;
        int totalCandidates = 0;
        int totalSendAttempts = 0;
        int totalSent = 0;
        int totalSkippedByLimit = 0;
        int parserRuns = 0;
        int aggregateFilteredBaseTotal = 0;
        int aggregateFilteredBaseSreality = 0;
        int aggregateFilteredBaseIdnes = 0;
        int aggregateFilteredBaseBezrealitky = 0;
        int aggregateFilteredBaseBazos = 0;
        int aggregateFinalFiltered = 0;
        int aggregateFinalSreality = 0;
        int aggregateFinalIdnes = 0;
        int aggregateFinalBezrealitky = 0;
        int aggregateFinalBazos = 0;

        for (UserFilter user : users) {
            Long userId = user.getTelegramUserId();

            try {
                usersProcessed++;

                String cacheKey = cacheKey(user);

                List<ListingDto> allListings = listingsCache.get(cacheKey);

                if (allListings == null) {
                    allListings = parserService.fetchListingsForFilter(user);
                    listingsCache.put(cacheKey, allListings);
                    parserRuns++;

                    log.info(
                            "Scheduler: parsed key={} listings={}",
                            cacheKey,
                            allListings != null ? allListings.size() : 0
                    );
                }

                List<ListingDto> listings = parserService.filterForUser(allListings, user);
                ParserRunStats userFilterStats = parserService.getLastRunStats();
                aggregateFilteredBaseTotal += userFilterStats.filteredBaseTotal();
                aggregateFilteredBaseSreality += userFilterStats.filteredBaseSreality();
                aggregateFilteredBaseIdnes += userFilterStats.filteredBaseIdnes();
                aggregateFilteredBaseBezrealitky += userFilterStats.filteredBaseBezrealitky();
                aggregateFilteredBaseBazos += userFilterStats.filteredBaseBazos();
                aggregateFinalFiltered += userFilterStats.finalFiltered();
                aggregateFinalSreality += userFilterStats.finalSreality();
                aggregateFinalIdnes += userFilterStats.finalIdnes();
                aggregateFinalBezrealitky += userFilterStats.finalBezrealitky();
                aggregateFinalBazos += userFilterStats.finalBazos();

                if (listings == null || listings.isEmpty()) {
                    log.debug("User {}: no matching listings", userId);
                    continue;
                }

                usersWithMatches++;
                totalCandidates += listings.size();

                int sentForUser = 0;

                for (ListingDto listing : listings) {
                    if (!user.isActive()) {
                        break;
                    }

                    if (sentForUser >= maxNotificationsPerUserPerCycle) {
                        totalSkippedByLimit++;
                        continue;
                    }

                    try {
                        totalSendAttempts++;
                        if (notificationService.sendIfNotSent(user, listing)) {
                            sentForUser++;
                            totalSent++;
                        }
                    } catch (Exception e) {
                        log.error("Error sending listing to user {} link={}", userId, listing.link(), e);
                    }
                }

            } catch (Exception e) {
                log.error("Error processing user {}", userId, e);
            }
        }

        parserService.updateLastRunFilterStats(
                aggregateFilteredBaseTotal,
                aggregateFilteredBaseSreality,
                aggregateFilteredBaseIdnes,
                aggregateFilteredBaseBezrealitky,
                aggregateFilteredBaseBazos,
                aggregateFinalFiltered,
                aggregateFinalSreality,
                aggregateFinalIdnes,
                aggregateFinalBezrealitky,
                aggregateFinalBazos
        );

        log.info(
                "Scheduler finished: usersProcessed={}, usersWithMatches={}, parserRuns={}, totalCandidates={}, totalSendAttempts={}, totalSent={}, totalSkippedByLimit={}, maxNotificationsPerUserPerCycle={}, aggregateFilteredBase={}, aggregateFinal={}",
                usersProcessed,
                usersWithMatches,
                parserRuns,
                totalCandidates,
                totalSendAttempts,
                totalSent,
                totalSkippedByLimit,
                maxNotificationsPerUserPerCycle,
                aggregateFilteredBaseTotal,
                aggregateFinalFiltered
        );
    }

    private String cacheKey(UserFilter user) {
        Region region = user.getRegion();

        if (region == null || region.getCode() == null) {
            return "DEFAULT";
        }

        return region.getCode();
    }
}
