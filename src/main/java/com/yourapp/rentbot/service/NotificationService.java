package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.SentLog;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.SentLogRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Service
public class NotificationService {

    private final TelegramClient telegramClient;
    private final SentLogRepo sentLogRepo;
    private final UserFilterRepo userFilterRepo;

    public NotificationService(TelegramClient telegramClient,
                               SentLogRepo sentLogRepo,
                               UserFilterRepo userFilterRepo) {
        this.telegramClient = telegramClient;
        this.sentLogRepo = sentLogRepo;
        this.userFilterRepo = userFilterRepo;
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
                💰 %s
                🔗 %s
                """.formatted(
                nvl(listing.title()),
                listing.priceCzk() > 0 ? (listing.priceCzk() + " Kč") : "—",
                nvl(listing.link())
        );

        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
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