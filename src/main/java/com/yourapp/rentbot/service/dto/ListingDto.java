package com.yourapp.rentbot.service.dto;

public record ListingDto(
        String title,
        int priceCzk,
        String link,
        String layout,
        String photoUrl,
        String locality
) {
}