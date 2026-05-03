package com.yourapp.rentbot.bot;

import com.yourapp.rentbot.domain.FavoriteListing;
import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.RegionGroup;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.flow.FlowService;
import com.yourapp.rentbot.flow.FlowStep;
import com.yourapp.rentbot.i18n.Language;
import com.yourapp.rentbot.i18n.MessageService;
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
import com.yourapp.rentbot.service.dto.ParserRunStats;

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
    private final MessageService messageService;

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
            ListingCacheService listingCacheService,
            MessageService messageService
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
        this.messageService = messageService;
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
        Language lang = getUserLanguage(userId);

        if (text.equalsIgnoreCase("/admin")) {
            if (chatId != ADMIN_ID) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            long users = userFilterRepo.count();
            long active = userFilterRepo.countByActiveTrue();
            long inactive = users - active;

            long onboarded = userFilterRepo.countByOnboardedTrue();
            long notOnboarded = userFilterRepo.countByOnboardedFalse();

            long layoutChosen = userFilterRepo.countByLayoutIsNotNull();
            long priceChosen = userFilterRepo.countByMaxPriceIsNotNull();

            long layout1 = userFilterRepo.countByLayout("1");
            long layout2 = userFilterRepo.countByLayout("2");
            long layout3 = userFilterRepo.countByLayout("3");
            long layout4 = userFilterRepo.countByLayout("4");

            Double avgMaxPriceValue = userFilterRepo.findAverageMaxPrice();
            long avgMaxPrice = avgMaxPriceValue != null ? Math.round(avgMaxPriceValue) : 0;

            long cityStep = userFilterRepo.countByStep(FlowStep.CITY);
            long districtStep = userFilterRepo.countByStep(FlowStep.DISTRICT_GROUP);
            long layoutStep = userFilterRepo.countByStep(FlowStep.LAYOUT);
            long priceStep = userFilterRepo.countByStep(FlowStep.MAX_PRICE);
            long confirmStep = userFilterRepo.countByStep(FlowStep.CONFIRM);

            long favorites = favoriteService.countAll();
            long sent = notificationService.countSent();

            int cachedSearchUsers = searchCache.size();
            int cachedSearchResults = searchCache.values()
                    .stream()
                    .mapToInt(List::size)
                    .sum();

            int pagingUsers = searchOffset.size();
            int favoriteCacheSize = favoriteLinkCache.size();

            ParserRunStats runStats = parserService.getLastRunStats();


            java.time.Instant now = java.time.Instant.now();
            long updated24h = userFilterRepo.countByUpdatedAtAfter(now.minus(java.time.Duration.ofHours(24)));
            long updated7d = userFilterRepo.countByUpdatedAtAfter(now.minus(java.time.Duration.ofDays(7)));

            long onboardingConversion = users > 0 ? Math.round((onboarded * 100.0) / users) : 0;
            long activeConversion = users > 0 ? Math.round((active * 100.0) / users) : 0;

            String stats = """
📊 Статистика бота

👤 Усього користувачів: %d
✅ Активних підписок: %d
⛔ Неактивних: %d

🚀 Пройшли онбординг: %d (%d%%)
😴 Не пройшли онбординг: %d

🛏 Обрали тип квартири: %d
💰 Обрали max price: %d
💵 Середній max price: %d Kč

🏠 1 кімната: %d
🏠 2 кімнати: %d
🏠 3 кімнати: %d
🏠 4+ кімнати: %d

🧭 STEP CITY: %d
🧭 STEP DISTRICT_GROUP: %d
🧭 STEP LAYOUT: %d
🧭 STEP MAX_PRICE: %d
🧭 STEP CONFIRM: %d

⭐ Усього в обраному: %d
📩 Надіслано повідомлень: %d

🕒 Оновлювались за 24 год: %d
📆 Оновлювались за 7 днів: %d

📈 Конверсія в активну підписку: %d%%

🗂 Користувачів у searchCache: %d
📦 Оголошень у searchCache: %d
📄 Користувачів у paging: %d
🧷 favoriteLinkCache: %d

📡 Останній пошук користувача:
Sreality raw: %d
iDNES raw: %d
Bezrealitky raw: %d
Bazoš raw: %d

🔁 Після дедуплікації:
By link: %d
By signature: %d

🧪 Після фільтрів до diversify:
Всього: %d
Sreality: %d
iDNES: %d
Bezrealitky: %d
Bazoš: %d

🎯 У фінальній видачі:
Всього: %d
Sreality: %d
iDNES: %d
Bezrealitky: %d
Bazoš: %d
"""
                    .formatted(
                            users,
                            active,
                            inactive,
                            onboarded,
                            onboardingConversion,
                            notOnboarded,
                            layoutChosen,
                            priceChosen,
                            avgMaxPrice,
                            layout1,
                            layout2,
                            layout3,
                            layout4,
                            cityStep,
                            districtStep,
                            layoutStep,
                            priceStep,
                            confirmStep,
                            favorites,
                            sent,
                            updated24h,
                            updated7d,
                            activeConversion,
                            cachedSearchUsers,
                            cachedSearchResults,
                            pagingUsers,
                            favoriteCacheSize,

                            runStats.srealityRaw(),
                            runStats.idnesRaw(),
                            runStats.bezrealitkyRaw(),
                            runStats.bazosRaw(),
                            runStats.afterDedupeByLink(),
                            runStats.afterDedupeBySignature(),

                            runStats.filteredBaseTotal(),
                            runStats.filteredBaseSreality(),
                            runStats.filteredBaseIdnes(),
                            runStats.filteredBaseBezrealitky(),
                            runStats.filteredBaseBazos(),

                            runStats.finalFiltered(),
                            runStats.finalSreality(),
                            runStats.finalIdnes(),
                            runStats.finalBezrealitky(),
                            runStats.finalBazos()
                    );

            send(chatId, stats, Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (text.equalsIgnoreCase("/language")
                || text.equals("🌐 Мова / Language")
                || text.equals("🌐 Язык / Language")
                || text.equals("🌐 Jazyk / Language")
                || text.equals("🌐 Language")) {
            send(chatId, messageService.get(Language.UA, "language.choose"), Keyboards.languageKeyboard());
            return;
        }

        if (text.equals(msg(userId, "menu.new.search"))) {
            flowService.reset(userId);

            List<Region> regions = regionRepo.findAll();
            send(chatId, msg(userId, "search.new"), Keyboards.regionsKeyboard(regions));
            return;
        }

        if (text.equals(msg(userId, "menu.my.filter"))) {
            UserFilter f = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> flowService.getOrCreate(userId));
            send(chatId, flowService.pretty(f, lang), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (text.equals(msg(userId, "menu.favorites"))) {
            showFavorites(chatId, userId);
            return;
        }

        if (text.equals(msg(userId, "menu.stop.search"))) {
            UserFilter f = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> flowService.getOrCreate(userId));

            if (!f.isActive()) {
                send(chatId, msg(userId, "search.stopped.already"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            f.setActive(false);
            flowService.save(f);

            UserFilter fullFilter = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> f);

            send(chatId,
                    msg(userId, "search.stopped") + "\n\n" + flowService.pretty(fullFilter, lang),
                    Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (text.equals(msg(userId, "menu.share.bot"))
                || text.equals("🚗 Знайти авто")
                || text.equals("🚗 Найти авто")
                || text.equals("🚗 Najít auto")
                || text.equals("🚗 Find a car")) {
            send(chatId, "🚗 Знайди своє авто в Чехії!\n\n👉 @CarRadarCZ_bot", Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (text.equals(msg(userId, "menu.support.project"))) {
            send(chatId, msg(userId, "support.text"), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (text.equalsIgnoreCase("/menu")) {
            send(chatId, msg(userId, "menu.title"), Keyboards.mainMenuKeyboard(lang));
            return;
        }

        if (text.equalsIgnoreCase("/start")) {
            UserFilter f = flowService.getOrCreate(userId);

            send(chatId, msg(userId, "menu.pinned"), Keyboards.persistentNavKeyboard(lang));

            if (!f.isOnboarded()) {
                send(chatId, messageService.get(Language.UA, "language.choose"), Keyboards.languageKeyboard());
                return;
            }

            flowService.reset(userId);

            List<Region> regions = regionRepo.findAll();
            send(chatId, msg(userId, "city.choose"), Keyboards.regionsKeyboard(regions));
            return;
        }

        if (text.equalsIgnoreCase("/test")) {
            try {
                List<ListingDto> listings = parserService.findNewListings(userId);

                if (listings.isEmpty()) {
                    send(chatId, msg(userId, "search.test.empty"), Keyboards.persistentNavKeyboard(lang));
                    return;
                }

                send(chatId,
                        msg(userId, "search.found.prefix")
                                + listings.size()
                                + msg(userId, "search.found.middle")
                                + Math.min(PAGE_SIZE, listings.size())
                                + msg(userId, "search.found.suffix"),
                        Keyboards.persistentNavKeyboard(lang));

                startPagedSearch(chatId, userId, listings);

            } catch (Exception e) {
                e.printStackTrace();
                send(chatId,
                        msg(userId, "search.test.error.prefix") + e.getMessage(),
                        Keyboards.persistentNavKeyboard(lang));
            }
            return;
        }

        send(chatId, msg(userId, "unknown.command"), Keyboards.persistentNavKeyboard(lang));
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

        Language lang = getUserLanguage(userId);

        if (data.startsWith("LANG:")) {
            String langCode = data.substring("LANG:".length());

            Language language = Language.valueOf(langCode);
            f.setLanguage(language);
            flowService.save(f);

            if (!f.isOnboarded()) {
                f.setOnboarded(true);
                flowService.save(f);

                flowService.reset(userId);

                List<Region> regions = regionRepo.findAll();
                send(chatId, msg(userId, "filter.start"), Keyboards.regionsKeyboard(regions));
            } else {
                send(chatId,
                        msg(userId, "language.updated"),
                        Keyboards.persistentNavKeyboard(getUserLanguage(userId)));
            }
            return;
        }

        if (data.equals("ONBOARDING:START")) {
            f.setOnboarded(true);
            flowService.save(f);

            flowService.reset(userId);

            List<Region> regions = regionRepo.findAll();
            send(chatId, msg(userId, "filter.start"), Keyboards.regionsKeyboard(regions));
            return;
        }

        if (data.startsWith("FAV:ADD:")) {
            String tokenValue = data.substring("FAV:ADD:".length());
            ListingDto dto = listingCacheService.get(tokenValue);

            if (dto == null) {
                send(chatId, msg(userId, "favorites.add.failed"), Keyboards.mainMenuKeyboard(lang));
                return;
            }

            boolean added = favoriteService.addFavorite(userId, dto);

            if (added) {
                send(chatId, msg(userId, "favorites.added"), Keyboards.mainMenuKeyboard(lang));
            } else {
                send(chatId, msg(userId, "favorites.already.exists"), Keyboards.mainMenuKeyboard(lang));
            }
            return;
        }

        if (data.startsWith("FAV:REMOVE:")) {
            String raw = data.substring("FAV:REMOVE:".length());

            try {
                int key = Integer.parseInt(raw);
                String link = favoriteLinkCache.get(key);

                if (link == null) {
                    send(chatId, msg(userId, "favorites.remove.failed"), Keyboards.mainMenuKeyboard(lang));
                    return;
                }

                boolean removed = favoriteService.removeFavorite(userId, link);

                if (removed) {
                    send(chatId, msg(userId, "favorites.removed"), Keyboards.mainMenuKeyboard(lang));
                } else {
                    send(chatId, msg(userId, "favorites.already.removed"), Keyboards.mainMenuKeyboard(lang));
                }

            } catch (Exception e) {
                e.printStackTrace();
                send(chatId, msg(userId, "favorites.remove.error"), Keyboards.mainMenuKeyboard(lang));
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
                            send(chatId, msg(userId, "search.new.empty"), Keyboards.mainMenuKeyboard(lang));
                            return;
                        }

                        send(chatId,
                                msg(userId, "search.found.prefix")
                                        + listings.size()
                                        + msg(userId, "search.found.middle")
                                        + Math.min(PAGE_SIZE, listings.size())
                                        + msg(userId, "search.found.suffix"),
                                Keyboards.mainMenuKeyboard(lang));

                        startPagedSearch(chatId, userId, listings);

                    } catch (Exception e) {
                        e.printStackTrace();
                        send(chatId, msg(userId, "search.error"), Keyboards.mainMenuKeyboard(lang));
                    }
                }

                case "MORE" -> sendNextPage(chatId, userId);

                case "FILTER" -> {
                    UserFilter fullFilter = userFilterRepo.findFullById(userId)
                            .orElseGet(() -> f);
                    send(chatId, flowService.pretty(fullFilter, lang), Keyboards.mainMenuKeyboard(lang));
                }

                case "FAVORITES" -> showFavorites(chatId, userId);

                case "STOP" -> {
                    if (!f.isActive()) {
                        send(chatId, msg(userId, "search.stopped.already"), Keyboards.mainMenuKeyboard(lang));
                        return;
                    }

                    f.setActive(false);
                    flowService.save(f);

                    UserFilter fullFilter = userFilterRepo.findFullById(userId)
                            .orElseGet(() -> f);

                    send(chatId,
                            msg(userId, "search.stopped") + "\n\n" + flowService.pretty(fullFilter, lang),
                            Keyboards.mainMenuKeyboard(lang));
                }

                default -> send(chatId, msg(userId, "menu.unknown.action"), Keyboards.mainMenuKeyboard(lang));
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
                send(chatId, msg(userId, "district.choose"), Keyboards.regionGroupsKeyboard(groups));
            } else {
                f.setStep(FlowStep.LAYOUT);
                flowService.save(f);

                send(chatId, msg(userId, "layout.choose"), Keyboards.layoutKeyboard(lang));
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

            send(chatId, msg(userId, "layout.choose"), Keyboards.layoutKeyboard(lang));
            return;
        }

        if (data.startsWith("LAYOUT:")) {
            String layout = data.substring("LAYOUT:".length());

            f.setLayout(layout);
            f.setStep(FlowStep.MAX_PRICE);
            flowService.save(f);

            send(chatId, msg(userId, "price.choose"), Keyboards.priceKeyboard(lang));
            return;
        }

        if (data.startsWith("PRICE:")) {
            int price = Integer.parseInt(data.substring("PRICE:".length()));

            f.setMaxPrice(price);
            f.setStep(FlowStep.CONFIRM);
            flowService.save(f);

            send(chatId,
                    msg(userId, "subscribe.not.enabled") + "\n\n" + flowService.pretty(f, lang),
                    Keyboards.confirmKeyboard(lang));

            return;
        }

        if (data.startsWith("CONFIRM:SUBSCRIBE")) {
            f.setActive(true);
            flowService.save(f);

            UserFilter fullFilter = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> f);

            send(chatId,
                    msg(userId, "subscribe.enabled") + "\n\n" + flowService.pretty(fullFilter, lang),
                    Keyboards.mainMenuKeyboard(lang));

            try {
                List<ListingDto> listings = parserService.findNewListings(userId);

                for (ListingDto l : listings) {
                    notificationService.sendIfNotSent(f, l);
                }

            } catch (Exception e) {
                e.printStackTrace();
                send(chatId, msg(userId, "notify.fetch.failed"), Keyboards.mainMenuKeyboard(lang));
            }

            return;
        }

        if (data.startsWith("CONFIRM:STOP")) {
            f.setActive(false);
            flowService.save(f);

            send(chatId, msg(userId, "notifications.disabled"), Keyboards.mainMenuKeyboard(lang));
            return;
        }

        if (data.startsWith("CONFIRM:RESET")) {
            flowService.reset(userId);
            List<Region> regions = regionRepo.findAll();
            send(chatId, msg(userId, "filter.reset"), Keyboards.regionsKeyboard(regions));
            return;
        }

        if (data.startsWith("CONFIRM:SHOW")) {
            UserFilter fullFilter = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> f);
            send(chatId, flowService.pretty(fullFilter, lang), Keyboards.confirmKeyboard(lang));
            return;
        }

        send(chatId, msg(userId, "callback.unknown") + data, null);
    }

    private void startPagedSearch(long chatId, long userId, List<ListingDto> listings) throws TelegramApiException {
        searchCache.put(userId, listings);
        searchOffset.put(userId, 0);
        sendNextPage(chatId, userId);
    }

    private void sendNextPage(long chatId, long userId) throws TelegramApiException {
        List<ListingDto> listings = searchCache.get(userId);
        Language lang = getUserLanguage(userId);

        if (listings == null || listings.isEmpty()) {
            send(chatId, msg(userId, "search.results.saved.empty"), Keyboards.mainMenuKeyboard(lang));
            return;
        }

        int offset = searchOffset.getOrDefault(userId, 0);

        if (offset >= listings.size()) {
            send(chatId, msg(userId, "search.all.shown"), Keyboards.mainMenuKeyboard(lang));
            return;
        }

        int toIndex = Math.min(offset + PAGE_SIZE, listings.size());

        for (int i = offset; i < toIndex; i++) {
            sendListing(chatId, userId, listings.get(i));
        }

        searchOffset.put(userId, toIndex);

        if (toIndex < listings.size()) {
            telegramClient.execute(
                    SendMessage.builder()
                            .chatId(chatId)
                            .text(msg(userId, "search.more"))
                            .replyMarkup(Keyboards.moreKeyboard(lang))
                            .build()
            );
        } else {
            send(chatId, msg(userId, "search.all.shown"), Keyboards.mainMenuKeyboard(lang));
        }
    }

    private void showFavorites(long chatId, long userId) throws TelegramApiException {
        List<FavoriteListing> favorites = favoriteService.getFavorites(userId);
        Language lang = getUserLanguage(userId);

        if (favorites.isEmpty()) {
            send(chatId, msg(userId, "favorites.empty"), Keyboards.mainMenuKeyboard(lang));
            return;
        }

        send(chatId, msg(userId, "favorites.title"), Keyboards.mainMenuKeyboard(lang));

        for (FavoriteListing fav : favorites) {
            sendFavorite(chatId, userId, fav);
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

    private void sendListing(long chatId, long userId, ListingDto l) throws TelegramApiException {
        Language lang = getUserLanguage(userId);

        String caption =
                "🏠 " + nvl(l.title()) + "\n" +
                        "🏷 " + msg(userId, "listing.source") + ": " + nvl(l.source()) + "\n" +
                        "💰 " + (l.priceCzk() > 0 ? l.priceCzk() + " Kč" : "—") + "\n" +
                        "📍 " + msg(userId, "listing.location") + ": " + nvl(l.locality());

        String tokenValue = listingCacheService.put(l);
        String link = safeUrl(l.link());

        if (hasUsablePhotoUrl(l.photoUrl())) {
            try {
                telegramClient.execute(
                        SendPhoto.builder()
                                .chatId(chatId)
                                .photo(new InputFile(l.photoUrl()))
                                .caption(trimCaption(caption))
                                .replyMarkup(Keyboards.listingKeyboard(tokenValue, link, lang))
                                .build()
                );
                return;
            } catch (Exception e) {
                System.out.println("SendPhoto failed for listing link=" + l.link() + " photo=" + l.photoUrl());
                e.printStackTrace();
            }
        }

        telegramClient.execute(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(caption)
                        .replyMarkup(Keyboards.listingKeyboard(tokenValue, link, lang))
                        .build()
        );
    }

    private void sendFavorite(long chatId, long userId, FavoriteListing fav) throws TelegramApiException {
        Language lang = getUserLanguage(userId);

        String caption =
                "🏠 " + nvl(fav.getTitle()) + "\n" +
                        "🏷 " + msg(userId, "listing.source") + ": " + nvl(fav.getSource()) + "\n" +
                        "💰 " + (fav.getPriceCzk() != null && fav.getPriceCzk() > 0 ? fav.getPriceCzk() + " Kč" : "—") + "\n" +
                        "📍 " + msg(userId, "listing.location") + ": " + nvl(fav.getLocality());

        int key = fav.getLink().hashCode();
        favoriteLinkCache.put(key, fav.getLink());
        String link = safeUrl(fav.getLink());

        if (hasUsablePhotoUrl(fav.getPhotoUrl())) {
            try {
                telegramClient.execute(
                        SendPhoto.builder()
                                .chatId(chatId)
                                .photo(new InputFile(fav.getPhotoUrl()))
                                .caption(trimCaption(caption))
                                .replyMarkup(Keyboards.favoriteKeyboard(String.valueOf(key), link, lang))
                                .build()
                );
                return;
            } catch (Exception e) {
                System.out.println("SendPhoto failed for favorite link=" + fav.getLink() + " photo=" + fav.getPhotoUrl());
                e.printStackTrace();
            }
        }

        telegramClient.execute(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(caption)
                        .replyMarkup(Keyboards.favoriteKeyboard(String.valueOf(key), link, lang))
                        .build()
        );
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

    private String trimCaption(String text) {
        if (text == null) {
            return "";
        }

        return text.length() <= 1024 ? text : text.substring(0, 1020) + "...";
    }

    private Language getUserLanguage(long userId) {
        try {
            return userFilterRepo.findById(userId)
                    .map(UserFilter::getLanguage)
                    .orElse(Language.UA);
        } catch (Exception e) {
            return Language.UA;
        }
    }

    private String msg(long userId, String key) {
        return messageService.get(getUserLanguage(userId), key);
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private String safeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "https://t.me/zhytloCZ_bot";
        }
        return url;
    }
}