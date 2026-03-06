package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
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

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        List<UserFilter> users = userFilterRepo.findAllByActiveTrue();

        for (UserFilter user : users) {
            try {
                List<ListingDto> listings =
                        parserService.findNewListings(user.getTelegramUserId());

                for (ListingDto listing : listings) {
                    notificationService.sendIfNotSent(user, listing);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}