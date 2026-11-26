package com.yourapp.rentbot.service;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.LongSupplier;

/** One per source, shared across all its regions and detail requests. */
final class SourceRequestGate {
    private final String source;
    private final LongSupplier clock;
    private long blockedUntil;
    private long requests;
    private long rateLimitDelay = 900_000L;
    SourceRequestGate(String source) { this(source, System::currentTimeMillis); }
    SourceRequestGate(String source, LongSupplier clock) { this.source = source; this.clock = clock; }

    synchronized void check() throws IOException {
        if (clock.getAsLong() < blockedUntil) throw new IOException(source + " requests paused");
    }

    synchronized void observe(int status, String retryAfter) {
        if (status != 403 && status != 429) return;
        long now = clock.getAsLong();
        long delay = Math.max(status == 403 ? 3_600_000L : rateLimitDelay, retryAfterMillis(retryAfter, now));
        blockedUntil = Math.max(blockedUntil, now + delay);
        LoggerFactory.getLogger(SourceRequestGate.class).warn("Source cooldown source={} status={} durationSeconds={}", source, status, delay / 1000);
    }

    synchronized Connection.Response execute(Connection request, long rateLimitDelayMillis) throws IOException {
        rateLimitDelay = Math.max(60_000L, rateLimitDelayMillis);
        return execute(request);
    }

    synchronized Connection.Response execute(Connection request) throws IOException {
        check();
        long started = System.nanoTime();
        requests++;
        Connection.Response response = request.execute();
        LoggerFactory.getLogger(SourceRequestGate.class).info("Source HTTP source={} requestsSinceStart={} status={} durationMs={}",
                source, requests, response.statusCode(), (System.nanoTime() - started) / 1_000_000);
        int status = response.statusCode();
        observe(status, response.header("Retry-After"));
        if (status >= 400) {
            try (var body = response.bodyStream()) { /* release error response */ }
            throw new HttpStatusException(source + " HTTP " + status, status, ParserDiagnostics.url(response.url().toString()));
        }
        return response;
    }

    static long retryAfterMillis(String value, long now) {
        if (value == null || value.isBlank()) return 0;
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds <= 0 ? 0 : Math.min(seconds, 604_800L) * 1000L;
        } catch (NumberFormatException ignored) {
            try {
                long millis = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now;
                return Math.max(0, Math.min(millis, 604_800_000L));
            } catch (RuntimeException invalid) { return 0; }
        }
    }
}
