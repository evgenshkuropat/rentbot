package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.UserFilter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ParserService {

    // позже тут будет реальный парсинг sreality/bezrealitky/etc.
    public List<ListingDto> findNewListings(UserFilter filter) {
        // Заглушка: возвращаем 1 “объявление” только для теста рассылки
        // Поменяй price чтобы проверять фильтры
        return List.of(new ListingDto(
                "test://listing/praha-1-15000",
                "Test byt 1+kk, Praha 1",
                15000,
                "Praha 1",
                "https://example.com"
        ));
    }
}