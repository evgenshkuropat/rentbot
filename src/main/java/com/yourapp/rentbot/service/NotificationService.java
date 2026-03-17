package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.SentLog;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.SentLogRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import com.yourapp.rentbot.ui.Keyboards;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Service
public class NotificationService {

    private final TelegramClient telegramClient;
    private final SentLogRepo sentLogRepo;
    private final UserFilterRepo userFilterRepo;
    private final ListingCacheService listingCacheService;

    public NotificationService(TelegramClient telegramClient,
                               SentLogRepo sentLogRepo,
                               UserFilterRepo userFilterRepo,
                               ListingCacheService listingCacheService) {
        this.telegramClient = telegramClient;
        this.sentLogRepo = sentLogRepo;
        this.userFilterRepo = userFilterRepo;
        this.listingCacheService = listingCacheService;
    }

    public void sendIfNotSent(UserFilter user, ListingDto listing) {
        Long chatId = user.getTelegramUserId();
        String key = listing.link();

        if (key == null || key.isBlank()) {
            return;
        }

        if (sentLogRepo.existsByTelegramUserIdAndListingKey(chatId, key)) {
            return;
        }

        String text = """
                🏠 %s
                🏷 Джерело: %s
                💰 %s
                📍 %s
                🔗 %s
                """.formatted(
                nvl(listing.title()),
                nvl(listing.source()),
                listing.priceCzk() > 0 ? (listing.priceCzk() + " Kč") : "—",
                nvl(listing.locality()),
                nvl(listing.link())
        );

        try {
            String token = listingCacheService.put(listing);

            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(Keyboards.addToFavoritesKeyboard(token))
                    .build());

        } catch (TelegramApiException e) {
            String msg = e.getMessage();

            if (msg != null && msg.contains("bot was blocked by the user")) {
                user.setActive(false);
                userFilterRepo.save(user);
                System.out.println("User blocked bot, subscription disabled: " + chatId);
                return;
            }

            e.printStackTrace();
            return;
        }

        saveSent(chatId, key);
    }

    private void saveSent(Long telegramUserId, String key) {
        SentLog log = new SentLog();
        log.setTelegramUserId(telegramUserId);
        log.setListingKey(key);
        sentLogRepo.save(log);
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
}