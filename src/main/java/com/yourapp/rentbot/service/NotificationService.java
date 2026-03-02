package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.SentLog;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.SentLogRepo;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Instant;

@Service
public class NotificationService {

    private final TelegramClient telegramClient;
    private final SentLogRepo sentLogRepo;

    public NotificationService(TelegramClient telegramClient, SentLogRepo sentLogRepo) {
        this.telegramClient = telegramClient;
        this.sentLogRepo = sentLogRepo;
    }

    public void sendIfNotSent(UserFilter user, ListingDto listing) {
        Long chatId = user.getTelegramUserId(); // в личке chatId == userId

        if (sentLogRepo.existsByTelegramUserIdAndListingKey(user.getTelegramUserId(), listing.key())) {
            return;
        }

        String text = """
                🏠 %s
                📍 %s
                💰 %d Kč
                🔗 %s
                """.formatted(listing.title(), listing.location(), listing.price(), listing.url());

        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build());

            SentLog log = new SentLog();
            log.setTelegramUserId(user.getTelegramUserId());
            log.setListingKey(listing.key());
            log.setSentAt(Instant.now());
            sentLogRepo.save(log);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}