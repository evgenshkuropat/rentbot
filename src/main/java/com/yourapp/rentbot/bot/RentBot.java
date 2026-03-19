package com.yourapp.rentbot.bot;

import com.yourapp.rentbot.domain.FavoriteListing;
import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.RegionGroup;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.flow.FlowService;
import com.yourapp.rentbot.flow.FlowStep;
import com.yourapp.rentbot.repo.RegionGroupRepo;
import com.yourapp.rentbot.repo.RegionRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.FavoriteService;
import com.yourapp.rentbot.service.ListingCacheService;
import com.yourapp.rentbot.service.NotificationService;
import com.yourapp.rentbot.service.ParserService;
import com.yourapp.rentbot.service.dto.ListingDto;
import com.yourapp.rentbot.ui.Keyboards;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RentBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final FlowService flowService;
    private final RegionRepo regionRepo;
    private final RegionGroupRepo regionGroupRepo;
    private final UserFilterRepo userFilterRepo;
    private final ParserService parserService;
    private final NotificationService notificationService;
    private final FavoriteService favoriteService;
    private final ListingCacheService listingCacheService;

    private final String token;

    private static final long ADMIN_ID = 1246486851L;

    private final Map<Integer, String> favoriteLinkCache = new HashMap<>();
    private final Map<Long, List<ListingDto>> searchCache = new HashMap<>();
    private final Map<Long, Integer> searchOffset = new HashMap<>();
    private static final int PAGE_SIZE = 10;

    public RentBot(
            @Value("${telegram.bot.token}") String token,
            FlowService flowService,
            RegionRepo regionRepo,
            RegionGroupRepo regionGroupRepo,
            UserFilterRepo userFilterRepo,
            ParserService parserService,
            NotificationService notificationService,
            FavoriteService favoriteService,
            ListingCacheService listingCacheService
    ) {
        this.token = token;
        this.flowService = flowService;
        this.regionRepo = regionRepo;
        this.regionGroupRepo = regionGroupRepo;
        this.userFilterRepo = userFilterRepo;
        this.parserService = parserService;
        this.notificationService = notificationService;
        this.favoriteService = favoriteService;
        this.listingCacheService = listingCacheService;
        this.telegramClient = new OkHttpTelegramClient(token);
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                onText(update);
            } else if (update.hasCallbackQuery()) {
                onCallback(update);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onText(Update update) throws TelegramApiException {
        long chatId = update.getMessage().getChatId();
        long userId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText().trim();


        // === ADMIN ===
        if (text.equalsIgnoreCase("/admin")) {
            if (chatId != ADMIN_ID) {
                send(chatId, "⛔ У тебе немає доступу", Keyboards.persistentNavKeyboard());
                return;
            }

            long users = userFilterRepo.count();
            long active = userFilterRepo.countByActiveTrue();
            long favorites = favoriteService.countAll();
            long sent = notificationService.countSent();

            String stats = """
📊 Статистика бота

👤 Користувачів: %d
✅ Активних підписок: %d
⭐ В обраному: %d
📩 Надіслано повідомлень: %d
"""
                    .formatted(users, active, favorites, sent);

            send(chatId, stats, Keyboards.persistentNavKeyboard());
            return;
        }

        if (text.equals("🔄 Новий пошук")) {
            flowService.reset(userId);

            List<Region> regions = regionRepo.findAll();
            send(chatId,
                    "Починаємо новий пошук 🔍\nОбери місто:",
                    Keyboards.regionsKeyboard(regions));
            return;
        }

        if (text.equals("📋 Мій фільтр")) {
            UserFilter f = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> flowService.getOrCreate(userId));
            send(chatId, flowService.pretty(f), Keyboards.persistentNavKeyboard());
            return;
        }

        if (text.equals("⭐ Обране")) {
            showFavorites(chatId, userId);
            return;
        }

        if (text.equals("⛔ Зупинити пошук")) {
            UserFilter f = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> flowService.getOrCreate(userId));

            if (!f.isActive()) {
                send(chatId, "Пошук вже зупинено 🙂", Keyboards.persistentNavKeyboard());
                return;
            }

            f.setActive(false);
            flowService.save(f);

            UserFilter fullFilter = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> f);

            send(chatId,
                    "⛔ Пошук зупинено\n\n" + flowService.pretty(fullFilter),
                    Keyboards.persistentNavKeyboard());
            return;
        }

        if (text.equals("📤 Поширити бота")) {
            send(chatId,
                    "Поділитися ботом можна за цим посиланням:\n" +
                            "https://t.me/share/url?url=https://t.me/zhytloCZ_bot&text=Знайди житло в Чехії 🇨🇿",
                    Keyboards.persistentNavKeyboard());
            return;
        }

        if (text.equals("💙 Підтримати проєкт")) {
            send(chatId,
                    "Підтримати розвиток проєкту можна тут 💙\n" +
                            "https://revolut.me/evzen13",
                    Keyboards.persistentNavKeyboard());
            return;
        }

        if (text.equalsIgnoreCase("/menu")) {
            send(chatId, "Головне меню:", Keyboards.mainMenuKeyboard());
            return;
        }

        if (text.equalsIgnoreCase("/start")) {
            UserFilter f = flowService.getOrCreate(userId);

            send(chatId, "Меню закріплено внизу 👇", Keyboards.persistentNavKeyboard());

            if (!f.isOnboarded()) {
                String welcome = """
🏠 Вітаю у боті пошуку житла в Чехії 🇨🇿

Я допомагаю знаходити нові оголошення про оренду квартир швидше за інших.

🔎 Джерела:
• Sreality
• Bezrealitky
• iDNES
• Bazoš

📢 Нові варіанти приходять одразу після появи — ти перший їх бачиш.

💡 Є як пропозиції від власників, так і від агентств.
""";

                send(chatId, welcome, Keyboards.onboardingKeyboard());
                return;
            }

            flowService.reset(userId);

            List<Region> regions = regionRepo.findAll();
            send(chatId,
                    "Обери місто 👇",
                    Keyboards.regionsKeyboard(regions));
            return;
        }

        if (text.equalsIgnoreCase("/test")) {
            try {
                List<ListingDto> listings = parserService.findNewListings(userId);

                if (listings.isEmpty()) {
                    send(chatId, "Нічого не знайшов 😕", Keyboards.persistentNavKeyboard());
                    return;
                }

                send(chatId,
                        "Знайшов " + listings.size() + " оголошень. Показую перші "
                                + Math.min(PAGE_SIZE, listings.size()) + " 👇",
                        Keyboards.persistentNavKeyboard());

                startPagedSearch(chatId, userId, listings);

            } catch (Exception e) {
                e.printStackTrace();
                send(chatId, "Помилка тесту: " + e.getMessage(), Keyboards.persistentNavKeyboard());
            }
            return;
        }

        send(chatId,
                "Користуйся кнопками 🙂\nНатисни /start щоб почати.",
                Keyboards.persistentNavKeyboard());
    }

    private void onCallback(Update update) throws TelegramApiException {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        long userId = update.getCallbackQuery().getFrom().getId();
        String data = update.getCallbackQuery().getData();
        String callbackId = update.getCallbackQuery().getId();

        answerCallback(callbackId);
        disableInlineKeyboard(update);

        UserFilter f = userFilterRepo.findFullById(userId)
                .orElseGet(() -> flowService.getOrCreate(userId));

        if (data.equals("ONBOARDING:START")) {
            f.setOnboarded(true);
            flowService.save(f);

            flowService.reset(userId);

            List<Region> regions = regionRepo.findAll();
            send(chatId,
                    "Супер, почнемо 🔍\nОбери місто:",
                    Keyboards.regionsKeyboard(regions));
            return;
        }

        if (data.startsWith("FAV:ADD:")) {
            String tokenValue = data.substring("FAV:ADD:".length());
            ListingDto dto = listingCacheService.get(tokenValue);

            if (dto == null) {
                send(chatId, "Не вдалося додати в обране 😕", Keyboards.mainMenuKeyboard());
                return;
            }

            boolean added = favoriteService.addFavorite(userId, dto);

            if (added) {
                send(chatId, "⭐ Додано в обране", Keyboards.mainMenuKeyboard());
            } else {
                send(chatId, "Це оголошення вже є в обраному 🙂", Keyboards.mainMenuKeyboard());
            }
            return;
        }

        if (data.startsWith("FAV:REMOVE:")) {
            String raw = data.substring("FAV:REMOVE:".length());

            try {
                int key = Integer.parseInt(raw);
                String link = favoriteLinkCache.get(key);

                if (link == null) {
                    send(chatId, "Не вдалося знайти оголошення для видалення 😕", Keyboards.mainMenuKeyboard());
                    return;
                }

                boolean removed = favoriteService.removeFavorite(userId, link);

                if (removed) {
                    send(chatId, "❌ Видалено з обраного", Keyboards.mainMenuKeyboard());
                } else {
                    send(chatId, "Оголошення вже відсутнє в обраному 🙂", Keyboards.mainMenuKeyboard());
                }

            } catch (Exception e) {
                e.printStackTrace();
                send(chatId, "Помилка при видаленні з обраного 😕", Keyboards.mainMenuKeyboard());
            }
            return;
        }

        if (data.startsWith("MENU:")) {
            String action = data.substring("MENU:".length());

            switch (action) {
                case "NEW" -> {
                    try {
                        List<ListingDto> listings = parserService.findNewListings(userId);

                        if (listings.isEmpty()) {
                            send(chatId, "Нічого нового не знайшов 😕", Keyboards.mainMenuKeyboard());
                            return;
                        }

                        send(chatId,
                                "Знайшов " + listings.size() + " оголошень. Показую перші "
                                        + Math.min(PAGE_SIZE, listings.size()) + " 👇",
                                Keyboards.mainMenuKeyboard());

                        startPagedSearch(chatId, userId, listings);

                    } catch (Exception e) {
                        e.printStackTrace();
                        send(chatId, "Помилка при пошуку квартир 😕", Keyboards.mainMenuKeyboard());
                    }
                }

                case "MORE" -> sendNextPage(chatId, userId);

                case "FILTER" -> {
                    UserFilter fullFilter = userFilterRepo.findFullById(userId)
                            .orElseGet(() -> f);
                    send(chatId, flowService.pretty(fullFilter), Keyboards.mainMenuKeyboard());
                }

                case "FAVORITES" -> showFavorites(chatId, userId);

                case "STOP" -> {
                    if (!f.isActive()) {
                        send(chatId, "Пошук вже зупинено 🙂", Keyboards.mainMenuKeyboard());
                        return;
                    }

                    f.setActive(false);
                    flowService.save(f);

                    UserFilter fullFilter = userFilterRepo.findFullById(userId)
                            .orElseGet(() -> f);

                    send(chatId,
                            "⛔ Пошук зупинено\n\n" + flowService.pretty(fullFilter),
                            Keyboards.mainMenuKeyboard());
                }

                default -> send(chatId, "Невідома дія меню 😅", Keyboards.mainMenuKeyboard());
            }

            return;
        }

        if (data.startsWith("REGION:")) {
            String code = data.substring("REGION:".length());

            Region region = regionRepo.findByCode(code)
                    .orElseThrow(() -> new IllegalArgumentException("Region not found by code=" + code));

            f.setRegion(region);
            f.setRegionGroup(null);
            f.setLayout(null);
            f.setMaxPrice(null);
            f.setActive(false);

            if (region.isHasDistricts()) {
                f.setStep(FlowStep.DISTRICT_GROUP);
                flowService.save(f);

                List<RegionGroup> groups = regionGroupRepo.findByRegionId(region.getId());
                send(chatId, "Обери район:", Keyboards.regionGroupsKeyboard(groups));
            } else {
                f.setStep(FlowStep.LAYOUT);
                flowService.save(f);

                send(chatId, "Обери тип квартири:", Keyboards.layoutKeyboard());
            }
            return;
        }

        if (data.startsWith("GROUP:")) {
            String groupCode = data.substring("GROUP:".length());

            RegionGroup group = regionGroupRepo.findByCode(groupCode)
                    .orElseThrow(() -> new IllegalArgumentException("RegionGroup not found by code=" + groupCode));

            f.setRegionGroup(group);
            f.setStep(FlowStep.LAYOUT);
            flowService.save(f);

            send(chatId, "Обери тип квартири:", Keyboards.layoutKeyboard());
            return;
        }

        if (data.startsWith("LAYOUT:")) {
            String layout = data.substring("LAYOUT:".length());

            f.setLayout(layout);
            f.setStep(FlowStep.MAX_PRICE);
            flowService.save(f);

            send(chatId, "Обери максимальну ціну:", Keyboards.priceKeyboard());
            return;
        }

        if (data.startsWith("PRICE:")) {
            int price = Integer.parseInt(data.substring("PRICE:".length()));

            f.setMaxPrice(price);
            f.setStep(FlowStep.CONFIRM);
            flowService.save(f);

            send(chatId,
                    "Готово ✅\n" + flowService.pretty(f),
                    Keyboards.confirmKeyboard());

            return;
        }

        if (data.startsWith("CONFIRM:SUBSCRIBE")) {
            f.setActive(true);
            flowService.save(f);

            UserFilter fullFilter = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> f);

            send(chatId,
                    "🔔 Сповіщення увімкнено!\n\n" + flowService.pretty(fullFilter),
                    Keyboards.mainMenuKeyboard());

            try {
                List<ListingDto> listings = parserService.findNewListings(userId);

                for (ListingDto l : listings) {
                    notificationService.sendIfNotSent(f, l);
                }

            } catch (Exception e) {
                e.printStackTrace();
                send(chatId,
                        "⚠️ Не вдалося зараз отримати оголошення, але підписка вже увімкнена.",
                        Keyboards.mainMenuKeyboard());
            }

            return;
        }

        if (data.startsWith("CONFIRM:STOP")) {
            f.setActive(false);
            flowService.save(f);

            send(chatId, "⛔ Сповіщення вимкнено", Keyboards.mainMenuKeyboard());
            return;
        }

        if (data.startsWith("CONFIRM:RESET")) {
            flowService.reset(userId);
            List<Region> regions = regionRepo.findAll();
            send(chatId, "Ок, давай заново. Обери місто:", Keyboards.regionsKeyboard(regions));
            return;
        }

        if (data.startsWith("CONFIRM:SHOW")) {
            UserFilter fullFilter = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> f);
            send(chatId, flowService.pretty(fullFilter), Keyboards.confirmKeyboard());
            return;
        }

        send(chatId, "Невідомий callback: " + data, null);
    }

    private void startPagedSearch(long chatId, long userId, List<ListingDto> listings) throws TelegramApiException {
        searchCache.put(userId, listings);
        searchOffset.put(userId, 0);
        sendNextPage(chatId, userId);
    }

    private void sendNextPage(long chatId, long userId) throws TelegramApiException {
        List<ListingDto> listings = searchCache.get(userId);

        if (listings == null || listings.isEmpty()) {
            send(chatId, "Немає збережених результатів пошуку 😕", Keyboards.mainMenuKeyboard());
            return;
        }

        int offset = searchOffset.getOrDefault(userId, 0);

        if (offset >= listings.size()) {
            send(chatId, "Це всі оголошення ✅", Keyboards.mainMenuKeyboard());
            return;
        }

        int toIndex = Math.min(offset + PAGE_SIZE, listings.size());

        for (int i = offset; i < toIndex; i++) {
            sendListing(chatId, listings.get(i));
        }

        searchOffset.put(userId, toIndex);

        if (toIndex < listings.size()) {
            telegramClient.execute(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text("Показати ще?")
                            .replyMarkup(Keyboards.moreKeyboard())
                            .build()
            );
        } else {
            send(chatId, "Це всі оголошення ✅", Keyboards.mainMenuKeyboard());
        }
    }

    private void showFavorites(long chatId, long userId) throws TelegramApiException {
        List<FavoriteListing> favorites = favoriteService.getFavorites(userId);

        if (favorites.isEmpty()) {
            send(chatId, "У тебе ще немає збережених оголошень ⭐", Keyboards.mainMenuKeyboard());
            return;
        }

        send(chatId, "⭐ Твоє обране:", Keyboards.mainMenuKeyboard());

        for (FavoriteListing fav : favorites) {
            sendFavorite(chatId, fav);
        }
    }

    private void disableInlineKeyboard(Update update) {
        try {
            var msg = update.getCallbackQuery().getMessage();

            telegramClient.execute(
                    EditMessageReplyMarkup.builder()
                            .chatId(msg.getChatId())
                            .messageId(msg.getMessageId())
                            .replyMarkup(null)
                            .build()
            );

        } catch (Exception ignored) {
        }
    }

    private void answerCallback(String callbackQueryId) throws TelegramApiException {
        telegramClient.execute(
                AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackQueryId)
                        .build()
        );
    }

    private void send(long chatId, String text, ReplyKeyboard keyboard) throws TelegramApiException {
        SendMessage.SendMessageBuilder b = SendMessage.builder()
                .chatId(chatId)
                .text(text);

        if (keyboard != null) {
            b.replyMarkup(keyboard);
        }

        telegramClient.execute(b.build());
    }

    private void sendListing(long chatId, ListingDto l) throws TelegramApiException {
        String caption =
                "🏠 " + nvl(l.title()) + "\n" +
                        "🏷 Джерело: " + nvl(l.source()) + "\n" +
                        "💰 " + (l.priceCzk() > 0 ? l.priceCzk() + " Kč" : "—") + "\n" +
                        "📍 " + nvl(l.locality()) + "\n" +
                        "🔗 " + nvl(l.link());

        String tokenValue = listingCacheService.put(l);

        try {
            if (l.photoUrl() != null && !l.photoUrl().isBlank()) {
                telegramClient.execute(
                        SendPhoto.builder()
                                .chatId(chatId)
                                .photo(new InputFile(l.photoUrl()))
                                .caption(caption)
                                .replyMarkup(Keyboards.addToFavoritesKeyboard(tokenValue))
                                .build()
                );
                return;
            }

        } catch (Exception ignored) {
        }

        telegramClient.execute(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(caption)
                        .replyMarkup(Keyboards.addToFavoritesKeyboard(tokenValue))
                        .build()
        );
    }

    private void sendFavorite(long chatId, FavoriteListing fav) throws TelegramApiException {
        String caption =
                "🏠 " + nvl(fav.getTitle()) + "\n" +
                        "🏷 Джерело: " + nvl(fav.getSource()) + "\n" +
                        "💰 " + (fav.getPriceCzk() != null && fav.getPriceCzk() > 0 ? fav.getPriceCzk() + " Kč" : "—") + "\n" +
                        "📍 " + nvl(fav.getLocality()) + "\n" +
                        "🔗 " + nvl(fav.getLink());

        int key = fav.getLink().hashCode();
        favoriteLinkCache.put(key, fav.getLink());

        try {
            if (fav.getPhotoUrl() != null && !fav.getPhotoUrl().isBlank()) {
                telegramClient.execute(
                        SendPhoto.builder()
                                .chatId(chatId)
                                .photo(new InputFile(fav.getPhotoUrl()))
                                .caption(caption)
                                .replyMarkup(Keyboards.removeFromFavoritesKeyboard(String.valueOf(key)))
                                .build()
                );
                return;
            }

        } catch (Exception ignored) {
        }

        telegramClient.execute(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(caption)
                        .replyMarkup(Keyboards.removeFromFavoritesKeyboard(String.valueOf(key)))
                        .build()
        );
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
}