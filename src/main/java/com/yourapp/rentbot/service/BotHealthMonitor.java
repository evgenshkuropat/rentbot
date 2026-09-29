package com.yourapp.rentbot.service;

import com.yourapp.rentbot.service.dto.ParserRunStats;
import com.yourapp.rentbot.service.dto.SchedulerRunStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Component
public class BotHealthMonitor {
    private static final Logger log = LoggerFactory.getLogger(BotHealthMonitor.class);
    private final SchedulerService scheduler;
    private final ParserService parser;
    private final TelegramClient telegramClient;
    private final long adminId;
    private final int stalledAfterMinutes;
    private final int maxCycleDurationMinutes;
    private Instant lastObservedCycle;
    private final Instant startedAt = Instant.now();
    private int consecutiveEmptyCycles;
    private boolean schedulerAlerted;
    private final Set<String> activeAlerts = new HashSet<>();

    public BotHealthMonitor(SchedulerService scheduler,
                            ParserService parser,
                            TelegramClient telegramClient,
                            @Value("${TELEGRAM_ADMIN_ID}") long adminId,
                            @Value("${rentbot.health.stalled-after-minutes:40}") int stalledAfterMinutes,
                            @Value("${rentbot.health.max-cycle-duration-minutes:60}") int maxCycleDurationMinutes) {
        this.scheduler = scheduler;
        this.parser = parser;
        this.telegramClient = telegramClient;
        this.adminId = adminId;
        this.stalledAfterMinutes = Math.max(20, stalledAfterMinutes);
        this.maxCycleDurationMinutes = Math.max(this.stalledAfterMinutes, maxCycleDurationMinutes);
    }

    @Scheduled(fixedDelayString = "${rentbot.health.check-delay-ms:300000}", initialDelayString = "${rentbot.health.initial-delay-ms:300000}")
    public void check() {
        Instant completedAt = scheduler.getLastCompletedAt();
        if (completedAt == null) {
            if (Duration.between(startedAt, Instant.now()).toMinutes() >= stalledAfterMinutes) {
                alertOnce("scheduler-not-started", "⚠️ Контроль бота: після запуску понад " + stalledAfterMinutes + " хвилин не завершився жоден повний цикл розсилки.");
            }
            return;
        }

        Instant cycleStartedAt = scheduler.getStartedAt();
        boolean cycleRunningNormally = scheduler.isRunning() && cycleStartedAt != null
                && Duration.between(cycleStartedAt, Instant.now()).toMinutes() < maxCycleDurationMinutes;
        if (!cycleRunningNormally && Duration.between(completedAt, Instant.now()).toMinutes() >= stalledAfterMinutes) {
            schedulerAlerted = true;
            alertOnce("scheduler-stalled", "⚠️ Контроль бота: повний цикл не завершувався понад " + stalledAfterMinutes + " хвилин. Перевір Railway logs.");
            return;
        }

        if (schedulerAlerted) {
            schedulerAlerted = false;
            resolve("scheduler-stalled", "✅ Контроль бота: повні цикли розсилки знову завершуються нормально.");
        }
        resolve("scheduler-not-started", null);

        if (completedAt.equals(lastObservedCycle)) return;
        lastObservedCycle = completedAt;
        SchedulerRunStats stats = scheduler.getLastRunStats();
        if (stats.usersProcessed() == 0) return;

        boolean empty = stats.aggregateFilteredBase() == 0 || stats.aggregateFinal() == 0;
        consecutiveEmptyCycles = empty ? consecutiveEmptyCycles + 1 : 0;
        if (consecutiveEmptyCycles >= 2) {
            ParserRunStats sources = parser.getLastRunStats();
            alertOnce("empty-results", "⚠️ Контроль бота: два повних цикли поспіль без результатів. "
                    + "Sreality=" + sources.srealityRaw()
                    + ", Bezrealitky=" + sources.bezrealitkyRaw()
                    + ", Bazoš=" + sources.bazosRaw()
                    + ". Перевір парсери.");
        } else if (!empty) {
            resolve("empty-results", "✅ Контроль бота: результати з парсерів знову надходять нормально.");
        }
    }

    private void alertOnce(String key, String text) {
        if (activeAlerts.add(key)) send(text);
    }

    private void resolve(String key, String text) {
        if (activeAlerts.remove(key) && text != null) send(text);
    }

    private void send(String text) {
        try {
            telegramClient.execute(SendMessage.builder().chatId(adminId).text(text).build());
        } catch (TelegramApiException e) {
            log.warn("Could not deliver bot health alert to admin", e);
        }
    }
}
