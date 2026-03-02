package com.yourapp.rentbot.service;

public record ListingDto(
        String key,     // уникальный ключ (например URL)
        String title,
        int price,
        String location,
        String url
) {}