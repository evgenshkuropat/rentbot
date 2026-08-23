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
import com.yourapp.rentbot.service.SchedulerService;
import com.yourapp.rentbot.service.dto.ListingDto;
import com.yourapp.rentbot.ui.Keyboards;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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
import com.yourapp.rentbot.service.dto.SchedulerRunStats;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class RentBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(RentBot.class);

    private final TelegramClient telegramClient;
    private final FlowService flowService;
    private final RegionRepo regionRepo;
    private final RegionGroupRepo regionGroupRepo;
    private final UserFilterRepo userFilterRepo;
    private final ParserService parserService;
    private final SchedulerService schedulerService;
    private final NotificationService notificationService;
    private final OwnerListingService ownerListingService;
    private final FavoriteService favoriteService;
    private final ListingCacheService listingCacheService;
    private final MessageService messageService;

    private final String token;
    private final long adminId;
    private final boolean milestone1500AutoEnabled;
    private final int milestone1500AutoBatchSize;
    private final AtomicBoolean milestone1500AutoRunning = new AtomicBoolean(false);

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
            @Value("${TELEGRAM_ADMIN_ID:1246486851}") long adminId,
            TelegramClient telegramClient,
            FlowService flowService,
            RegionRepo regionRepo,
            RegionGroupRepo regionGroupRepo,
            UserFilterRepo userFilterRepo,
            ParserService parserService,
            SchedulerService schedulerService,
            NotificationService notificationService,
            OwnerListingService ownerListingService,
            FavoriteService favoriteService,
            ListingCacheService listingCacheService,
            MessageService messageService,
            @Value("${rentbot.milestone1500.auto-enabled:false}") boolean milestone1500AutoEnabled,
            @Value("${rentbot.milestone1500.auto-batch-size:25}") int milestone1500AutoBatchSize
    ) {
        this.token = token;
        this.adminId = adminId;
        this.telegramClient = telegramClient;
        this.flowService = flowService;
        this.regionRepo = regionRepo;
        this.regionGroupRepo = regionGroupRepo;
        this.userFilterRepo = userFilterRepo;
        this.parserService = parserService;
        this.schedulerService = schedulerService;
        this.notificationService = notificationService;
        this.ownerListingService = ownerListingService;
        this.favoriteService = favoriteService;
        this.listingCacheService = listingCacheService;
        this.messageService = messageService;
        this.milestone1500AutoEnabled = milestone1500AutoEnabled;
        this.milestone1500AutoBatchSize = Math.max(1, Math.min(milestone1500AutoBatchSize, 100));
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @PostConstruct
    public void logMilestone1500AutoConfig() {
        log.info(
                "Milestone 1500 auto broadcast config: enabled={}, batchSize={}",
                milestone1500AutoEnabled,
                milestone1500AutoBatchSize
        );
    }

    @Scheduled(
            fixedDelayString = "${rentbot.milestone1500.auto-delay-ms:600000}",
            initialDelayString = "${rentbot.milestone1500.auto-initial-delay-ms:120000}"
    )
    public void sendMilestone1500Automatically() {
        if (!milestone1500AutoEnabled) {
            return;
        }

        if (!milestone1500AutoRunning.compareAndSet(false, true)) {
            log.warn("Milestone 1500 auto broadcast already running, skipping...");
            return;
        }

        try {
            ReactivationResult result = sendMilestone1500Messages(milestone1500AutoBatchSize);

            log.info(
                    "Milestone 1500 auto broadcast: checked={}, sent={}, skipped={}, deactivated={}, failed={}",
                    result.checked,
                    result.sent,
                    result.skipped,
                    result.deactivated,
                    result.failed
            );
        } catch (Exception e) {
            log.error("Milestone 1500 auto broadcast failed", e);
        } finally {
            milestone1500AutoRunning.set(false);
        }
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

        if (text.equalsIgnoreCase("/add_owner_listing")) {
            startOwnerListingDraft(chatId, userId, update.getMessage().getFrom().getUserName(), lang);
            return;
        }

        if (text.equalsIgnoreCase("/cancel") && ownerListingDrafts.containsKey(userId)) {
            ownerListingDrafts.remove(userId);
            send(chatId, ownerListingCancelledText(lang), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (ownerListingDrafts.containsKey(userId) && !isPersistentMenuText(text)) {
            handleOwnerListingText(chatId, userId, text, lang);
            return;
        }

        if (ownerListingDrafts.containsKey(userId)) {
            ownerListingDrafts.remove(userId);
        }

        if (text.equalsIgnoreCase("/admin")) {
            cleanupExpiredInteractionCaches();

            if (chatId != adminId) {
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
            long approvedOwnerListings = ownerListingService.countApprovedListings();
            java.time.Instant now = java.time.Instant.now();
            long sentLast14Days = notificationService.countSentSince(
                    now.minus(java.time.Duration.ofDays(14))
            );

            int cachedSearchUsers = searchCache.size();
            int cachedSearchResults = searchCache.values()
                    .stream()
                    .mapToInt(List::size)
                    .sum();

            int pagingUsers = searchCurrentIndex.size();
            int favoriteCacheSize = favoriteLinkCache.size();

            ParserRunStats runStats = parserService.getLastRunStats();
            SchedulerRunStats schedulerStats = schedulerService.getLastRunStats();

            int filteredBaseOther = runStats.filteredBaseTotal()
                    - runStats.filteredBaseSreality()
                    - runStats.filteredBaseIdnes()
                    - runStats.filteredBaseBezrealitky()
                    - runStats.filteredBaseBazos();
            int finalOther = runStats.finalFiltered()
                    - runStats.finalSreality()
                    - runStats.finalIdnes()
                    - runStats.finalBezrealitky()
                    - runStats.finalBazos();

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
🧭 STEP CONFIRM (неактивні): %d
🧭 CONFIRM + активна підписка: %d

⭐ Усього в обраному: %d
🏡 Унікальних активних оголошень власників: %d
📩 Успішно надіслано за останні 14 днів: %d

🕒 Оновлювались за 24 год: %d
📆 Оновлювались за 7 днів: %d

📈 Конверсія в активну підписку: %d%%

🗂 Користувачів у searchCache: %d
📦 Оголошень у searchCache: %d
📄 Користувачів у paging: %d
🧷 favoriteLinkCache: %d

📡 Останній парсинг / ручний пошук:
Sreality raw: %d
iDNES raw: %d
Bezrealitky raw: %d
Bazoš raw: %d

🔁 Після дедуплікації:
By link: %d
By signature: %d

🧪 Останній повний цикл до diversify:
Всього: %d
Sreality: %d
iDNES: %d
Bezrealitky: %d
Bazoš: %d
Власник — попадань у підбірки: %d

🎯 Останній повний цикл у фінальній видачі:
Всього: %d
Sreality: %d
iDNES: %d
Bezrealitky: %d
Bazoš: %d
Власник — попадань у підбірки: %d

📬 Останній повний цикл розсилки:
Оброблено користувачів: %d
Зі співпадіннями: %d
Запусків парсерів: %d
Кандидатів у фінальній видачі: %d
Перевірено кандидатів: %d
Нових успішно надіслано: %d
Пропущено через ліміт: %d
Після фільтрів: %d
У фінальній видачі: %d
Власницьких попадань у підбірки: %d
Користувачів зі співпадіннями від власників: %d
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
                            approvedOwnerListings,
                            sentLast14Days,
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
                            filteredBaseOther,

                            runStats.finalFiltered(),
                            runStats.finalSreality(),
                            runStats.finalIdnes(),
                            runStats.finalBezrealitky(),
                            runStats.finalBazos(),
                            finalOther,

                            schedulerStats.usersProcessed(),
                            schedulerStats.usersWithMatches(),
                            schedulerStats.parserRuns(),
                            schedulerStats.totalCandidates(),
                            schedulerStats.totalSendAttempts(),
                            schedulerStats.totalSent(),
                            schedulerStats.totalSkippedByLimit(),
                            schedulerStats.aggregateFilteredBase(),
                            schedulerStats.aggregateFinal(),
                            schedulerStats.ownerMatches(),
                            schedulerStats.usersWithOwnerMatches()
                    );

            send(chatId, stats, Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (text.toLowerCase().startsWith("/admin_reactivate")) {
            if (chatId != adminId) {
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

        if (text.toLowerCase().startsWith("/admin_milestone1500")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            int limit = parseAdminLimit(text, 50, 100);
            ReactivationResult result = sendMilestone1500Messages(limit);

            send(chatId,
                    """
                    🎉 Milestone 1500 finished

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

        if (text.toLowerCase().startsWith("/admin_owner_list")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            int limit = parseAdminLimit(text, 10, 50);
            sendOwnerListingsList(chatId, limit);
            return;
        }

        if (text.toLowerCase().startsWith("/admin_owner_view")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            Long listingId = parseAdminIdArgument(text);
            showOwnerListingById(chatId, listingId, lang);
            return;
        }

        if (text.toLowerCase().startsWith("/admin_owner_archive")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            Long listingId = parseAdminIdArgument(text);
            archiveOwnerListingById(chatId, listingId, lang);
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

        if (text.equals(msg(userId, "menu.new.search"))
                || text.equals("🔄 Новий пошук")
                || text.equals("🔄 Новый поиск")
                || text.equals("🔄 Nové hledání")
                || text.equals("🔄 New search")) {
            filterEditMode.remove(userId);
            flowService.reset(userId);

            sendRegionsEntry(chatId, userId, msg(userId, "search.new"));
            return;
        }

        if (text.equals(msg(userId, "menu.my.filter"))
                || text.equals("📋 Мій фільтр")
                || text.equals("📋 Мой фильтр")
                || text.equals("📋 Můj filtr")
                || text.equals("📋 My filter")) {
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
            send(chatId,
                    switch (lang) {
                        case RU -> "Остановить автоматический поиск и уведомления?";
                        case CZ -> "Zastavit automatické hledání a upozornění?";
                        case EN -> "Stop automatic search and notifications?";
                        default -> "Зупинити автоматичний пошук і сповіщення?";
                    },
                    Keyboards.stopConfirmationKeyboard(lang));
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

        if (text.equals("🏠 Додати житло")
                || text.equals("🏠 Добавить жильё")
                || text.equals("🏠 Přidat bydlení")
                || text.equals("🏠 Add listing")
                || text.equals("🏠 Додати житло від власника")
                || text.equals("🏠 Добавить жильё от собственника")
                || text.equals("🏠 Přidat nabídku od majitele")
                || text.equals("🏠 Add owner listing")) {
            startOwnerListingDraft(chatId, userId, update.getMessage().getFrom().getUserName(), lang);
            return;
        }

        if (text.equals("💎 Преміум")
                || text.equals("💎 Премиум")
                || text.equals("💎 Premium")) {
            send(chatId, premiumInfo(lang), Keyboards.authorContactKeyboard(lang));
            return;
        }

        if (text.equals(msg(userId, "menu.support.project"))) {
            send(chatId, msg(userId, "support.text"), Keyboards.supportKeyboard(lang));
            return;
        }

        if (text.equalsIgnoreCase("/menu")) {
            send(chatId, msg(userId, "menu.title"), Keyboards.persistentNavKeyboard(lang));
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

        if (text.equals("🔍 Перевірити нові")
                || text.equals("🔍 Проверить новые")
                || text.equals("🔍 Zkontrolovat nové")
                || text.equals("🔍 Check new")
                || text.equals("🔍 Нові квартири")
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

    private boolean isPersistentMenuText(String text) {
        return text.equalsIgnoreCase("/start")
                || text.equalsIgnoreCase("/menu")
                || text.equalsIgnoreCase("/language")
                || text.equalsIgnoreCase("/test")
                || text.equals("🔄 Новий пошук")
                || text.equals("🔄 Новый поиск")
                || text.equals("🔄 Nové hledání")
                || text.equals("🔄 New search")
                || text.equals("⚙️ Налаштувати пошук")
                || text.equals("⚙️ Настроить поиск")
                || text.equals("⚙️ Nastavit hledání")
                || text.equals("⚙️ Set up search")
                || text.equals("📋 Мій фільтр")
                || text.equals("📋 Мой фильтр")
                || text.equals("📋 Můj filtr")
                || text.equals("📋 My filter")
                || text.equals("📋 Мій пошук")
                || text.equals("📋 Мой поиск")
                || text.equals("📋 Moje hledání")
                || text.equals("📋 My search")
                || text.equals("🔍 Нові квартири")
                || text.equals("🔍 Новые квартиры")
                || text.equals("🔍 Nové byty")
                || text.equals("🔍 New listings")
                || text.equals("🔍 Перевірити нові")
                || text.equals("🔍 Проверить новые")
                || text.equals("🔍 Zkontrolovat nové")
                || text.equals("🔍 Check new")
                || text.equals("⭐ Обране")
                || text.equals("⭐ Избранное")
                || text.equals("⭐ Oblíbené")
                || text.equals("⭐ Favorites")
                || text.equals("🏠 Додати житло")
                || text.equals("🏠 Добавить жильё")
                || text.equals("🏠 Přidat bydlení")
                || text.equals("🏠 Add listing")
                || text.equals("💎 Преміум")
                || text.equals("💎 Премиум")
                || text.equals("💎 Premium")
                || text.equals("🌐 Мова / Language")
                || text.equals("🌐 Язык / Language")
                || text.equals("🌐 Jazyk / Language")
                || text.equals("🌐 Language")
                || text.equals("🤝 Інші сервіси")
                || text.equals("🤝 Другие сервисы")
                || text.equals("🤝 Další služby")
                || text.equals("🤝 Other services")
                || text.equals("📦 Інші сервіси")
                || text.equals("📦 Другие сервисы")
                || text.equals("📦 Další služby")
                || text.equals("📦 Other services");
    }

    private void startOwnerListingDraft(long chatId, long userId, String username, Language lang) throws TelegramApiException {
        OwnerListingDraft draft = new OwnerListingDraft();
        draft.createdByUsername = username;
        ownerListingDrafts.put(userId, draft);

        send(chatId,
                switch (lang) {
                    case RU -> """
                            🏠 Добавить жильё

                            Заполните короткую анкету. После проверки объявление сможет появиться в боте для людей, которым оно подходит по фильтру.

                            1/8 Напишите город или округ, например: Praha, Brno, Kolín, Plzeň.

                            Отменить: /cancel
                            """;
                    case CZ -> """
                            🏠 Přidat bydlení

                            Vyplňte krátký formulář. Po kontrole se nabídka může zobrazit lidem, kterým odpovídá podle filtru.

                            1/8 Napište město nebo okres, například: Praha, Brno, Kolín, Plzeň.

                            Zrušit: /cancel
                            """;
                    case EN -> """
                            🏠 Add listing

                            Fill in a short form. After review, the listing can appear in the bot for people whose filter matches it.

                            1/8 Send the city or district, for example: Praha, Brno, Kolín, Plzeň.

                            Cancel: /cancel
                            """;
                    default -> """
                            🏠 Додати житло

                            Заповніть коротку анкету. Після перевірки оголошення може зʼявитися в боті для людей, яким воно підходить за фільтром.

                            1/8 Напишіть місто або округ, наприклад: Praha, Brno, Kolín, Plzeň.

                            Скасувати: /cancel
                            """;
                },
                Keyboards.persistentNavKeyboard(lang));
    }

    private String ownerListingCancelledText(Language lang) {
        return switch (lang) {
            case RU -> "Добавление объявления отменено.";
            case CZ -> "Přidání nabídky bylo zrušeno.";
            case EN -> "Listing submission cancelled.";
            default -> "Додавання оголошення скасовано.";
        };
    }

    private String ownerListingPhotoRequiredText(Language lang) {
        return switch (lang) {
            case RU -> "8/8 Пришлите фото квартиры. Фото обязательно для отправки на проверку.";
            case CZ -> "8/8 Pošlete fotku bytu. Fotka je povinná pro odeslání ke kontrole.";
            case EN -> "8/8 Send an apartment photo. A photo is required before review.";
            default -> "8/8 Надішліть фото квартири. Фото обовʼязкове для відправки на перевірку.";
        };
    }

    private String ownerListingUnexpectedPhotoText(Language lang, String expectedStep) {
        return switch (lang) {
            case RU -> "Фото нужно будет отправить на последнем шаге. Сейчас ожидаю: " + expectedStep + ".";
            case CZ -> "Fotku pošlete až v posledním kroku. Teď očekávám: " + expectedStep + ".";
            case EN -> "You will send the photo in the last step. Right now I am waiting for: " + expectedStep + ".";
            default -> "Фото потрібно буде надіслати на останньому кроці. Зараз очікую: " + expectedStep + ".";
        };
    }

    private String ownerListingRegionNotFoundText(Language lang) {
        return switch (lang) {
            case RU -> "Не нашёл такой город/округ в базе. Напишите как в боте, например: Praha, Brno, Kolín, Plzeň.";
            case CZ -> "Takové město nebo okres jsem v databázi nenašel. Napište ho jako v botu, například: Praha, Brno, Kolín, Plzeň.";
            case EN -> "I could not find that city or district in the database. Write it as in the bot, for example: Praha, Brno, Kolín, Plzeň.";
            default -> "Не знайшов таке місто/округ у базі. Напишіть як у боті, наприклад: Praha, Brno, Kolín, Plzeň.";
        };
    }

    private String ownerListingLocalityPromptText(Language lang) {
        return switch (lang) {
            case RU -> "2/8 Локация или адрес. Например: Kolín - Kolín II, Masarykova.";
            case CZ -> "2/8 Lokalita nebo adresa. Například: Kolín - Kolín II, Masarykova.";
            case EN -> "2/8 Location or address. For example: Kolín - Kolín II, Masarykova.";
            default -> "2/8 Локація або адреса. Наприклад: Kolín - Kolín II, Masarykova.";
        };
    }

    private String ownerListingLocalityRequiredText(Language lang) {
        return switch (lang) {
            case RU -> "Локация не может быть пустой. Напишите район, город или адрес.";
            case CZ -> "Lokalita nesmí být prázdná. Napište část města, město nebo adresu.";
            case EN -> "Location cannot be empty. Send the district, city, or address.";
            default -> "Локація не може бути пустою. Напишіть район, місто або адресу.";
        };
    }

    private String ownerListingLayoutPromptText(Language lang) {
        return switch (lang) {
            case RU -> "3/8 Тип жилья: room, 1, 2, 3 или 4.";
            case CZ -> "3/8 Typ bydlení: room, 1, 2, 3 nebo 4.";
            case EN -> "3/8 Housing type: room, 1, 2, 3, or 4.";
            default -> "3/8 Тип житла: room, 1, 2, 3 або 4.";
        };
    }

    private String ownerListingLayoutInvalidText(Language lang) {
        return switch (lang) {
            case RU -> "Не понял тип. Напишите: room, 1, 2, 3 или 4.";
            case CZ -> "Nerozumím typu. Napište: room, 1, 2, 3 nebo 4.";
            case EN -> "I did not understand the type. Send: room, 1, 2, 3, or 4.";
            default -> "Не зрозумів тип. Напишіть: room, 1, 2, 3 або 4.";
        };
    }

    private String ownerListingPricePromptText(Language lang) {
        return switch (lang) {
            case RU -> "4/8 Цена в Kč. Например: 12990.";
            case CZ -> "4/8 Cena v Kč. Například: 12990.";
            case EN -> "4/8 Price in Kč. For example: 12990.";
            default -> "4/8 Ціна в Kč. Наприклад: 12990.";
        };
    }

    private String ownerListingPriceInvalidText(Language lang) {
        return switch (lang) {
            case RU -> "Цена должна быть числом. Например: 12990.";
            case CZ -> "Cena musí být číslo. Například: 12990.";
            case EN -> "Price must be a number. For example: 12990.";
            default -> "Ціна має бути числом. Наприклад: 12990.";
        };
    }

    private String ownerListingTitlePromptText(Language lang) {
        return switch (lang) {
            case RU -> "5/8 Название объявления. Например: Pronájem bytu 2+kk 39 m² Masarykova, Kolín.";
            case CZ -> "5/8 Název nabídky. Například: Pronájem bytu 2+kk 39 m² Masarykova, Kolín.";
            case EN -> "5/8 Listing title. For example: Pronájem bytu 2+kk 39 m² Masarykova, Kolín.";
            default -> "5/8 Назва оголошення. Наприклад: Pronájem bytu 2+kk 39 m² Masarykova, Kolín.";
        };
    }

    private String ownerListingTitleRequiredText(Language lang) {
        return switch (lang) {
            case RU -> "Название не может быть пустым.";
            case CZ -> "Název nesmí být prázdný.";
            case EN -> "Title cannot be empty.";
            default -> "Назва не може бути пустою.";
        };
    }

    private String ownerListingDescriptionPromptText(Language lang) {
        return switch (lang) {
            case RU -> "6/8 Описание. Можно коротко: мебель, депозит, доступность. Если описания нет, напишите -";
            case CZ -> "6/8 Popis. Stačí krátce: nábytek, kauce, dostupnost. Pokud popis není, napište -";
            case EN -> "6/8 Description. Short is fine: furniture, deposit, availability. If there is no description, send -";
            default -> "6/8 Опис. Можна коротко: меблі, депозит, доступність. Якщо опису немає, напишіть -";
        };
    }

    private String ownerListingContactPromptText(Language lang) {
        return switch (lang) {
            case RU -> "7/8 Контакт владельца: телефон, Telegram или другой способ связи.";
            case CZ -> "7/8 Kontakt na majitele: telefon, Telegram nebo jiný způsob spojení.";
            case EN -> "7/8 Owner contact: phone, Telegram, or another contact method.";
            default -> "7/8 Контакт власника: телефон, Telegram або інший спосіб зв'язку.";
        };
    }

    private String ownerListingContactRequiredText(Language lang) {
        return switch (lang) {
            case RU -> "Контакт не может быть пустым.";
            case CZ -> "Kontakt nesmí být prázdný.";
            case EN -> "Contact cannot be empty.";
            default -> "Контакт не може бути пустим.";
        };
    }

    private void onPhoto(Update update) throws TelegramApiException {
        long chatId = update.getMessage().getChatId();
        long userId = update.getMessage().getFrom().getId();
        Language lang = getUserLanguage(userId);

        OwnerListingDraft draft = ownerListingDrafts.get(userId);
        if (draft == null) {
            return;
        }

        if (draft.step != OwnerListingDraft.Step.PHOTO) {
            send(chatId, ownerListingUnexpectedPhotoText(lang, draft.stepLabel(lang)), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        List<PhotoSize> photos = update.getMessage().getPhoto();
        if (photos == null || photos.isEmpty()) {
            send(chatId, ownerListingPhotoRequiredText(lang), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        draft.photoFileId = photos.get(photos.size() - 1).getFileId();
        draft.step = OwnerListingDraft.Step.CONFIRM;
        sendOwnerListingPreview(chatId, draft, lang);
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
                            ownerListingRegionNotFoundText(lang),
                            Keyboards.persistentNavKeyboard(lang));
                    return;
                }
                draft.region = region.get();
                draft.step = OwnerListingDraft.Step.LOCALITY;
                send(chatId, ownerListingLocalityPromptText(lang), Keyboards.persistentNavKeyboard(lang));
            }
            case LOCALITY -> {
                draft.locality = cleanRequired(text);
                if (draft.locality == null) {
                    send(chatId, ownerListingLocalityRequiredText(lang), Keyboards.persistentNavKeyboard(lang));
                    return;
                }
                draft.step = OwnerListingDraft.Step.LAYOUT;
                send(chatId, ownerListingLayoutPromptText(lang), Keyboards.persistentNavKeyboard(lang));
            }
            case LAYOUT -> {
                String layout = normalizeOwnerLayout(text);
                if (layout == null) {
                    send(chatId, ownerListingLayoutInvalidText(lang), Keyboards.persistentNavKeyboard(lang));
                    return;
                }
                draft.layout = layout;
                draft.step = OwnerListingDraft.Step.PRICE;
                send(chatId, ownerListingPricePromptText(lang), Keyboards.persistentNavKeyboard(lang));
            }
            case PRICE -> {
                Integer price = parseOwnerPrice(text);
                if (price == null) {
                    send(chatId, ownerListingPriceInvalidText(lang), Keyboards.persistentNavKeyboard(lang));
                    return;
                }
                draft.priceCzk = price;
                draft.step = OwnerListingDraft.Step.TITLE;
                send(chatId, ownerListingTitlePromptText(lang), Keyboards.persistentNavKeyboard(lang));
            }
            case TITLE -> {
                draft.title = cleanRequired(text);
                if (draft.title == null) {
                    send(chatId, ownerListingTitleRequiredText(lang), Keyboards.persistentNavKeyboard(lang));
                    return;
                }
                draft.step = OwnerListingDraft.Step.DESCRIPTION;
                send(chatId, ownerListingDescriptionPromptText(lang), Keyboards.persistentNavKeyboard(lang));
            }
            case DESCRIPTION -> {
                draft.description = "-".equals(text.trim()) ? null : text.trim();
                draft.step = OwnerListingDraft.Step.CONTACT;
                send(chatId, ownerListingContactPromptText(lang), Keyboards.persistentNavKeyboard(lang));
            }
            case CONTACT -> {
                draft.contact = cleanRequired(text);
                if (draft.contact == null) {
                    send(chatId, ownerListingContactRequiredText(lang), Keyboards.persistentNavKeyboard(lang));
                    return;
                }
                draft.step = OwnerListingDraft.Step.PHOTO;
                send(chatId, ownerListingPhotoRequiredText(lang), Keyboards.persistentNavKeyboard(lang));
            }
            case PHOTO -> {
                send(chatId, ownerListingPhotoRequiredText(lang), Keyboards.persistentNavKeyboard(lang));
            }
            case CONFIRM -> {
                if (isOwnerListingSubmitText(text)) {
                    submitOwnerListingDraft(chatId, userId, draft, lang);
                    return;
                }

                if (isOwnerListingCancelText(text)) {
                    ownerListingDrafts.remove(userId);
                    send(chatId, ownerListingCancelledText(lang), Keyboards.persistentNavKeyboard(lang));
                    return;
                }

                send(chatId,
                        ownerListingConfirmHelpText(lang),
                        Keyboards.ownerListingConfirmKeyboard(lang));
            }
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

    private void submitOwnerListingDraft(long chatId,
                                         long userId,
                                         OwnerListingDraft draft,
                                         Language lang) throws TelegramApiException {
        if (draft == null || !draft.readyToPublish()) {
            send(chatId, ownerListingDraftNotReadyText(lang), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        OwnerListing listing = new OwnerListing();
        listing.setCreatedByTelegramId(userId);
        listing.setCreatedByUsername(draft.createdByUsername);
        listing.setRegion(draft.region);
        listing.setLocality(draft.locality);
        listing.setLayout(draft.layout);
        listing.setPriceCzk(draft.priceCzk);
        listing.setTitle(draft.title);
        listing.setDescription(draft.description);
        listing.setContact(draft.contact);
        listing.setPhotoFileId(draft.photoFileId);
        listing.setCreatedAt(Instant.now());

        OwnerListing saved;
        try {
            saved = ownerListingService.savePending(listing);
        } catch (Exception e) {
            System.out.println("Owner listing save failed for user=" + userId + ", error=" + e.getMessage());
            send(chatId, ownerListingSubmitFailedText(lang), Keyboards.ownerListingConfirmKeyboard(lang));
            return;
        }

        ownerListingDrafts.remove(userId);

        send(chatId,
                switch (lang) {
                    case RU -> "✅ Объявление отправлено на проверку.\n\nПосле модерации оно сможет появиться в выдаче.";
                    case CZ -> "✅ Nabídka byla odeslána ke kontrole.\n\nPo schválení se může zobrazit ve výsledcích.";
                    case EN -> "✅ Listing sent for review.\n\nAfter approval it can appear in search results.";
                    default -> "✅ Оголошення надіслано на перевірку.\n\nПісля модерації воно зможе зʼявитися у видачі.";
                },
                Keyboards.persistentNavKeyboard(lang));

        try {
            sendOwnerListingToAdmin(saved);
        } catch (Exception e) {
            System.out.println("Owner listing admin notification failed for listing="
                    + saved.getId()
                    + ", error=" + e.getMessage());
        }
    }

    private String ownerListingSubmitFailedText(Language lang) {
        return switch (lang) {
            case RU -> "Не смог сохранить объявление. Попробуйте нажать ✅ Отправить ещё раз чуть позже.";
            case CZ -> "Nabídku se nepodařilo uložit. Zkuste prosím stisknout ✅ Odeslat znovu za chvíli.";
            case EN -> "Could not save the listing. Please press ✅ Send again in a moment.";
            default -> "Не вдалося зберегти оголошення. Спробуйте натиснути ✅ Надіслати ще раз трохи пізніше.";
        };
    }

    private String ownerListingDraftNotReadyText(Language lang) {
        return switch (lang) {
            case RU -> "Черновик не готов или уже отменён. Начните с /add_owner_listing.";
            case CZ -> "Koncept není hotový nebo už byl zrušen. Začněte pomocí /add_owner_listing.";
            case EN -> "The draft is not ready or was already cancelled. Start with /add_owner_listing.";
            default -> "Чернетка не готова або вже скасована. Почніть з /add_owner_listing.";
        };
    }

    private boolean isOwnerListingSubmitText(String text) {
        String lower = text == null ? "" : text.trim().toLowerCase();
        if (lower.equals("так")
                || lower.equals("та")
                || lower.equals("да")
                || lower.equals("отправить")
                || lower.equals("надіслати")
                || lower.equals("відправити")
                || lower.equals("відправ")
                || lower.equals("odeslat")) {
            return true;
        }

        String normalized = normalizeSearch(text);
        return normalized.equals("tak")
                || normalized.equals("ta")
                || normalized.equals("yes")
                || normalized.equals("y")
                || normalized.equals("da")
                || normalized.equals("ano")
                || normalized.equals("ok")
                || normalized.equals("send")
                || normalized.equals("submit")
                || normalized.equals("nadislat")
                || normalized.equals("vidpravyty")
                || normalized.equals("odeslat")
                || normalized.equals("otpravit");
    }

    private boolean isOwnerListingCancelText(String text) {
        String lower = text == null ? "" : text.trim().toLowerCase();
        if (lower.equals("ні")
                || lower.equals("нет")
                || lower.equals("скасувати")
                || lower.equals("отмена")
                || lower.equals("отменить")
                || lower.equals("zrušit")
                || lower.equals("zrusit")) {
            return true;
        }

        String normalized = normalizeSearch(text);
        return normalized.equals("ni")
                || normalized.equals("no")
                || normalized.equals("ne")
                || normalized.equals("net")
                || normalized.equals("cancel")
                || normalized.equals("skasuvaty")
                || normalized.equals("otmena");
    }

    private String ownerListingConfirmHelpText(Language lang) {
        return switch (lang) {
            case RU -> "Чтобы отправить объявление на проверку, нажмите ✅ Отправить или напишите Да.";
            case CZ -> "Pro odeslání nabídky ke kontrole stiskněte ✅ Odeslat nebo napište Ano.";
            case EN -> "To send the listing for review, press ✅ Send or type Yes.";
            default -> "Щоб надіслати оголошення на перевірку, натисніть ✅ Надіслати або напишіть Так.";
        };
    }

    private String ownerListingPreviewText(Language lang) {
        return switch (lang) {
            case RU -> """
                    🏠 Объявление от владельца

                    Город/округ: %s
                    Локация: %s
                    Тип: %s
                    Цена: %s
                    Название: %s
                    Описание: %s
                    Контакт: %s
                    Фото: %s

                    Отправить это объявление на проверку?
                    """;
            case CZ -> """
                    🏠 Nabídka od majitele

                    Město/okres: %s
                    Lokalita: %s
                    Typ: %s
                    Cena: %s
                    Název: %s
                    Popis: %s
                    Kontakt: %s
                    Foto: %s

                    Odeslat tuto nabídku ke kontrole?
                    """;
            case EN -> """
                    🏠 Owner listing

                    City/district: %s
                    Location: %s
                    Type: %s
                    Price: %s
                    Title: %s
                    Description: %s
                    Contact: %s
                    Photo: %s

                    Send this listing for review?
                    """;
            default -> """
                    🏠 Оголошення від власника

                    Місто/округ: %s
                    Локація: %s
                    Тип: %s
                    Ціна: %s
                    Назва: %s
                    Опис: %s
                    Контакт: %s
                    Фото: %s

                    Надіслати це оголошення на перевірку?
                    """;
        };
    }

    private String ownerListingHasPhotoText(Language lang) {
        return switch (lang) {
            case RU -> "есть";
            case CZ -> "ano";
            case EN -> "yes";
            default -> "є";
        };
    }

    private String ownerListingNoPhotoText(Language lang) {
        return switch (lang) {
            case RU -> "нет";
            case CZ -> "ne";
            case EN -> "no";
            default -> "немає";
        };
    }

    private void sendOwnerListingPreview(long chatId, OwnerListingDraft draft, Language lang) throws TelegramApiException {
        String preview = ownerListingPreviewText(lang).formatted(
                draft.region == null ? "—" : draft.region.getTitle(),
                nvl(draft.locality),
                nvl(draft.layout),
                draft.priceCzk == null ? "—" : formatPrice(draft.priceCzk),
                nvl(draft.title),
                nvl(draft.description),
                nvl(draft.contact),
                draft.photoFileId == null ? ownerListingNoPhotoText(lang) : ownerListingHasPhotoText(lang)
        );

        send(chatId, preview, Keyboards.ownerListingConfirmKeyboard(lang));
    }

    private void sendOwnerListingsList(long chatId, int limit) throws TelegramApiException {
        List<OwnerListing> listings = ownerListingService.listRecent(limit);
        if (listings.isEmpty()) {
            send(chatId, "Поки немає оголошень від власників.", Keyboards.persistentNavKeyboard(Language.UA));
            return;
        }

        send(chatId,
                "🏠 Оголошення від власників\n\nПоказую останні: " + listings.size()
                        + "\n\nКоманди:\n/admin_owner_view ID\n/admin_owner_archive ID",
                Keyboards.persistentNavKeyboard(Language.UA));

        for (OwnerListing listing : listings) {
            boolean approved = listing.getStatus() == OwnerListing.Status.APPROVED;
            send(chatId,
                    ownerListingAdminSummary(listing),
                    Keyboards.ownerListingAdminKeyboard(listing.getId(), approved));
        }
    }

    private void showOwnerListingById(long chatId, Long listingId, Language lang) throws TelegramApiException {
        if (listingId == null) {
            send(chatId, "Вкажи ID. Приклад: /admin_owner_view 7", Keyboards.persistentNavKeyboard(lang));
            return;
        }

        Optional<OwnerListing> listing = ownerListingService.findById(listingId);
        if (listing.isEmpty()) {
            send(chatId, "Оголошення не знайдено. ID: " + listingId, Keyboards.persistentNavKeyboard(lang));
            return;
        }

        sendOwnerListingAdminView(chatId, listing.get());
    }

    private void archiveOwnerListingById(long chatId, Long listingId, Language lang) throws TelegramApiException {
        if (listingId == null) {
            send(chatId, "Вкажи ID. Приклад: /admin_owner_archive 7", Keyboards.persistentNavKeyboard(lang));
            return;
        }

        Optional<OwnerListing> listing = ownerListingService.findById(listingId);
        if (listing.isEmpty()) {
            send(chatId, "Оголошення не знайдено. ID: " + listingId, Keyboards.persistentNavKeyboard(lang));
            return;
        }

        OwnerListing archived = ownerListingService.archive(listing.get());
        send(chatId,
                "🗄 Оголошення приховано.\nID: " + archived.getId()
                        + "\n\nВоно більше не потрапляє у видачу.",
                Keyboards.persistentNavKeyboard(lang));
    }

    private void sendOwnerListingAdminView(long chatId, OwnerListing listing) throws TelegramApiException {
        boolean approved = listing.getStatus() == OwnerListing.Status.APPROVED;
        String text = ownerListingAdminDetails(listing);

        if (hasUsablePhotoUrl(listing.getPhotoFileId())) {
            try {
                telegramClient.execute(
                        SendPhoto.builder()
                                .chatId(chatId)
                                .photo(new InputFile(listing.getPhotoFileId()))
                                .caption(trimCaption(text))
                                .replyMarkup(Keyboards.ownerListingAdminKeyboard(listing.getId(), approved))
                                .build()
                );
                return;
            } catch (Exception e) {
                System.out.println("Owner listing admin view photo failed for listing="
                        + listing.getId()
                        + ", fallback=text, error=" + e.getMessage());
            }
        }

        send(chatId, text, Keyboards.ownerListingAdminKeyboard(listing.getId(), approved));
    }

    private String ownerListingAdminSummary(OwnerListing listing) {
        return """
                🏠 Оголошення від власника

                ID: %d
                Статус: %s
                Регіон пошуку: %s
                Локація: %s
                Тип: %s
                Ціна: %s
                Назва: %s
                """.formatted(
                listing.getId(),
                ownerListingStatusLabel(listing),
                listing.getRegion() == null ? "—" : listing.getRegion().getTitle(),
                nvl(listing.getLocality()),
                nvl(listing.getLayout()),
                listing.getPriceCzk() == null ? "—" : formatPrice(listing.getPriceCzk()),
                nvl(listing.getTitle())
        );
    }

    private String ownerListingAdminDetails(OwnerListing listing) {
        return """
                🏠 Оголошення від власника

                ID: %d
                Статус: %s
                Автор: %s
                Регіон пошуку: %s
                Локація: %s
                Тип: %s
                Ціна: %s
                Назва: %s
                Опис: %s
                Контакт: %s
                Фото: %s
                """.formatted(
                listing.getId(),
                ownerListingStatusLabel(listing),
                ownerListingAuthorLabel(listing),
                listing.getRegion() == null ? "—" : listing.getRegion().getTitle(),
                nvl(listing.getLocality()),
                nvl(listing.getLayout()),
                listing.getPriceCzk() == null ? "—" : formatPrice(listing.getPriceCzk()),
                nvl(listing.getTitle()),
                nvl(listing.getDescription()),
                nvl(listing.getContact()),
                hasUsablePhotoUrl(listing.getPhotoFileId()) ? "є" : "немає"
        );
    }

    private String ownerListingStatusLabel(OwnerListing listing) {
        if (listing.getStatus() == OwnerListing.Status.APPROVED) {
            return "опубліковано";
        }
        if (listing.getApprovedAt() == null) {
            return "очікує модерації";
        }
        return "приховано";
    }

    private String ownerListingAuthorLabel(OwnerListing listing) {
        if (listing.getCreatedByUsername() == null || listing.getCreatedByUsername().isBlank()) {
            return String.valueOf(listing.getCreatedByTelegramId());
        }
        return "@" + listing.getCreatedByUsername() + " / " + listing.getCreatedByTelegramId();
    }

    private void sendOwnerListingToAdmin(OwnerListing listing) throws TelegramApiException {
        String author = listing.getCreatedByUsername() == null || listing.getCreatedByUsername().isBlank()
                ? String.valueOf(listing.getCreatedByTelegramId())
                : "@" + listing.getCreatedByUsername() + " / " + listing.getCreatedByTelegramId();

        String text = """
                🏠 Нова заявка: житло від власника

                ID: %d
                Автор: %s
                Регіон пошуку: %s
                Локація: %s
                Тип: %s
                Ціна: %s
                Назва: %s
                Опис: %s
                Контакт: %s

                Опублікувати оголошення?
                """.formatted(
                listing.getId(),
                author,
                listing.getRegion() == null ? "—" : listing.getRegion().getTitle(),
                nvl(listing.getLocality()),
                nvl(listing.getLayout()),
                listing.getPriceCzk() == null ? "—" : formatPrice(listing.getPriceCzk()),
                nvl(listing.getTitle()),
                nvl(listing.getDescription()),
                nvl(listing.getContact())
        );

        if (hasUsablePhotoUrl(listing.getPhotoFileId())) {
            try {
                telegramClient.execute(
                        SendPhoto.builder()
                                .chatId(adminId)
                                .photo(new InputFile(listing.getPhotoFileId()))
                                .caption(trimCaption(text))
                                .replyMarkup(Keyboards.ownerListingModerationKeyboard(listing.getId()))
                                .build()
                );
                return;
            } catch (Exception e) {
                System.out.println("Owner listing photo notification failed for listing="
                        + listing.getId()
                        + ", fallback=text, error=" + e.getMessage());
            }
        }

        send(adminId, text, Keyboards.ownerListingModerationKeyboard(listing.getId()));
    }

    private void notifyOwnerListingAuthor(OwnerListing listing, boolean approved) {
        if (listing.getCreatedByTelegramId() == null) {
            return;
        }

        Language lang = getUserLanguage(listing.getCreatedByTelegramId());
        String text = approved
                ? switch (lang) {
                    case RU -> "✅ Ваше объявление опубликовано и теперь может появляться в выдаче.";
                    case CZ -> "✅ Vaše nabídka byla zveřejněna a může se zobrazovat ve výsledcích.";
                    case EN -> "✅ Your listing has been published and can now appear in search results.";
                    default -> "✅ Ваше оголошення опубліковано і тепер може зʼявлятися у видачі.";
                }
                : switch (lang) {
                    case RU -> "❌ Ваше объявление не было опубликовано после проверки.";
                    case CZ -> "❌ Vaše nabídka nebyla po kontrole zveřejněna.";
                    case EN -> "❌ Your listing was not published after review.";
                    default -> "❌ Ваше оголошення не було опубліковано після перевірки.";
                };

        try {
            send(listing.getCreatedByTelegramId(), text, Keyboards.persistentNavKeyboard(lang));
        } catch (Exception e) {
            System.out.println("Owner listing author notification failed for listing="
                    + listing.getId()
                    + ", user=" + listing.getCreatedByTelegramId()
                    + ", error=" + e.getMessage());
        }
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

        if (data.equals("OWNER:SUBMIT")) {
            OwnerListingDraft draft = ownerListingDrafts.get(userId);
            if (draft == null || !draft.readyToPublish()) {
                send(chatId, ownerListingDraftNotReadyText(lang), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            submitOwnerListingDraft(chatId, userId, draft, lang);
            return;
        }

        if (data.equals("OWNER:CANCEL")) {
            ownerListingDrafts.remove(userId);
            send(chatId, ownerListingCancelledText(lang), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (data.startsWith("OWNER:APPROVE:")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            Long listingId = parseLongOrNull(data.substring("OWNER:APPROVE:".length()));
            Optional<OwnerListing> pending = listingId == null
                    ? Optional.empty()
                    : ownerListingService.findPending(listingId);

            if (pending.isEmpty()) {
                send(chatId, "Заявку не знайдено або вона вже оброблена.", Keyboards.persistentNavKeyboard(lang));
                return;
            }

            OwnerListing approved = ownerListingService.approve(pending.get());
            send(chatId,
                    "✅ Оголошення опубліковане.\nID: " + approved.getId()
                            + "\n\nВоно тепер бере участь у фільтрах як джерело «Власник».",
                    Keyboards.persistentNavKeyboard(lang));
            notifyOwnerListingAuthor(approved, true);
            return;
        }

        if (data.startsWith("OWNER:REJECT:")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            Long listingId = parseLongOrNull(data.substring("OWNER:REJECT:".length()));
            Optional<OwnerListing> pending = listingId == null
                    ? Optional.empty()
                    : ownerListingService.findPending(listingId);

            if (pending.isEmpty()) {
                send(chatId, "Заявку не знайдено або вона вже оброблена.", Keyboards.persistentNavKeyboard(lang));
                return;
            }

            OwnerListing archived = ownerListingService.archive(pending.get());
            send(chatId,
                    "❌ Оголошення відхилене / відправлене в архів.\nID: " + archived.getId(),
                    Keyboards.persistentNavKeyboard(lang));
            notifyOwnerListingAuthor(archived, false);
            return;
        }

        if (data.startsWith("OWNER:VIEW:")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            Long listingId = parseLongOrNull(data.substring("OWNER:VIEW:".length()));
            showOwnerListingById(chatId, listingId, lang);
            return;
        }

        if (data.startsWith("OWNER:ARCHIVE:")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            Long listingId = parseLongOrNull(data.substring("OWNER:ARCHIVE:".length()));
            archiveOwnerListingById(chatId, listingId, lang);
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

        if (data.equals("STOP:CONFIRM")) {
            filterEditMode.remove(userId);
            f.setActive(false);
            flowService.save(f);

            UserFilter fullFilter = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> f);
            send(chatId,
                    msg(userId, "search.stopped") + "\n\n" + flowService.pretty(fullFilter, lang),
                    Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (data.equals("STOP:CANCEL")) {
            UserFilter fullFilter = userFilterRepo.findFullById(userId)
                    .orElseGet(() -> f);
            send(chatId, flowService.pretty(fullFilter, lang), Keyboards.filterActionsKeyboard(lang));
            return;
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
                    send(chatId,
                            switch (lang) {
                                case RU -> "Остановить автоматический поиск и уведомления?";
                                case CZ -> "Zastavit automatické hledání a upozornění?";
                                case EN -> "Stop automatic search and notifications?";
                                default -> "Зупинити автоматичний пошук і сповіщення?";
                            },
                            Keyboards.stopConfirmationKeyboard(lang));
                }

                default -> send(chatId, msg(userId, "menu.unknown.action"), Keyboards.mainMenuKeyboard(lang));
            }

            return;
        }

        if (data.startsWith("SERVICE:NO_AGENT")) {
            send(chatId, premiumInfo(lang), Keyboards.authorContactKeyboard(lang));
            return;
        }

        if (data.startsWith("SERVICE:OWNER_LISTING")) {
            startOwnerListingDraft(chatId, userId, update.getCallbackQuery().getFrom().getUserName(), lang);
            return;
        }

        if (data.equals("SERVICE:SUPPORT")) {
            send(chatId, msg(userId, "support.text"), Keyboards.supportKeyboard(lang));
            return;
        }

        if (data.equals("SUPPORT:RAIFFEISEN")) {
            send(chatId, raiffeisenSupportInfo(lang), Keyboards.supportKeyboard(lang));
            return;
        }

        if (data.equals("SUPPORT:BACK")) {
            send(chatId, switch (lang) {
                case RU -> "Другие полезные сервисы:";
                case CZ -> "Další užitečné služby:";
                case EN -> "Other useful services:";
                default -> "Інші корисні сервіси:";
            }, Keyboards.servicesInlineKeyboard(lang));
            return;
        }

        if (data.startsWith("SERVICE:DP_DOCUMENT")) {
            send(chatId, dpDocumentInfo(lang), Keyboards.dpDocumentKeyboard(lang));
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

    private ReactivationResult sendMilestone1500Messages(int limit) {
        ReactivationResult result = new ReactivationResult();
        Instant now = Instant.now();

        List<UserFilter> candidates = userFilterRepo.findMilestone1500Candidates(PageRequest.of(0, limit));
        result.checked = candidates.size();

        for (UserFilter user : candidates) {
            if (user.getTelegramUserId() == null) {
                result.skipped++;
                continue;
            }

            Language userLang = user.getLanguage() != null ? user.getLanguage() : Language.UA;

            try {
                send(user.getTelegramUserId(),
                        milestone1500Text(userLang),
                        Keyboards.milestone1500Keyboard(userLang));

                user.setMilestone1500SentAt(now);
                userFilterRepo.save(user);
                result.sent++;

            } catch (TelegramApiException e) {
                if (isUnreachableTelegramUser(e.getMessage())) {
                    user.setActive(false);
                    userFilterRepo.save(user);
                    result.deactivated++;
                } else {
                    result.failed++;
                    System.out.println("Milestone 1500 message failed for user="
                            + user.getTelegramUserId()
                            + ", error=" + e.getMessage());
                }
            } catch (Exception e) {
                result.failed++;
                System.out.println("Unexpected milestone 1500 failure for user="
                        + user.getTelegramUserId()
                        + ", error=" + e.getMessage());
            }
        }

        return result;
    }

    private String milestone1500Text(Language lang) {
        return switch (lang) {
            case RU -> """
                    🎉 Нас уже 1500 в Zhytlo CZ!

                    Спасибо, что пользуетесь ботом для поиска жилья в Чехии.

                    Если бот помогает вам, поделитесь им с друзьями — возможно, кому-то это тоже сэкономит время.

                    А если хотите поддержать развитие проекта, можно сделать это кнопкой ниже 💙
                    """;
            case CZ -> """
                    🎉 V Zhytlo CZ je nás už 1500!

                    Děkuji, že používáte bot pro hledání bydlení v Česku.

                    Pokud vám bot pomáhá, sdílejte ho s přáteli — možná někomu také ušetří čas.

                    Pokud chcete podpořit rozvoj projektu, můžete to udělat tlačítkem níže 💙
                    """;
            case EN -> """
                    🎉 There are already 1500 of us in Zhytlo CZ!

                    Thank you for using the bot to search for housing in Czechia.

                    If the bot helps you, share it with friends — it may save someone time too.

                    If you want to support the project, you can do it with the button below 💙
                    """;
            default -> """
                    🎉 Нас уже 1500 у Zhytlo CZ!

                    Дякую, що користуєтесь ботом для пошуку житла в Чехії.

                    Якщо бот допомагає вам, поділіться ним з друзями — можливо, комусь це теж зекономить час.

                    А якщо хочете підтримати розвиток проєкту, можете зробити це кнопкою нижче 💙
                    """;
        };
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

    private Long parseAdminIdArgument(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }

        return parseLongOrNull(digits);
    }

    private Long parseLongOrNull(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return null;
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

    private String raiffeisenSupportInfo(Language lang) {
        return switch (lang) {
            case RU -> """
                    💳 Raiffeisenbank

                    Счёт: 972026002/5500

                    Откройте приложение своего банка, выберите платёж по реквизитам и укажите этот счёт.""";
            case CZ -> """
                    💳 Raiffeisenbank

                    Účet: 972026002/5500

                    Otevřete aplikaci své banky, zvolte platbu na účet a zadejte tento účet.""";
            case EN -> """
                    💳 Raiffeisenbank

                    Account: 972026002/5500

                    Open your banking app, choose a bank transfer and enter this account.""";
            default -> """
                    💳 Raiffeisenbank

                    Рахунок: 972026002/5500

                    Відкрийте застосунок свого банку, оберіть платіж за реквізитами та вкажіть цей рахунок.""";
        };
    }

    private String premiumInfo(Language lang) {
        return switch (lang) {
            case RU -> """
💎 Премиум

Для тех, кто хочет искать жильё активнее и получать больше подходящих вариантов.

Цена раннего доступа: 99 Kč / месяц.

Что будет в Премиум:

✅ несколько поисков одновременно
Например: Praha 1+kk, Brno 2+kk и Plzeň комната.

🔎 больше вариантов в выдаче
Больше подходящих предложений из Sreality, iDNES, Bezrealitky и Bazoš.

🏠 больше вариантов без риелтора
Отдельный акцент на объявления от собственников и Bezrealitky.

⚡ приоритет в новых функциях
Первые пользователи помогают выбрать, что добавить дальше.

Премиум запускается в тестовом режиме. Чтобы подключить — напишите автору.
""";
            case CZ -> """
💎 Premium

Pro ty, kteří chtějí hledat bydlení aktivněji a dostávat více relevantních nabídek.

Cena předběžného přístupu: 99 Kč / měsíc.

Co bude v Premium:

✅ více hledání najednou
Například: Praha 1+kk, Brno 2+kk a Plzeň pokoj.

🔎 více nabídek ve výsledcích
Více relevantních nabídek ze Sreality, iDNES, Bezrealitky a Bazoš.

🏠 více nabídek bez realitky
Větší důraz na nabídky od majitelů a Bezrealitky.

⚡ priorita u nových funkcí
První uživatelé pomohou vybrat, co přidat dál.

Premium se spouští v testovacím režimu. Pro aktivaci napište autorovi.
""";
            case EN -> """
💎 Premium

For people who want to search more actively and get more relevant listings.

Early access price: 99 Kč / month.

What Premium will include:

✅ multiple searches at once
For example: Praha 1+kk, Brno 2+kk and Plzeň room.

🔎 more listings in results
More relevant listings from Sreality, iDNES, Bezrealitky and Bazoš.

🏠 more no-agent options
More focus on owner listings and Bezrealitky.

⚡ priority for new features
Early users help decide what to add next.

Premium is launching in test mode. To activate it, contact the author.
""";
            default -> """
💎 Преміум

Для тих, хто хоче шукати житло активніше й отримувати більше відповідних варіантів.

Ціна раннього доступу: 99 Kč / місяць.

Що буде в Преміум:

✅ кілька пошуків одночасно
Наприклад: Praha 1+kk, Brno 2+kk і Plzeň кімната.

🔎 більше варіантів у видачі
Більше відповідних пропозицій із Sreality, iDNES, Bezrealitky та Bazoš.

🏠 більше варіантів без рієлтора
Більший акцент на оголошеннях від власників і Bezrealitky.

⚡ пріоритет у нових функціях
Перші користувачі допомагають обрати, що додати далі.

Преміум запускається в тестовому режимі. Щоб підключити — напишіть автору.
""";
        };
    }

    private String dpDocumentInfo(Language lang) {
        return switch (lang) {
            case RU -> """
🇺🇦 Запись в ДП Документ Прага

Неофициальный канал, где отслеживают электронную очередь и доступные места для записи в центре ДП «Документ» в Праге и других городах.

Проверяйте информацию самостоятельно — бот только делится полезным источником.
""";
            case CZ -> """
🇺🇦 Rezervace DP Dokument Praha

Neoficiální kanál, kde sledují elektronickou frontu a volná místa pro rezervaci v centru DP „Dokument“ v Praze a dalších městech.

Informace si prosím ověřujte sami — bot pouze sdílí užitečný zdroj.
""";
            case EN -> """
🇺🇦 DP Document Prague appointments

An unofficial channel that tracks the electronic queue and available appointment slots at the DP Document center in Prague and other cities.

Please verify the information yourself — the bot only shares a useful source.
""";
            default -> """
🇺🇦 Запис у ДП Документ Прага

Неофіційний канал, де відстежують електронну чергу та доступні місця для запису в центрі ДП «Документ» у Празі та інших містах.

Перевіряйте інформацію самостійно — бот лише ділиться корисним джерелом.
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
                        "🏷 " + msg(userId, "listing.source") + ": " + displaySource(l.source(), lang) + "\n" +
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
                        "🏷 " + msg(userId, "listing.source") + ": " + displaySource(fav.getSource(), lang) + "\n" +
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

    private String displaySource(String source, Language lang) {
        if (source == null || source.isBlank()) {
            return "—";
        }

        String normalized = source.trim().toLowerCase();
        if (normalized.equals("owner") || normalized.equals("власник")) {
            return switch (lang) {
                case RU -> "Владелец";
                case CZ -> "Majitel";
                case EN -> "Owner";
                default -> "Власник";
            };
        }

        return source;
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
                        "🏷 " + msg(userId, "listing.source") + ": " + displaySource(l.source(), lang) + "\n\n" +
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
        private String createdByUsername;

        private boolean readyToPublish() {
            return step == Step.CONFIRM
                    && region != null
                    && locality != null
                    && layout != null
                    && priceCzk != null
                    && title != null
                    && contact != null
                    && photoFileId != null;
        }

        private String stepLabel(Language lang) {
            return switch (step) {
                case CITY -> switch (lang) {
                    case RU -> "город";
                    case CZ -> "město";
                    case EN -> "city";
                    default -> "місто";
                };
                case LOCALITY -> switch (lang) {
                    case RU -> "локация";
                    case CZ -> "lokalita";
                    case EN -> "location";
                    default -> "локація";
                };
                case LAYOUT -> switch (lang) {
                    case RU -> "тип жилья";
                    case CZ -> "typ bydlení";
                    case EN -> "housing type";
                    default -> "тип житла";
                };
                case PRICE -> switch (lang) {
                    case RU -> "цена";
                    case CZ -> "cena";
                    case EN -> "price";
                    default -> "ціна";
                };
                case TITLE -> switch (lang) {
                    case RU -> "название";
                    case CZ -> "název";
                    case EN -> "title";
                    default -> "назва";
                };
                case DESCRIPTION -> switch (lang) {
                    case RU -> "описание";
                    case CZ -> "popis";
                    case EN -> "description";
                    default -> "опис";
                };
                case CONTACT -> switch (lang) {
                    case RU -> "контакт";
                    case CZ -> "kontakt";
                    case EN -> "contact";
                    default -> "контакт";
                };
                case PHOTO -> switch (lang) {
                    case RU -> "фото";
                    case CZ -> "foto";
                    case EN -> "photo";
                    default -> "фото";
                };
                case CONFIRM -> switch (lang) {
                    case RU -> "подтверждение";
                    case CZ -> "potvrzení";
                    case EN -> "confirmation";
                    default -> "підтвердження";
                };
            };
        }
    }
}
