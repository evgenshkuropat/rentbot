package com.yourapp.rentbot.service;

import com.yourapp.rentbot.service.dto.ListingDto;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Shared by manual and scheduled searches. Each source/region has one refresh at a time. */
final class SourceListingCache {
    interface Loader { List<ListingDto> load() throws IOException; }
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SourceListingCache.class);
    private static final long TTL = 600_000, FAILURE_TTL = 120_000, MAX_STALE = 1_800_000;
    private final ConcurrentHashMap<String, Slot> slots = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    SourceListingCache() { this(System::currentTimeMillis); }
    SourceListingCache(LongSupplier clock) { this.clock = clock; }

    List<ListingDto> get(String key, Loader loader) throws IOException {
        Slot slot = slots.computeIfAbsent(key, ignored -> new Slot());
        synchronized (slot) {
            long now = clock.getAsLong();
            if (now < slot.refreshAt) {
                log.debug("Source cache hit key={} count={}", key, slot.rows.size());
                return now - slot.loadedAt < MAX_STALE ? slot.rows : List.of();
            }
            try {
                List<ListingDto> rows = List.copyOf(loader.load());
                slot.rows = rows;
                slot.loadedAt = clock.getAsLong();
                slot.refreshAt = slot.loadedAt + TTL;
                log.info("Source cache refreshed key={} count={} ttlSeconds={}", key, rows.size(), TTL / 1000);
                return rows;
            } catch (IOException | RuntimeException e) {
                slot.refreshAt = clock.getAsLong() + FAILURE_TTL;
                log.warn("Source cache refresh failed key={} errorType={} retrySeconds={}", key, e.getClass().getSimpleName(), FAILURE_TTL / 1000);
                if (clock.getAsLong() - slot.loadedAt < MAX_STALE && slot.loadedAt >= 0) return slot.rows;
                throw e;
            }
        }
    }
    private static class Slot {
        List<ListingDto> rows = List.of();
        long loadedAt = -MAX_STALE, refreshAt;
    }
}
