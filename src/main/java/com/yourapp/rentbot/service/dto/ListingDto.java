package com.yourapp.rentbot.service.dto;

import java.time.LocalDateTime;

public record ListingDto(
        String title,
        int priceCzk,
        String link,
        String layout,
        String locality,
        String photoUrl,
        String source,
        LocalDateTime foundAt
) {
}