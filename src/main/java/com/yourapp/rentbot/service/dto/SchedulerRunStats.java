package com.yourapp.rentbot.service.dto;

public record SchedulerRunStats(
        int usersProcessed,
        int usersWithMatches,
        int parserRuns,
        int totalCandidates,
        int totalSendAttempts,
        int totalSent,
        int totalSkippedByLimit,
        int aggregateFilteredBase,
        int aggregateFinal
) {
}
