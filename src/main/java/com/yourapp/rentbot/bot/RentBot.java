package com.yourapp.rentbot.bot;

import com.yourapp.rentbot.domain.FavoriteListing;
import com.yourapp.rentbot.domain.OwnerListing;
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
import com.yourapp.rentbot.service.OwnerListingService;
import com.yourapp.rentbot.service.ParserService;
import com.yourapp.rentbot.service.dto.ListingDto;
import com.yourapp.rentbot.ui.Keyboards;
import org.springframework.data.domain.PageRequest;
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
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import com.yourapp.rentbot.service.dto.ParserRunStats;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class RentBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final FlowService flowService;
    private final RegionRepo regionRepo;
    private final RegionGroupRepo regionGroupRepo;
    private final UserFilterRepo userFilterRepo;
    private final ParserService parserService;
    private final NotificationService notificationService;
    private final OwnerListingService ownerListingService;
    private final FavoriteService favoriteService;
    private final ListingCacheService listingCacheService;
    private final MessageService messageService;

    private final String token;

    private static final long ADMIN_ID = 1246486851L;
    private static final long INTERACTION_CACHE_TTL_MILLIS = 6 * 60 * 60 * 1000L;

    private final Map<Integer, String> favoriteLinkCache = new HashMap<>();
    private final Map<Integer, Long> favoriteLinkCacheAt = new HashMap<>();
    private final Map<Long, List<ListingDto>> searchCache = new HashMap<>();
    private final Map<Long, Long> searchCacheAt = new HashMap<>();
    private final Map<Long, Integer> searchOffset = new HashMap<>();
    private final Map<Long, Integer> searchCurrentIndex = new HashMap<>();
    private final Map<Long, String> filterEditMode = new HashMap<>();
    private final Map<Long, OwnerListingDraft> ownerListingDrafts = new HashMap<>();
    private static final int PAGE_SIZE = 10;
    private static final String EDIT_CITY = "CITY";
    private static final String EDIT_DISTRICT = "DISTRICT";
    private static final String EDIT_LAYOUT = "LAYOUT";

    public RentBot(
            @Value("${telegram.bot.token}") String token,
            FlowService flowService,
            RegionRepo regionRepo,
            RegionGroupRepo regionGroupRepo,
            UserFilterRepo userFilterRepo,
            ParserService parserService,
            NotificationService notificationService,
            OwnerListingService ownerListingService,
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
        this.ownerListingService = ownerListingService;
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
            } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
                onPhoto(update);
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

        if (chatId == ADMIN_ID && text.equalsIgnoreCase("/add_owner_listing")) {
            OwnerListingDraft draft = new OwnerListingDraft();
            ownerListingDrafts.put(userId, draft);
            send(chatId,
                    "Додаємо житло від власника.\n\n1/8 Напиши місто або округ, наприклад: Praha, Brno, Kolín, Plzeň.\n\nСкасувати: /cancel",
                    Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (chatId == ADMIN_ID && text.equalsIgnoreCase("/cancel") && ownerListingDrafts.containsKey(userId)) {
            ownerListingDrafts.remove(userId);
            send(chatId, "Додавання оголошення скасовано.", Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (chatId == ADMIN_ID && ownerListingDrafts.containsKey(userId)) {
            handleOwnerListingText(chatId, userId, text, lang);
            return;
        }

        if (text.equalsIgnoreCase("/admin")) {
            cleanupExpiredInteractionCaches();

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

            long layoutRoom = userFilterRepo.countByLayout("ROOM");
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
            long confirmActiveStep = userFilterRepo.countByStepAndActiveTrue(FlowStep.CONFIRM);
            long confirmStep = userFilterRepo.countByStep(FlowStep.CONFIRM) - confirmActiveStep;
            long doneStep = confirmActiveStep;

            long favorites = favoriteService.countAll();
            long sent = notificationService.countSent();

            int cachedSearchUsers = searchCache.size();
            int cachedSearchResults = searchCache.values()
                    .stream()
                    .mapToInt(List::size)
                    .sum();

            int pagingUsers = searchCurrentIndex.size();
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

🚪 Кімната: %d
🏠 1 кімната: %d
🏠 2 кімнати: %d
🏠 3 кімнати: %d
🏠 4+ кімнати: %d

🧭 STEP CITY: %d
🧭 STEP DISTRICT_GROUP: %d
🧭 STEP LAYOUT: %d
🧭 STEP MAX_PRICE: %d
🧭 STEP CONFIRM: %d
🧭 STEP DONE: %d

⭐ Усього в обраному: %d
📩 Надіслано за останні 14 днів: %d

🕒 Оновлювались за 24 год: %d
📆 Оновлювались за 7 днів: %d

📈 Конверсія в активну підписку: %d%%

🗂 Користувачів у searchCache: %d
📦 Оголошень у searchCache: %d
📄 Користувачів у paging: %d
🧷 favoriteLinkCache: %d

📡 Останній запуск парсерів:
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
                            layoutRoom,
                            layout1,
                            layout2,
                            layout3,
                            layout4,
                            cityStep,
                            districtStep,
                            layoutStep,
                            priceStep,
                            confirmStep,
                            doneStep,
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

        if (text.toLowerCase().startsWith("/admin_reactivate")) {
            if (chatId != ADMIN_ID) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            int limit = parseAdminLimit(text, 50, 100);
            ReactivationResult result = sendReactivationMessages(limit);

            send(chatId,
                    """
                    🔄 Reactivation finished

                    Candidates checked: %d
                    Sent: %d
                    Skipped: %d
                    Deactivated: %d
                    Failed: %d
                    """
                            .formatted(
                                    result.checked,
                                    result.sent,
                                    result.skipped,
                                    result.deactivated,
                                    result.failed
                            ),
                    Keyboards.persistentNavKeyboard(lang));
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

        if (text.equals("🤝 Інші сервіси")
                || text.equals("🤝 Другие сервисы")
                || text.equals("🤝 Další služby")
                || text.equals("🤝 Other services")
                || text.equals("📦 Інші сервіси")
                || text.equals("📦 Другие сервисы")
                || text.equals("📦 Další služby")
                || text.equals("📦 Other services")) {

            send(chatId,
                    switch (lang) {
                        case RU -> "Другие полезные сервисы:";
                        case CZ -> "Další užitečné služby:";
                        case EN -> "Other useful services:";
                        default -> "Інші корисні сервіси:";
                    },
                    Keyboards.servicesInlineKeyboard(lang));

            return;
        }

        if (text.equals(msg(userId, "menu.new.search"))) {
            filterEditMode.remove(userId);
            flowService.reset(userId);

            sendRegionsEntry(chatId, userId, msg(userId, "search.new"));
            return;
        }

        if (text.equals(msg(userId, "menu.my.filter"))) {
            UserFilter f = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> flowService.getOrCreate(userId));
            send(chatId, flowService.pretty(f, lang), Keyboards.filterActionsKeyboard(lang));
            return;
        }

        if (text.equals(msg(userId, "menu.favorites"))) {
            showFavorites(chatId, userId);
            return;
        }

        if (text.equals(msg(userId, "menu.stop.search"))) {
            filterEditMode.remove(userId);
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

        if (text.equals(msg(userId, "menu.share.bot"))) {
            send(chatId, msg(userId, "share.text"), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (text.equals("🚗 Знайти авто")
                || text.equals("🚗 Найти авто")
                || text.equals("🚗 Najít auto")
                || text.equals("🚗 Find a car")) {
            send(chatId, "🚗 Знайди своє авто в Чехії!\n\n👉 @CarRadarCZ_bot", Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (text.equals("💎 Преміум")
                || text.equals("💎 Премиум")
                || text.equals("💎 Premium")) {
            send(chatId, premiumInfo(lang), Keyboards.authorContactKeyboard(lang));
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

            sendRegionsEntry(chatId, userId, msg(userId, "city.choose"));
            return;
        }

        if (text.equals("🔍 Нові квартири")
                || text.equals("🔍 Новые квартиры")
                || text.equals("🔍 Nové byty")
                || text.equals("🔍 New listings")) {
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
                                + 1
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
                                + 1
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

        if (text.equals("🚗 Пошук авто")
                || text.equals("🚗 Поиск авто")
                || text.equals("🚗 Hledání auta")
                || text.equals("🚗 Car search")) {

            send(chatId,
                    "👉 https://t.me/CarRadarCZ_bot",
                    Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (text.equals("⬅️ Назад")
                || text.equals("⬅️ Zpět")
                || text.equals("⬅️ Back")) {

            send(chatId,
                    msg(userId, "menu.title"),
                    Keyboards.persistentNavKeyboard(lang));
            return;
        }

        send(chatId, msg(userId, "unknown.command"), Keyboards.persistentNavKeyboard(lang));
    }

    private void onPhoto(Update update) throws TelegramApiException {
        long chatId = update.getMessage().getChatId();
        long userId = update.getMessage().getFrom().getId();
        Language lang = getUserLanguage(userId);

        if (chatId != ADMIN_ID) {
            return;
        }

        OwnerListingDraft draft = ownerListingDrafts.get(userId);
        if (draft == null) {
            return;
        }

        if (draft.step != OwnerListingDraft.Step.PHOTO) {
            send(chatId, "Фото збережу на останньому кроці. Зараз очікую: " + draft.stepLabel(), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        List<PhotoSize> photos = update.getMessage().getPhoto();
        if (photos == null || photos.isEmpty()) {
            send(chatId, "Не бачу фото. Надішли фото або напиши - якщо фото немає.", Keyboards.persistentNavKeyboard(lang));
            return;
        }

        draft.photoFileId = photos.get(photos.size() - 1).getFileId();
        draft.step = OwnerListingDraft.Step.CONFIRM;
        sendOwnerListingPreview(chatId, draft);
    }

    private void handleOwnerListingText(long chatId, long userId, String text, Language lang) throws TelegramApiException {
        OwnerListingDraft draft = ownerListingDrafts.get(userId);
        if (draft == null) {
            return;
        }

        switch (draft.step) {
            case CITY -> {
                Optional<Region> region = findRegionByInput(text);
                if (region.isEmpty()) {
                    send(chatId,
                            "Не знайшов таке місто/округ у базі. Напиши як у боті, наприклад: Praha, Brno, Kolín, Plzeň.",
                            Keyboards.persistentNavKeyboard(lang));
                    return;
                }
                draft.region = region.get();
                draft.step = OwnerListingDraft.Step.LOCALITY;
                send(chatId, "2/8 Локація або адреса. Наприклад: Kolín - Kolín II, Masarykova.", Keyboards.persistentNavKeyboard(lang));
            }
            case LOCALITY -> {
                draft.locality = cleanRequired(text);
                if (draft.locality == null) {
                    send(chatId, "Локація не може бути пустою. Напиши район, місто або адресу.", Keyboards.persistentNavKeyboard(lang));
                    return;
                }
                draft.step = OwnerListingDraft.Step.LAYOUT;
                send(chatId, "3/8 Тип житла: room, 1, 2, 3 або 4.", Keyboards.persistentNavKeyboard(lang));
            }
            case LAYOUT -> {
                String layout = normalizeOwnerLayout(text);
                if (layout == null) {
                    send(chatId, "Не зрозумів тип. Напиши: room, 1, 2, 3 або 4.", Keyboards.persistentNavKeyboard(lang));
                    return;
                }
                draft.layout = layout;
                draft.step = OwnerListingDraft.Step.PRICE;
                send(chatId, "4/8 Ціна в Kč. Наприклад: 12990.", Keyboards.persistentNavKeyboard(lang));
            }
            case PRICE -> {
                Integer price = parseOwnerPrice(text);
                if (price == null) {
                    send(chatId, "Ціна має бути числом. Наприклад: 12990.", Keyboards.persistentNavKeyboard(lang));
                    return;
                }
                draft.priceCzk = price;
                draft.step = OwnerListingDraft.Step.TITLE;
                send(chatId, "5/8 Назва оголошення. Наприклад: Pronájem bytu 2+kk 39 m² Masarykova, Kolín.", Keyboards.persistentNavKeyboard(lang));
            }
            case TITLE -> {
                draft.title = cleanRequired(text);
                if (draft.title == null) {
                    send(chatId, "Назва не може бути пустою.", Keyboards.persistentNavKeyboard(lang));
                    return;
                }
                draft.step = OwnerListingDraft.Step.DESCRIPTION;
                send(chatId, "6/8 Опис. Можна коротко: меблі, депозит, доступність. Якщо опису немає, напиши -", Keyboards.persistentNavKeyboard(lang));
            }
            case DESCRIPTION -> {
                draft.description = "-".equals(text.trim()) ? null : text.trim();
                draft.step = OwnerListingDraft.Step.CONTACT;
                send(chatId, "7/8 Контакт власника: телефон, Telegram або інший спосіб зв'язку.", Keyboards.persistentNavKeyboard(lang));
            }
            case CONTACT -> {
                draft.contact = cleanRequired(text);
                if (draft.contact == null) {
                    send(chatId, "Контакт не може бути пустим.", Keyboards.persistentNavKeyboard(lang));
                    return;
                }
                draft.step = OwnerListingDraft.Step.PHOTO;
                send(chatId, "8/8 Надішли фото квартири. Якщо фото немає, напиши -", Keyboards.persistentNavKeyboard(lang));
            }
            case PHOTO -> {
                if (!"-".equals(text.trim())) {
                    send(chatId, "На цьому кроці надішли саме фото або напиши - якщо фото немає.", Keyboards.persistentNavKeyboard(lang));
                    return;
                }
                draft.photoFileId = null;
                draft.step = OwnerListingDraft.Step.CONFIRM;
                sendOwnerListingPreview(chatId, draft);
            }
            case CONFIRM -> sendOwnerListingPreview(chatId, draft);
        }
    }

    private Optional<Region> findRegionByInput(String input) {
        String normalized = normalizeSearch(input);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        return regionRepo.findAll().stream()
                .filter(region -> normalizeSearch(region.getTitle()).equals(normalized)
                        || normalizeSearch(region.getCode()).equals(normalized))
                .findFirst()
                .or(() -> regionRepo.findAll().stream()
                        .filter(region -> normalizeSearch(region.getTitle()).contains(normalized)
                                || normalized.contains(normalizeSearch(region.getTitle())))
                        .findFirst());
    }

    private String normalizeSearch(String value) {
        if (value == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase()
                .replaceAll("[^a-z0-9]+", "");
    }

    private String normalizeOwnerLayout(String text) {
        if (text == null) {
            return null;
        }
        String normalized = normalizeSearch(text);
        if (normalized.equals("room")
                || normalized.contains("kimnata")
                || normalized.contains("komnata")
                || normalized.contains("pokoj")) {
            return "ROOM";
        }
        if (normalized.startsWith("1")) {
            return "1";
        }
        if (normalized.startsWith("2")) {
            return "2";
        }
        if (normalized.startsWith("3")) {
            return "3";
        }
        if (normalized.startsWith("4")) {
            return "4";
        }
        return null;
    }

    private Integer parseOwnerPrice(String text) {
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            int price = Integer.parseInt(digits);
            return price > 0 ? price : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String cleanRequired(String text) {
        if (text == null || text.isBlank() || "-".equals(text.trim())) {
            return null;
        }
        return text.trim();
    }

    private void sendOwnerListingPreview(long chatId, OwnerListingDraft draft) throws TelegramApiException {
        String preview = """
                🏠 Оголошення від власника

                Місто/округ: %s
                Локація: %s
                Тип: %s
                Ціна: %s
                Назва: %s
                Опис: %s
                Контакт: %s
                Фото: %s

                Опублікувати це оголошення?
                """.formatted(
                draft.region == null ? "—" : draft.region.getTitle(),
                nvl(draft.locality),
                nvl(draft.layout),
                draft.priceCzk == null ? "—" : formatPrice(draft.priceCzk),
                nvl(draft.title),
                nvl(draft.description),
                nvl(draft.contact),
                draft.photoFileId == null ? "немає" : "є"
        );

        send(chatId, preview, Keyboards.ownerListingConfirmKeyboard());
    }

    private void onCallback(Update update) throws TelegramApiException {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        long userId = update.getCallbackQuery().getFrom().getId();
        String data = update.getCallbackQuery().getData();
        String callbackId = update.getCallbackQuery().getId();

        boolean favoriteAddCallback = data.startsWith("FAV:ADD:");

        if (!favoriteAddCallback) {
            answerCallback(callbackId);
        }

        if (!data.startsWith("LISTING:") && !favoriteAddCallback) {
            disableInlineKeyboard(update);
        }

        UserFilter f = userFilterRepo.findFullById(userId)
                .orElseGet(() -> flowService.getOrCreate(userId));

        Language lang = getUserLanguage(userId);

        if (data.equals("OWNER:PUBLISH")) {
            if (chatId != ADMIN_ID) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            OwnerListingDraft draft = ownerListingDrafts.get(userId);
            if (draft == null || !draft.readyToPublish()) {
                send(chatId, "Чернетка не готова або вже скасована. Почни з /add_owner_listing.", Keyboards.persistentNavKeyboard(lang));
                return;
            }

            OwnerListing listing = new OwnerListing();
            listing.setCreatedByTelegramId(userId);
            listing.setRegion(draft.region);
            listing.setLocality(draft.locality);
            listing.setLayout(draft.layout);
            listing.setPriceCzk(draft.priceCzk);
            listing.setTitle(draft.title);
            listing.setDescription(draft.description);
            listing.setContact(draft.contact);
            listing.setPhotoFileId(draft.photoFileId);
            listing.setCreatedAt(Instant.now());
            listing.setApprovedAt(Instant.now());

            OwnerListing saved = ownerListingService.saveApproved(listing);
            ownerListingDrafts.remove(userId);

            send(chatId,
                    "✅ Оголошення опубліковано.\nID: " + saved.getId() + "\n\nВоно тепер бере участь у фільтрах як джерело «Власник».",
                    Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (data.equals("OWNER:CANCEL")) {
            if (chatId == ADMIN_ID) {
                ownerListingDrafts.remove(userId);
                send(chatId, "Додавання оголошення скасовано.", Keyboards.persistentNavKeyboard(lang));
            }
            return;
        }

        if (data.startsWith("LANG:")) {
            String langCode = data.substring("LANG:".length());

            Language language = Language.valueOf(langCode);
            f.setLanguage(language);
            flowService.save(f);

            if (!f.isOnboarded()) {
                f.setOnboarded(true);
                flowService.save(f);

                flowService.reset(userId);

                sendRegionsEntry(chatId, userId, msg(userId, "filter.start"));

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

            sendRegionsEntry(chatId, userId, msg(userId, "filter.start"));
            return;
        }

        if (data.startsWith("FAV:ADD:")) {
            String tokenValue = data.substring("FAV:ADD:".length());
            ListingDto dto = listingCacheService.get(tokenValue);

            if (dto == null) {
                answerCallback(callbackId, msg(userId, "favorites.add.failed"));
                return;
            }

            boolean added = favoriteService.addFavorite(userId, dto);

            if (added) {
                answerCallback(callbackId, msg(userId, "favorites.added"));
            } else {
                answerCallback(callbackId, msg(userId, "favorites.already.exists"));
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

        if (data.startsWith("LISTING:")) {
            String action = data.substring("LISTING:".length());

            List<ListingDto> listings = searchCache.get(userId);

            if (listings == null || listings.isEmpty()) {
                send(chatId, msg(userId, "search.results.saved.empty"), Keyboards.mainMenuKeyboard(lang));
                return;
            }

            int index = searchCurrentIndex.getOrDefault(userId, 0);

            if ("NEXT".equals(action)) {
                if (index < listings.size() - 1) {
                    searchCurrentIndex.put(userId, index + 1);
                    sendCurrentListing(chatId, userId);
                } else {
                    send(chatId, "Це останнє оголошення.", Keyboards.mainMenuKeyboard(lang));
                }
                return;
            }

            if ("PREV".equals(action)) {
                if (index > 0) {
                    searchCurrentIndex.put(userId, index - 1);
                    sendCurrentListing(chatId, userId);
                } else {
                    send(chatId, "Це перше оголошення.", Keyboards.mainMenuKeyboard(lang));
                }
                return;
            }
        }

        if (data.startsWith("MENU:")) {
            String action = data.substring("MENU:".length());

            switch (action) {
                case "NEW" -> {
                    filterEditMode.remove(userId);
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
                                        + 1
                                        + msg(userId, "search.found.suffix"),
                                Keyboards.mainMenuKeyboard(lang));

                        startPagedSearch(chatId, userId, listings);

                    } catch (Exception e) {
                        e.printStackTrace();
                        send(chatId, msg(userId, "search.error"), Keyboards.mainMenuKeyboard(lang));
                    }
                }

                case "FILTER" -> {
                    UserFilter fullFilter = userFilterRepo.findFullById(userId)
                            .orElseGet(() -> f);
                    send(chatId, flowService.pretty(fullFilter, lang), Keyboards.filterActionsKeyboard(lang));
                }

                case "FAVORITES" -> showFavorites(chatId, userId);

                case "STOP" -> {
                    filterEditMode.remove(userId);
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

        if (data.startsWith("SERVICE:NO_AGENT")) {
            send(chatId, premiumInfo(lang), Keyboards.authorContactKeyboard(lang));
            return;
        }

        if (data.startsWith("SERVICE:SUPPORT")) {
            send(chatId, msg(userId, "support.text"), Keyboards.authorContactKeyboard(lang));
            return;
        }

        if (data.startsWith("SERVICE:REAL_ESTATE")) {
            send(chatId, realEstateSearchInfo(lang), Keyboards.authorContactKeyboard(lang));
            return;
        }

        if (data.startsWith("EDIT:")) {
            String action = data.substring("EDIT:".length());
            UserFilter fullFilter = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> f);

            switch (action) {
                case "FILTER" -> send(chatId,
                        flowService.pretty(fullFilter, lang),
                        Keyboards.editFilterKeyboard(hasDistricts(fullFilter), lang));

                case EDIT_CITY -> {
                    filterEditMode.put(userId, EDIT_CITY);
                    fullFilter.setActive(false);
                    fullFilter.setStep(FlowStep.CITY);
                    flowService.save(fullFilter);
                    sendRegionsEntry(chatId, userId, editPrompt(lang, EDIT_CITY));
                }

                case EDIT_DISTRICT -> {
                    if (!hasDistricts(fullFilter)) {
                        send(chatId,
                                editUnavailable(lang),
                                Keyboards.editFilterKeyboard(false, lang));
                        return;
                    }

                    filterEditMode.put(userId, EDIT_DISTRICT);
                    fullFilter.setActive(false);
                    fullFilter.setStep(FlowStep.DISTRICT_GROUP);
                    flowService.save(fullFilter);

                    List<RegionGroup> groups = regionGroupRepo.findByRegionId(fullFilter.getRegion().getId());
                    send(chatId, msg(userId, "district.choose"), Keyboards.regionGroupsKeyboard(groups));
                }

                case EDIT_LAYOUT -> {
                    filterEditMode.put(userId, EDIT_LAYOUT);
                    fullFilter.setActive(false);
                    fullFilter.setStep(FlowStep.LAYOUT);
                    flowService.save(fullFilter);
                    send(chatId, msg(userId, "layout.choose"), Keyboards.layoutKeyboard(lang));
                }

                case "PRICE" -> {
                    filterEditMode.remove(userId);
                    fullFilter.setActive(false);
                    if (fullFilter.getLayout() == null || fullFilter.getLayout().isBlank()) {
                        fullFilter.setStep(FlowStep.LAYOUT);
                        flowService.save(fullFilter);
                        send(chatId, msg(userId, "layout.choose"), Keyboards.layoutKeyboard(lang));
                        return;
                    }

                    fullFilter.setStep(FlowStep.MAX_PRICE);
                    flowService.save(fullFilter);
                    send(chatId, msg(userId, "price.choose"), Keyboards.priceKeyboard(lang));
                }

                default -> send(chatId, msg(userId, "callback.unknown") + data, null);
            }

            return;
        }

        if (data.startsWith("REGION:")) {
            String code = data.substring("REGION:".length());

            if ("OTHER".equals(code)) {
                List<Region> otherRegions = regionRepo.findByPopularFalseOrderByTitleAsc();

                if (otherRegions == null || otherRegions.isEmpty()) {
                    send(chatId,
                            "❌ Other cities list is empty. Check DB: popular=false is missing.",
                            Keyboards.persistentNavKeyboard(lang));
                    return;
                }

                send(chatId,
                        switch (lang) {
                            case RU -> "Выберите город:";
                            case CZ -> "Vyberte město:";
                            case EN -> "Choose a city:";
                            default -> "Оберіть місто:";
                        },
                        Keyboards.regionsKeyboard(otherRegions)
                );

                return;
            }

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
            f.setActive(false);

            if (EDIT_DISTRICT.equals(filterEditMode.remove(userId))
                    && f.getLayout() != null
                    && f.getMaxPrice() != null) {
                f.setStep(FlowStep.CONFIRM);
                enableSubscriptionAndSendListings(chatId, userId, f, lang);
                return;
            }

            f.setStep(FlowStep.LAYOUT);
            flowService.save(f);

            send(chatId, msg(userId, "layout.choose"), Keyboards.layoutKeyboard(lang));
            return;
        }

        if (data.startsWith("LAYOUT:")) {
            String layout = data.substring("LAYOUT:".length());

            f.setLayout(layout);
            f.setActive(false);

            if (EDIT_LAYOUT.equals(filterEditMode.remove(userId))
                    && f.getMaxPrice() != null) {
                f.setStep(FlowStep.CONFIRM);
                enableSubscriptionAndSendListings(chatId, userId, f, lang);
                return;
            }

            f.setStep(FlowStep.MAX_PRICE);
            flowService.save(f);

            send(chatId, msg(userId, "price.choose"), Keyboards.priceKeyboard(lang));
            return;
        }

        if (data.startsWith("PRICE:")) {
            int price = Integer.parseInt(data.substring("PRICE:".length()));

            filterEditMode.remove(userId);
            f.setMaxPrice(price);
            f.setStep(FlowStep.CONFIRM);
            enableSubscriptionAndSendListings(chatId, userId, f, lang);

            return;
        }

        if (data.startsWith("CONFIRM:SUBSCRIBE")) {
            filterEditMode.remove(userId);
            enableSubscriptionAndSendListings(chatId, userId, f, lang);

            return;
        }

        if (data.startsWith("CONFIRM:STOP")) {
            filterEditMode.remove(userId);
            f.setActive(false);
            flowService.save(f);

            send(chatId, msg(userId, "notifications.disabled"), Keyboards.mainMenuKeyboard(lang));
            return;
        }

        if (data.startsWith("CONFIRM:RESET")) {
            filterEditMode.remove(userId);
            flowService.reset(userId);
            sendRegionsEntry(chatId, userId, msg(userId, "filter.reset"));
            return;
        }

        if (data.startsWith("CONFIRM:SHOW")) {
            UserFilter fullFilter = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> f);
            send(chatId, flowService.pretty(fullFilter, lang), Keyboards.filterActionsKeyboard(lang));
            return;
        }

        send(chatId, msg(userId, "callback.unknown") + data, null);
    }

    private ReactivationResult sendReactivationMessages(int limit) {
        ReactivationResult result = new ReactivationResult();
        Instant now = Instant.now();
        Instant staleBefore = now.minus(java.time.Duration.ofDays(14));
        Instant canSendAgainBefore = now.minus(java.time.Duration.ofDays(30));

        List<UserFilter> candidates = userFilterRepo.findReactivationCandidates(
                staleBefore,
                canSendAgainBefore,
                PageRequest.of(0, limit)
        );

        result.checked = candidates.size();

        for (UserFilter user : candidates) {
            if (user.getTelegramUserId() == null) {
                result.skipped++;
                continue;
            }

            Language userLang = user.getLanguage() != null ? user.getLanguage() : Language.UA;

            try {
                send(user.getTelegramUserId(),
                        reactivationText(user, userLang),
                        Keyboards.reactivationKeyboard(userLang));

                user.setReactivationSentAt(now);
                userFilterRepo.save(user);
                result.sent++;

            } catch (TelegramApiException e) {
                if (isUnreachableTelegramUser(e.getMessage())) {
                    user.setActive(false);
                    userFilterRepo.save(user);
                    result.deactivated++;
                } else {
                    result.failed++;
                    System.out.println("Reactivation message failed for user="
                            + user.getTelegramUserId()
                            + ", error=" + e.getMessage());
                }
            } catch (Exception e) {
                result.failed++;
                System.out.println("Unexpected reactivation failure for user="
                        + user.getTelegramUserId()
                        + ", error=" + e.getMessage());
            }
        }

        return result;
    }

    private String reactivationText(UserFilter user, Language lang) {
        return switch (lang) {
            case RU -> "Привет 👋\n\n"
                    + "Ваш поиск аренды все еще включен. Если вариантов стало мало или фильтр уже неактуален, можно быстро изменить город, район, тип жилья или бюджет.\n\n"
                    + flowService.pretty(user, lang);
            case CZ -> "Ahoj 👋\n\n"
                    + "Vaše hledání nájmu je stále zapnuté. Pokud je nabídek málo nebo filtr už není aktuální, můžete rychle upravit město, oblast, typ bydlení nebo rozpočet.\n\n"
                    + flowService.pretty(user, lang);
            case EN -> "Hi 👋\n\n"
                    + "Your rent search is still active. If there are not enough listings or your filter is outdated, you can quickly update the city, district, housing type, or budget.\n\n"
                    + flowService.pretty(user, lang);
            default -> "Привіт 👋\n\n"
                    + "Ваш пошук оренди все ще увімкнений. Якщо варіантів стало мало або фільтр вже неактуальний, можна швидко змінити місто, район, тип житла або бюджет.\n\n"
                    + flowService.pretty(user, lang);
        };
    }

    private int parseAdminLimit(String text, int defaultLimit, int maxLimit) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length < 2) {
            return defaultLimit;
        }

        try {
            int parsed = Integer.parseInt(parts[1]);
            return Math.max(1, Math.min(parsed, maxLimit));
        } catch (NumberFormatException e) {
            return defaultLimit;
        }
    }

    private boolean isUnreachableTelegramUser(String message) {
        if (message == null) {
            return false;
        }

        String lower = message.toLowerCase();
        return lower.contains("bot was blocked by the user")
                || lower.contains("user is deactivated")
                || lower.contains("chat not found");
    }

    private void enableSubscriptionAndSendListings(long chatId, long userId, UserFilter filter, Language lang) throws TelegramApiException {
        filter.setActive(true);
        flowService.save(filter);

        UserFilter fullFilter = userFilterRepo.findFullById(userId)
                .orElseGet(() -> filter);

        send(chatId,
                msg(userId, "subscribe.enabled") + "\n\n" + flowService.pretty(fullFilter, lang),
                Keyboards.mainMenuKeyboard(lang));

        try {
            List<ListingDto> listings = parserService.findNewListings(userId);

            if (listings.isEmpty()) {
                send(chatId,
                        msg(userId, "search.new.empty"),
                        Keyboards.mainMenuKeyboard(lang));
                return;
            }

            send(chatId,
                    msg(userId, "search.found.prefix")
                            + listings.size()
                            + msg(userId, "search.found.middle")
                            + 1
                            + msg(userId, "search.found.suffix"),
                    Keyboards.mainMenuKeyboard(lang));

            startPagedSearch(chatId, userId, listings);

        } catch (Exception e) {
            e.printStackTrace();
            send(chatId, msg(userId, "notify.fetch.failed"), Keyboards.mainMenuKeyboard(lang));
        }
    }

    private void startPagedSearch(long chatId, long userId, List<ListingDto> listings) throws TelegramApiException {
        cleanupExpiredInteractionCaches();
        searchCache.put(userId, listings);
        searchCacheAt.put(userId, System.currentTimeMillis());
        searchCurrentIndex.put(userId, 0);
        sendCurrentListing(chatId, userId);
    }

    private boolean hasDistricts(UserFilter filter) {
        return filter != null && filter.getRegion() != null && filter.getRegion().isHasDistricts();
    }

    private String editPrompt(Language lang, String target) {
        return switch (target) {
            case EDIT_CITY -> switch (lang) {
                case RU -> "Выберите новый город:";
                case CZ -> "Vyberte nové mesto:";
                case EN -> "Choose a new city:";
                default -> "Оберіть нове місто:";
            };
            default -> switch (lang) {
                case RU -> "Выберите новое значение:";
                case CZ -> "Vyberte novou hodnotu:";
                case EN -> "Choose a new value:";
                default -> "Оберіть нове значення:";
            };
        };
    }

    private String editUnavailable(Language lang) {
        return switch (lang) {
            case RU -> "У этого города нет выбора районов. Можно изменить город, тип квартиры или цену.";
            case CZ -> "Toto město nemá výběr oblastí. Můžete změnit město, typ bytu nebo cenu.";
            case EN -> "This city has no district selector. You can change city, apartment type, or price.";
            default -> "У цьому місті немає вибору районів. Можна змінити місто, тип квартири або ціну.";
        };
    }

    private String premiumInfo(Language lang) {
        return switch (lang) {
            case RU -> """
💎 Премиум

Для тех, кто хочет искать жильё быстрее и сразу в нескольких направлениях.

Что планируем добавить:

✅ несколько поисков одновременно
Например: Praha 1+kk, Brno 2+kk, Plzeň комната.

🔎 больше подходящих предложений
Расширенный поиск по Sreality, iDNES, Bezrealitky и Bazoš.

🏠 больше вариантов без риелтора
Отдельный акцент на объявления от собственников и Bezrealitky.

Премиум сейчас в разработке. Если хочешь попасть в список первых пользователей — напиши автору.
""";
            case CZ -> """
💎 Premium

Pro ty, kteří chtějí hledat bydlení rychleji a ve více směrech najednou.

Co plánujeme přidat:

✅ více hledání najednou
Například: Praha 1+kk, Brno 2+kk, Plzeň pokoj.

🔎 více relevantních nabídek
Rozšířené hledání přes Sreality, iDNES, Bezrealitky a Bazoš.

🏠 více nabídek bez realitky
Větší důraz na nabídky od majitelů a Bezrealitky.

Premium je zatím ve vývoji. Pokud chcete být mezi prvními uživateli, napište autorovi.
""";
            case EN -> """
💎 Premium

For people who want to search faster and track several apartment searches at once.

What we plan to add:

✅ multiple searches at once
For example: Praha 1+kk, Brno 2+kk, Plzeň room.

🔎 more relevant listings
Expanded search across Sreality, iDNES, Bezrealitky and Bazoš.

🏠 more no-agent options
More focus on owner listings and Bezrealitky.

Premium is still in development. If you want early access, contact the author.
""";
            default -> """
💎 Преміум

Для тих, хто хоче шукати житло швидше й одразу в кількох напрямках.

Що плануємо додати:

✅ кілька пошуків одночасно
Наприклад: Praha 1+kk, Brno 2+kk, Plzeň кімната.

🔎 більше релевантних пропозицій
Розширений пошук по Sreality, iDNES, Bezrealitky та Bazoš.

🏠 більше варіантів без рієлтора
Більший акцент на оголошеннях від власників і Bezrealitky.

Преміум зараз у розробці. Якщо хочеш потрапити до перших користувачів — напиши автору.
""";
        };
    }

    private String realEstateSearchInfo(Language lang) {
        return switch (lang) {
            case RU -> """
🏘 Поиск недвижимости

Сервис в разработке.

План: поиск квартир, домов и других объектов недвижимости в Чехии в одном месте. Если хотите предложить идею или первыми протестировать сервис, напишите автору.
""";
            case CZ -> """
🏘 Hledání nemovitostí

Služba je ve vývoji.

Plán: hledání bytů, domů a dalších nemovitostí v Česku na jednom místě. Pokud máte nápad nebo chcete službu vyzkoušet mezi prvními, napište autorovi.
""";
            case EN -> """
🏘 Real estate search

This service is in development.

Plan: search apartments, houses, and other real estate in Czechia in one place. If you have an idea or want to test it early, contact the author.
""";
            default -> """
🏘 Пошук нерухомості

Сервіс у розробці.

План: пошук квартир, будинків та інших об'єктів нерухомості в Чехії в одному місці. Якщо маєте ідею або хочете протестувати сервіс першими, напишіть автору.
""";
        };
    }

    private void sendCurrentListing(long chatId, long userId) throws TelegramApiException {
        List<ListingDto> listings = searchCache.get(userId);
        Language lang = getUserLanguage(userId);

        if (listings == null || listings.isEmpty()) {
            send(chatId, msg(userId, "search.results.saved.empty"), Keyboards.mainMenuKeyboard(lang));
            return;
        }

        int index = searchCurrentIndex.getOrDefault(userId, 0);

        if (index < 0) {
            index = 0;
        }

        if (index >= listings.size()) {
            index = listings.size() - 1;
        }

        searchCurrentIndex.put(userId, index);

        ListingDto listing = listings.get(index);
        sendListingCard(chatId, userId, listing, index, listings.size());
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

    private void answerCallback(String callbackQueryId) {
        try {
            telegramClient.execute(
                    AnswerCallbackQuery.builder()
                            .callbackQueryId(callbackQueryId)
                            .build()
            );
        } catch (TelegramApiException e) {
            if (!isExpiredCallback(e)) {
                System.out.println("AnswerCallbackQuery failed: " + e.getMessage());
            }
        }
    }

    private void answerCallback(String callbackQueryId, String text) {
        try {
            telegramClient.execute(
                    AnswerCallbackQuery.builder()
                            .callbackQueryId(callbackQueryId)
                            .text(text)
                            .showAlert(false)
                            .build()
            );
        } catch (TelegramApiException e) {
            if (!isExpiredCallback(e)) {
                System.out.println("AnswerCallbackQuery failed: " + e.getMessage());
            }
        }
    }

    private boolean isExpiredCallback(TelegramApiException e) {
        String message = e.getMessage();
        return message != null
                && message.contains("query is too old and response timeout expired");
    }

    private void sendRegionsEntry(long chatId, long userId, String text) throws TelegramApiException {
        Language lang = getUserLanguage(userId);
        List<Region> popularRegions = regionRepo.findByPopularTrueOrderByTitleAsc();

        if (popularRegions == null || popularRegions.isEmpty()) {
            send(chatId,
                    "❌ No popular regions in DB. Check regions.popular=true.",
                    Keyboards.persistentNavKeyboard(lang));
            return;
        }

        send(chatId, text, Keyboards.regionsEntryKeyboard(popularRegions, lang));
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
                        "💰 " + formatPrice(l.priceCzk()) + pricePeriod(lang) + "\n" +
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
                        "💰 " + formatPrice(fav.getPriceCzk() != null ? fav.getPriceCzk() : 0) + pricePeriod(lang) + "\n" +
                        "📍 " + msg(userId, "listing.location") + ": " + nvl(fav.getLocality());

        int key = fav.getLink().hashCode();
        favoriteLinkCache.put(key, fav.getLink());
        favoriteLinkCacheAt.put(key, System.currentTimeMillis());
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
            return true;
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

    private String formatPrice(int price) {
        if (price <= 0) {
            return "—";
        }

        return String.format("%,d", price)
                .replace(",", " ") + " Kč";
    }

    private String pricePeriod(Language lang) {
        return switch (lang) {
            case RU -> " / мес";
            case CZ -> " / měs";
            case EN -> " / month";
            default -> " / міс";
        };
    }

    private String addedLabel(Language lang) {
        return switch (lang) {
            case RU -> "Добавлено";
            case CZ -> "Přidáno";
            case EN -> "Added";
            default -> "Додано";
        };
    }

    private String freshnessIcon(LocalDateTime time) {
        if (time == null) {
            return "🕒";
        }

        java.time.Duration diff = java.time.Duration.between(time, LocalDateTime.now());

        if (diff.toHours() < 3) {
            return "🔥";
        }

        return "🕒";
    }

    private String safeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "https://t.me/zhytloCZ_bot";
        }
        String lower = url.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return "https://t.me/evzen_cz";
        }
        return url;
    }

    private void cleanupExpiredInteractionCaches() {
        long cutoff = System.currentTimeMillis() - INTERACTION_CACHE_TTL_MILLIS;

        searchCacheAt.entrySet().removeIf(entry -> {
            if (entry.getValue() >= cutoff) {
                return false;
            }

            Long cachedUserId = entry.getKey();
            searchCache.remove(cachedUserId);
            searchOffset.remove(cachedUserId);
            searchCurrentIndex.remove(cachedUserId);
            return true;
        });

        favoriteLinkCacheAt.entrySet().removeIf(entry -> {
            if (entry.getValue() >= cutoff) {
                return false;
            }

            favoriteLinkCache.remove(entry.getKey());
            return true;
        });
    }

    private void sendListingCard(long chatId, long userId, ListingDto l, int index, int total) throws TelegramApiException {
        Language lang = getUserLanguage(userId);

        String caption =
                "🏠 " + nvl(l.title()) + "\n\n" +
                        "💰 " + formatPrice(l.priceCzk()) + pricePeriod(lang) + "\n" +
                        "📍 " + msg(userId, "listing.location") + ": " + nvl(l.locality()) + "\n" +
                        freshnessIcon(l.foundAt()) + " " + addedLabel(lang) + ": " + formatTimeAgo(l.foundAt(), lang) + "\n" +
                        "🏷 " + msg(userId, "listing.source") + ": " + nvl(l.source()) + "\n\n" +
                        "📄 " + listingLabel(lang) + " " + (index + 1) + " / " + total;

        String tokenValue = listingCacheService.put(l);
        String link = safeUrl(l.link());

        if (hasUsablePhotoUrl(l.photoUrl())) {
            try {
                telegramClient.execute(
                        SendPhoto.builder()
                                .chatId(chatId)
                                .photo(new InputFile(l.photoUrl()))
                                .caption(trimCaption(caption))
                                .replyMarkup(Keyboards.listingPagerKeyboard(tokenValue, link, lang))
                                .build()
                );
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        telegramClient.execute(
                SendMessage.builder()
                        .chatId(chatId)
                        .text(caption)
                        .replyMarkup(Keyboards.listingPagerKeyboard(tokenValue, link, lang))
                        .build()
        );
    }

    private String formatTimeAgo(LocalDateTime time, Language lang) {
        if (time == null) {
            return "—";
        }

        java.time.Duration diff = java.time.Duration.between(time, LocalDateTime.now());

        long minutes = diff.toMinutes();
        long hours = diff.toHours();
        long days = diff.toDays();

        if (minutes < 60) {
            return switch (lang) {
                case RU -> "только что";
                case CZ -> "právě teď";
                case EN -> "just now";
                default -> "щойно";
            };
        }

        if (hours < 24) {
            return switch (lang) {
                case RU -> hours + " ч назад";
                case CZ -> "před " + hours + " h";
                case EN -> hours + "h ago";
                default -> hours + " год тому";
            };
        }

        if (days == 1) {
            return switch (lang) {
                case RU -> "вчера";
                case CZ -> "včera";
                case EN -> "yesterday";
                default -> "вчора";
            };
        }

        if (days == 2) {
            return switch (lang) {
                case RU -> "позавчера";
                case CZ -> "předevčírem";
                case EN -> "the day before yesterday";
                default -> "позавчора";
            };
        }

        if (days < 7) {
            return switch (lang) {
                case RU -> days + " дн. назад";
                case CZ -> "před " + days + " dny";
                case EN -> days + " days ago";
                default -> days + " днів тому";
            };
        }

        return time.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"));
    }

    private String listingLabel(Language lang) {
        return switch (lang) {
            case RU -> "Объявление";
            case CZ -> "Inzerát";
            case EN -> "Listing";
            default -> "Оголошення";
        };
    }

    private static class ReactivationResult {
        private int checked;
        private int sent;
        private int skipped;
        private int deactivated;
        private int failed;
    }

    private static class OwnerListingDraft {
        private enum Step {
            CITY,
            LOCALITY,
            LAYOUT,
            PRICE,
            TITLE,
            DESCRIPTION,
            CONTACT,
            PHOTO,
            CONFIRM
        }

        private Step step = Step.CITY;
        private Region region;
        private String locality;
        private String layout;
        private Integer priceCzk;
        private String title;
        private String description;
        private String contact;
        private String photoFileId;

        private boolean readyToPublish() {
            return step == Step.CONFIRM
                    && region != null
                    && locality != null
                    && layout != null
                    && priceCzk != null
                    && title != null
                    && contact != null;
        }

        private String stepLabel() {
            return switch (step) {
                case CITY -> "місто";
                case LOCALITY -> "локація";
                case LAYOUT -> "тип житла";
                case PRICE -> "ціна";
                case TITLE -> "назва";
                case DESCRIPTION -> "опис";
                case CONTACT -> "контакт";
                case PHOTO -> "фото";
                case CONFIRM -> "підтвердження";
            };
        }
    }
}
