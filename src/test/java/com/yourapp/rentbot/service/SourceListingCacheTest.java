package com.yourapp.rentbot.service;

import com.yourapp.rentbot.service.dto.ListingDto;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class SourceListingCacheTest {
    private final ListingDto row = new ListingDto("Byt", 15000, "https://example.test/1", "2+kk", "Praha", "", "test", LocalDateTime.now());

    @Test void sharesResultsUntilExpiryAndSeparatesSourcesAndRegions() throws Exception {
        AtomicLong now = new AtomicLong(1);
        SourceListingCache cache = new SourceListingCache(now::get);
        AtomicInteger loads = new AtomicInteger();
        SourceListingCache.Loader loader = () -> { loads.incrementAndGet(); return List.of(row); };
        cache.get("sreality:PRAHA", loader);
        cache.get("sreality:PRAHA", loader);
        cache.get("sreality:BRNO", loader);
        cache.get("bezrealitky:PRAHA", loader);
        assertThat(loads.get()).isEqualTo(3);
        now.addAndGet(600_000);
        cache.get("sreality:PRAHA", loader);
        assertThat(loads.get()).isEqualTo(4);
    }

    @Test void sharesAnInflightRefresh() throws Exception {
        SourceListingCache cache = new SourceListingCache();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        SourceListingCache.Loader loader = () -> {
            loads.incrementAndGet(); entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            return List.of(row);
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<List<ListingDto>> first = executor.submit(() -> cache.get("source:city", loader));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<List<ListingDto>> second = executor.submit(() -> cache.get("source:city", loader));
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).containsExactly(row);
            assertThat(second.get(5, TimeUnit.SECONDS)).containsExactly(row);
            assertThat(loads.get()).isEqualTo(1);
        }
    }

    @Test void backsOffFailuresAndBoundsStaleResults() throws Exception {
        AtomicLong now = new AtomicLong(1);
        SourceListingCache cache = new SourceListingCache(now::get);
        cache.get("key", () -> List.of(row));
        AtomicInteger loads = new AtomicInteger();
        SourceListingCache.Loader failure = () -> { loads.incrementAndGet(); throw new IOException("offline"); };
        now.addAndGet(600_000);
        assertThat(cache.get("key", failure)).containsExactly(row);
        assertThat(cache.get("key", failure)).containsExactly(row);
        assertThat(loads.get()).isEqualTo(1);
        now.addAndGet(1_200_000);
        assertThatThrownBy(() -> cache.get("key", failure)).isInstanceOf(IOException.class);
        assertThat(cache.get("key", failure)).isEmpty();
    }
}
