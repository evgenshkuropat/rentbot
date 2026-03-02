package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.UserFilterRepo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SchedulerService {

    private final UserFilterRepo userFilterRepo;
    private final ParserService parserService;
    private final NotificationService notificationService;

    public SchedulerService(UserFilterRepo userFilterRepo,
                            ParserService parserService,
                            NotificationService notificationService) {
        this.userFilterRepo = userFilterRepo;
        this.parserService = parserService;
        this.notificationService = notificationService;
    }

    // каждые 60 секунд (для теста). Потом сделаем 300_000 (5 минут).
    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        List<UserFilter> users = userFilterRepo.findAllByActiveTrue();

        for (UserFilter user : users) {
            List<ListingDto> listings = parserService.findNewListings(user);

            for (ListingDto listing : listings) {
                // тут позже будет нормальный matching по фильтрам
                // (районы, dispo, цена)
                notificationService.sendIfNotSent(user, listing);
            }
        }
    }
}