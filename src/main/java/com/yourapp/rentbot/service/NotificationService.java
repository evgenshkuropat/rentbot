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

        String caption =
                "🏠 " + nvl(listing.title()) + "\n" +
                        "🏷 " + sourceLabel + ": " + nvl(listing.source()) + "\n" +
                        "💰 " + (listing.priceCzk() > 0 ? listing.priceCzk() + " Kč" : "—") + "\n" +
                        "📍 " + locationLabel + ": " + nvl(listing.locality());

        String token = listingCacheService.put(listing);
        String link = safeUrl(listing.link());

        try {
            if (hasUsablePhotoUrl(listing.photoUrl())) {
                try {
                    telegramClient.execute(
                            SendPhoto.builder()
                                    .chatId(chatId)
                                    .photo(new InputFile(listing.photoUrl()))
                                    .caption(trimCaption(caption))
                                    .replyMarkup(Keyboards.listingKeyboard(token, link, lang))
                                    .build()
                    );

                    saveSent(chatId, key);
                    return;

                } catch (TelegramApiException e) {
                    String msg = e.getMessage();

                    if (isBlockedByUser(msg)) {
                        deactivateUser(user, chatId);
                        return;
                    }

                    System.out.println("SendPhoto failed, fallback to text. link=" + listing.link());
                } catch (Exception e) {
                    System.out.println("Unexpected SendPhoto failure, fallback to text. link=" + listing.link());
                }
            }

            sendTextFallback(chatId, caption, token, link, lang);
            saveSent(chatId, key);

        } catch (TelegramApiException e) {
            String msg = e.getMessage();

            if (isBlockedByUser(msg)) {
                deactivateUser(user, chatId);
                return;
            }

            System.out.println("SendMessage failed in NotificationService for link=" + listing.link()
                    + ", error=" + msg);
        } catch (Exception e) {
            System.out.println("Unexpected notification failure for link=" + listing.link()
                    + ", error=" + e.getMessage());
        }
    }

    private void sendTextFallback(Long chatId,
                                  String caption,
                                  String token,
                                  String link,
                                  Language lang) throws TelegramApiException {
        telegramClient.execute(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(caption)
                        .replyMarkup(Keyboards.listingKeyboard(token, link, lang))
                        .build()
        );
    }

    private void deactivateUser(UserFilter user, Long chatId) {
        user.setActive(false);
        userFilterRepo.save(user);
        System.out.println("User blocked bot, subscription disabled: " + chatId);
    }

    private boolean hasUsablePhotoUrl(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return false;
        }

        String lower = photoUrl.toLowerCase();

        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            return false;
        }

        return !lower.contains(".html")
                && !lower.contains("placeholder")
                && !lower.contains("noimage");
    }

    private boolean isBlockedByUser(String msg) {
        return msg != null && msg.contains("bot was blocked by the user");
    }

    private String trimCaption(String text) {
        if (text == null) {
            return "";
        }

        return text.length() <= 1024 ? text : text.substring(0, 1020) + "...";
    }

    private void saveSent(Long telegramUserId, String key) {
        SentLog log = new SentLog();
        log.setTelegramUserId(telegramUserId);
        log.setListingKey(key);
        sentLogRepo.save(log);
    }

    private String safeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "https://t.me/zhytloCZ_bot";
        }
        return url;
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    public long countSent() {
        return sentLogRepo.count();
    }
}