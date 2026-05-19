package com.yourapp.rentbot.service;

import com.yourapp.rentbot.service.dto.ListingDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ListingCacheService {

    private static final long CACHE_TTL_MILLIS = 6 * 60 * 60 * 1000L;

    private final Map<String, CacheEntry> listingCache = new ConcurrentHashMap<>();

    public String put(ListingDto dto) {
        String token = UUID.randomUUID().toString().substring(0, 8);
        listingCache.put(token, new CacheEntry(dto, System.currentTimeMillis()));
        return token;
    }

    public ListingDto get(String token) {
        CacheEntry entry = listingCache.get(token);
        if (entry == null) {
            return null;
        }

        if (System.currentTimeMillis() - entry.createdAtMillis() > CACHE_TTL_MILLIS) {
            listingCache.remove(token);
            return null;
        }

        return entry.listing();
    }

    @Scheduled(fixedDelayString = "${rentbot.cache.cleanup-delay-ms:3600000}")
    public void cleanupExpiredEntries() {
        long cutoff = System.currentTimeMillis() - CACHE_TTL_MILLIS;
        listingCache.entrySet().removeIf(entry -> entry.getValue().createdAtMillis() < cutoff);
    }

    private record CacheEntry(ListingDto listing, long createdAtMillis) {
    }
}
