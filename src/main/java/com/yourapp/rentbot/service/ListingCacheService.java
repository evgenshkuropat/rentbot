package com.yourapp.rentbot.service;

import com.yourapp.rentbot.service.dto.ListingDto;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ListingCacheService {

    private final Map<String, ListingDto> listingCache = new ConcurrentHashMap<>();

    public String put(ListingDto dto) {
        String token = UUID.randomUUID().toString().substring(0, 8);
        listingCache.put(token, dto);
        return token;
    }

    public ListingDto get(String token) {
        return listingCache.get(token);
    }
}