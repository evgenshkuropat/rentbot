package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.SentLog;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.i18n.Language;
import com.yourapp.rentbot.repo.SentLogRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import com.yourapp.rentbot.ui.Keyboards;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
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

        Language lang = user.getLanguage() != null ? user.getLanguage() : Language.UA;

        String sourceLabel = switch (lang) {
            case RU -> "Источник";
            case CZ -> "Zdroj";
            case EN -> "Source";
            default -> "Джерело";
        };

        String locationLabel = switch (lang) {
            case RU -> "Локация";
            case CZ -> "Lokalita";
            case EN -> "Location";
            default -> "Локація";
        };

        String linkLabel = switch (lang) {
            case RU -> "Ссылка";
            case CZ -> "Odkaz";
            case EN -> "Link";
            default -> "Посилання";
        };

        String caption =
                "🏠 " + nvl(listing.title()) + "\n" +
                        "🏷 " + sourceLabel + ": " + nvl(listing.source()) + "\n" +
                        "💰 " + (listing.priceCzk() > 0 ? listing.priceCzk() + " Kč" : "—") + "\n" +
                        "📍 " + locationLabel + ": " + nvl(listing.locality()) + "\n" +
                        "🔗 " + linkLabel + ": " + nvl(listing.link());

        try {
            String token = listingCacheService.put(listing);

            if (listing.photoUrl() != null && !listing.photoUrl().isBlank()) {
                telegramClient.execute(
                        SendPhoto.builder()
                                .chatId(chatId)
                                .photo(new InputFile(listing.photoUrl()))
                                .caption(caption)
                                .replyMarkup(Keyboards.addToFavoritesKeyboard(token, lang))
                                .build()
                );
            } else {
                telegramClient.execute(
                        SendMessage.builder()
                                .chatId(chatId)
                                .text(caption)
                                .replyMarkup(Keyboards.addToFavoritesKeyboard(token, lang))
                                .build()
                );
            }

        } catch (TelegramApiException e) {
            String msg = e.getMessage();

            if (msg != null && msg.contains("bot was blocked by the user")) {
                user.setActive(false);
                userFilterRepo.save(user);
                System.out.println("User blocked bot, subscription disabled: " + chatId);
                return;
            }

            if (msg != null && msg.contains("failed to get HTTP URL content")) {
                try {
                    String token = listingCacheService.put(listing);

                    telegramClient.execute(
                            SendMessage.builder()
                                    .chatId(chatId)
                                    .text(caption)
                                    .replyMarkup(Keyboards.addToFavoritesKeyboard(token, lang))
                                    .build()
                    );
                } catch (TelegramApiException ex) {
                    ex.printStackTrace();
                    return;
                }
            } else {
                e.printStackTrace();
                return;
            }
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

    public long countSent() {
        return sentLogRepo.count();
    }
}