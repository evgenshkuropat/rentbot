package com.yourapp.rentbot.bot;

import com.yourapp.rentbot.domain.FavoriteListing;
import com.yourapp.rentbot.domain.OwnerListing;
import com.yourapp.rentbot.domain.PremiumSearch;
import com.yourapp.rentbot.domain.PremiumPaymentRequest;
import com.yourapp.rentbot.domain.PremiumEvent;
import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.RegionGroup;
import com.yourapp.rentbot.domain.ReactivationEvent;
import com.yourapp.rentbot.domain.SearchStatusEvent;
import com.yourapp.rentbot.domain.SupportEvent;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.flow.FlowService;
import com.yourapp.rentbot.flow.FlowStep;
import com.yourapp.rentbot.i18n.Language;
import com.yourapp.rentbot.i18n.MessageService;
import com.yourapp.rentbot.repo.RegionGroupRepo;
import com.yourapp.rentbot.repo.RegionRepo;
import com.yourapp.rentbot.repo.SentLogRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.FavoriteService;
import com.yourapp.rentbot.service.ListingCacheService;
import com.yourapp.rentbot.service.NotificationService;
import com.yourapp.rentbot.service.OwnerListingService;
import com.yourapp.rentbot.service.OwnerListingAdminMessages;
import com.yourapp.rentbot.service.OwnerListingModerationService;
import com.yourapp.rentbot.service.ParserService;
import com.yourapp.rentbot.service.PremiumService;
import com.yourapp.rentbot.service.PremiumPaymentService;
import com.yourapp.rentbot.service.PremiumMetricsService;
import com.yourapp.rentbot.service.PremiumViewService;
import com.yourapp.rentbot.service.ReactivationMetricsService;
import com.yourapp.rentbot.service.SearchStatusMetricsService;
import com.yourapp.rentbot.service.SchedulerService;
import com.yourapp.rentbot.service.SupportMetricsService;
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

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final SentLogRepo sentLogRepo;
    private final UserFilterRepo userFilterRepo;
    private final ParserService parserService;
    private final SchedulerService schedulerService;
    private final NotificationService notificationService;
    private final OwnerListingService ownerListingService;
    private final OwnerListingAdminMessages ownerListingAdminMessages;
    private final OwnerListingModerationService ownerListingModerationService;
    private final OwnerListingFormHandler ownerListingFormHandler;
    private final FavoriteService favoriteService;
    private final ListingCacheService listingCacheService;
    private final MessageService messageService;
    private final SupportMetricsService supportMetricsService;
    private final PremiumService premiumService;
    private final PremiumPaymentService premiumPaymentService;
    private final PremiumMetricsService premiumMetricsService;
    private final PremiumViewService premiumViewService;
    private final ReactivationMetricsService reactivationMetricsService;
    private final SearchStatusMetricsService searchStatusMetricsService;

    private final String token;
    private final long adminId;
    private final boolean milestone1500AutoEnabled;
    private final int milestone1500AutoBatchSize;
    private final boolean inactiveReactivationAutoEnabled;
    private final int inactiveReactivationAutoBatchSize;
    private final boolean searchStatusAutoEnabled;
    private final int searchStatusAutoBatchSize;
    private final boolean premiumExpiryReminderAutoEnabled;
    private final boolean premiumSecondSearchReminderAutoEnabled;
    private final int supportMonthlyGoalCzk;
    private final AtomicBoolean milestone1500AutoRunning = new AtomicBoolean(false);
    private final AtomicBoolean inactiveReactivationAutoRunning = new AtomicBoolean(false);
    private final AtomicBoolean searchStatusAutoRunning = new AtomicBoolean(false);
    private final AtomicBoolean premiumExpiryReminderAutoRunning = new AtomicBoolean(false);
    private final AtomicBoolean premiumSecondSearchReminderAutoRunning = new AtomicBoolean(false);

    private static final long INTERACTION_CACHE_TTL_MILLIS = 6 * 60 * 60 * 1000L;

    private final Map<Integer, String> favoriteLinkCache = new HashMap<>();
    private final Map<Integer, Long> favoriteLinkCacheAt = new HashMap<>();
    private final Map<Long, List<ListingDto>> searchCache = new HashMap<>();
    private final Map<Long, Long> searchCacheAt = new HashMap<>();
    private final Map<Long, Integer> searchOffset = new HashMap<>();
    private final Map<Long, Integer> searchCurrentIndex = new HashMap<>();
    private final Map<Long, String> filterEditMode = new HashMap<>();
    private static final int PAGE_SIZE = 10;
    private static final String EDIT_CITY = "CITY";
    private static final String EDIT_DISTRICT = "DISTRICT";
    private static final String EDIT_LAYOUT = "LAYOUT";

    public RentBot(
            @Value("${telegram.bot.token}") String token,
            @Value("${TELEGRAM_ADMIN_ID}") long adminId,
            TelegramClient telegramClient,
            FlowService flowService,
            RegionRepo regionRepo,
            RegionGroupRepo regionGroupRepo,
            SentLogRepo sentLogRepo,
            UserFilterRepo userFilterRepo,
            ParserService parserService,
            SchedulerService schedulerService,
            NotificationService notificationService,
            OwnerListingService ownerListingService,
            OwnerListingAdminMessages ownerListingAdminMessages,
            OwnerListingModerationService ownerListingModerationService,
            OwnerListingFormHandler ownerListingFormHandler,
            FavoriteService favoriteService,
            ListingCacheService listingCacheService,
            MessageService messageService,
            SupportMetricsService supportMetricsService,
            PremiumService premiumService,
            PremiumPaymentService premiumPaymentService,
            PremiumMetricsService premiumMetricsService,
            PremiumViewService premiumViewService,
            ReactivationMetricsService reactivationMetricsService,
            SearchStatusMetricsService searchStatusMetricsService,
            @Value("${rentbot.milestone1500.auto-enabled:false}") boolean milestone1500AutoEnabled,
            @Value("${rentbot.milestone1500.auto-batch-size:25}") int milestone1500AutoBatchSize,
            @Value("${rentbot.reactivation.inactive.auto-enabled:false}") boolean inactiveReactivationAutoEnabled,
            @Value("${rentbot.reactivation.inactive.auto-batch-size:50}") int inactiveReactivationAutoBatchSize,
            @Value("${rentbot.search-status.auto-enabled:false}") boolean searchStatusAutoEnabled,
            @Value("${rentbot.search-status.auto-batch-size:100}") int searchStatusAutoBatchSize,
            @Value("${rentbot.premium.expiry-reminder.auto-enabled:true}") boolean premiumExpiryReminderAutoEnabled,
            @Value("${rentbot.premium.second-search-reminder.auto-enabled:true}") boolean premiumSecondSearchReminderAutoEnabled,
            @Value("${rentbot.support.monthly-goal-czk:${RENTBOT_SUPPORT_MONTHLY_GOAL_CZK:800}}") int supportMonthlyGoalCzk
    ) {
        this.token = token;
        this.adminId = adminId;
        this.telegramClient = telegramClient;
        this.flowService = flowService;
        this.regionRepo = regionRepo;
        this.regionGroupRepo = regionGroupRepo;
        this.sentLogRepo = sentLogRepo;
        this.userFilterRepo = userFilterRepo;
        this.parserService = parserService;
        this.schedulerService = schedulerService;
        this.notificationService = notificationService;
        this.ownerListingService = ownerListingService;
        this.ownerListingAdminMessages = ownerListingAdminMessages;
        this.ownerListingModerationService = ownerListingModerationService;
        this.ownerListingFormHandler = ownerListingFormHandler;
        this.favoriteService = favoriteService;
        this.listingCacheService = listingCacheService;
        this.messageService = messageService;
        this.supportMetricsService = supportMetricsService;
        this.premiumService = premiumService;
        this.premiumPaymentService = premiumPaymentService;
        this.premiumMetricsService = premiumMetricsService;
        this.premiumViewService = premiumViewService;
        this.reactivationMetricsService = reactivationMetricsService;
        this.searchStatusMetricsService = searchStatusMetricsService;
        this.milestone1500AutoEnabled = milestone1500AutoEnabled;
        this.milestone1500AutoBatchSize = Math.max(1, Math.min(milestone1500AutoBatchSize, 100));
        this.inactiveReactivationAutoEnabled = inactiveReactivationAutoEnabled;
        this.inactiveReactivationAutoBatchSize = Math.max(1, Math.min(inactiveReactivationAutoBatchSize, 100));
        this.searchStatusAutoEnabled = searchStatusAutoEnabled;
        this.searchStatusAutoBatchSize = Math.max(1, Math.min(searchStatusAutoBatchSize, 100));
        this.premiumExpiryReminderAutoEnabled = premiumExpiryReminderAutoEnabled;
        this.premiumSecondSearchReminderAutoEnabled = premiumSecondSearchReminderAutoEnabled;
        this.supportMonthlyGoalCzk = Math.max(1, supportMonthlyGoalCzk);
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
    public void logAutomaticBroadcastConfig() {
        log.info(
                "Milestone 1500 auto broadcast config: enabled={}, batchSize={}",
                milestone1500AutoEnabled,
                milestone1500AutoBatchSize
        );
        log.info(
                "Inactive reactivation auto broadcast config: enabled={}, batchSize={}, schedule=19:30 Europe/Prague",
                inactiveReactivationAutoEnabled,
                inactiveReactivationAutoBatchSize
        );
        log.info(
                "Search status auto broadcast config: enabled={}, batchSize={}, schedule=18:45 Europe/Prague",
                searchStatusAutoEnabled,
                searchStatusAutoBatchSize
        );
        log.info("Premium expiry reminders: enabled={}, schedule=11:00 Europe/Prague", premiumExpiryReminderAutoEnabled);
        log.info("Premium second-search reminders: enabled={}, schedule=12:00 Europe/Prague", premiumSecondSearchReminderAutoEnabled);
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

    @Scheduled(
            cron = "${rentbot.reactivation.inactive.auto-cron:0 30 19 * * *}",
            zone = "${rentbot.reactivation.inactive.time-zone:Europe/Prague}"
    )
    public void sendInactiveReactivationAutomatically() {
        if (!inactiveReactivationAutoEnabled) {
            return;
        }

        if (!inactiveReactivationAutoRunning.compareAndSet(false, true)) {
            log.warn("Inactive reactivation auto broadcast already running, skipping...");
            return;
        }

        try {
            ReactivationResult result = sendInactiveReactivationMessages(inactiveReactivationAutoBatchSize);
            log.info(
                    "Inactive reactivation auto broadcast: checked={}, sent={}, skipped={}, deactivated={}, failed={}",
                    result.checked,
                    result.sent,
                    result.skipped,
                    result.deactivated,
                    result.failed
            );
        } catch (Exception e) {
            log.error("Inactive reactivation auto broadcast failed", e);
        } finally {
            inactiveReactivationAutoRunning.set(false);
        }
    }

    @Scheduled(
            cron = "${rentbot.search-status.auto-cron:0 45 18 * * *}",
            zone = "${rentbot.search-status.time-zone:Europe/Prague}"
    )
    public void sendSearchStatusAutomatically() {
        if (!searchStatusAutoEnabled) {
            return;
        }

        if (!searchStatusAutoRunning.compareAndSet(false, true)) {
            log.warn("Search status auto broadcast already running, skipping...");
            return;
        }

        try {
            ReactivationResult result = sendSearchStatusMessages(searchStatusAutoBatchSize);
            log.info(
                    "Search status auto broadcast: checked={}, sent={}, skipped={}, deactivated={}, failed={}",
                    result.checked, result.sent, result.skipped, result.deactivated, result.failed
            );
        } catch (Exception e) {
            log.error("Search status auto broadcast failed", e);
        } finally {
            searchStatusAutoRunning.set(false);
        }
    }

    @Scheduled(cron = "${rentbot.premium.expiry-reminder.cron:0 0 11 * * *}", zone = "${rentbot.premium.expiry-reminder.time-zone:Europe/Prague}")
    public void sendPremiumExpiryRemindersAutomatically() {
        if (!premiumExpiryReminderAutoEnabled || !premiumExpiryReminderAutoRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            Instant now = Instant.now();
            List<UserFilter> users = userFilterRepo.findPremiumExpiryReminderCandidates(now.plusSeconds(2 * 24 * 60 * 60), now.plusSeconds(3 * 24 * 60 * 60));
            int sent = 0;
            for (UserFilter user : users) {
                try {
                    Language language = getUserLanguage(user.getTelegramUserId());
                    send(user.getTelegramUserId(), premiumExpiryReminderText(language, user.getPremiumUntil()), Keyboards.premiumRenewalKeyboard(language));
                    user.setPremiumExpiryReminderSentAt(now);
                    userFilterRepo.save(user);
                    sent++;
                } catch (Exception e) {
                    log.warn("Could not send Premium expiry reminder user={}", user.getTelegramUserId(), e);
                }
            }
            log.info("Premium expiry reminders: candidates={}, sent={}", users.size(), sent);
        } finally {
            premiumExpiryReminderAutoRunning.set(false);
        }
    }

    @Scheduled(cron = "${rentbot.premium.second-search-reminder.cron:0 0 12 * * *}", zone = "${rentbot.premium.second-search-reminder.time-zone:Europe/Prague}")
    public void sendPremiumSecondSearchRemindersAutomatically() {
        if (!premiumSecondSearchReminderAutoEnabled || !premiumSecondSearchReminderAutoRunning.compareAndSet(false, true)) return;
        try {
            Instant now = Instant.now();
            List<UserFilter> candidates = userFilterRepo.findPremiumSecondSearchReminderCandidates(now, now.minusSeconds(24 * 60 * 60));
            int sent = 0;
            for (UserFilter user : candidates) {
                if (premiumService.findActiveSearch(user.getTelegramUserId()).isPresent()) continue;
                try {
                    Language language = getUserLanguage(user.getTelegramUserId());
                    send(user.getTelegramUserId(), premiumSecondSearchReminderText(language), Keyboards.premiumActiveKeyboard(language));
                    user.setPremiumSecondSearchReminderSentAt(now);
                    userFilterRepo.save(user);
                    sent++;
                } catch (Exception e) {
                    log.warn("Could not send Premium second-search reminder user={}", user.getTelegramUserId(), e);
                }
            }
            log.info("Premium second-search reminders: candidates={}, sent={}", candidates.size(), sent);
        } finally {
            premiumSecondSearchReminderAutoRunning.set(false);
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
            log.error("Unhandled Telegram update", e);
        }
    }

    private void onText(Update update) throws TelegramApiException {
        long chatId = update.getMessage().getChatId();
        long userId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText().trim();
        Language lang = getUserLanguage(userId);

        if (text.equalsIgnoreCase("/add_owner_listing")) {
            sendOwnerListingFormResult(chatId, ownerListingFormHandler.start(userId, update.getMessage().getFrom().getUserName(), lang), lang);
            return;
        }

        if (text.equalsIgnoreCase("/cancel") && ownerListingFormHandler.hasDraft(userId)) {
            sendOwnerListingFormResult(chatId, ownerListingFormHandler.cancel(userId, lang), lang);
            return;
        }

        if (ownerListingFormHandler.hasDraft(userId) && !isPersistentMenuText(text)) {
            sendOwnerListingFormResult(chatId, ownerListingFormHandler.handleText(userId, text, lang), lang);
            return;
        }

        if (ownerListingFormHandler.hasDraft(userId)) {
            ownerListingFormHandler.discard(userId);
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
            SupportMetricsService.SupportMetrics supportMetrics = supportMetricsService.since(
                    now.minus(java.time.Duration.ofDays(14))
            );
            ReactivationMetricsService.ReactivationMetrics reactivationMetrics = reactivationMetricsService.since(
                    now.minus(java.time.Duration.ofDays(30))
            );
            SearchStatusMetricsService.SearchStatusMetrics searchStatusMetrics = searchStatusMetricsService.since(
                    now.minus(java.time.Duration.ofDays(14))
            );
            PremiumMetricsService.PremiumMetrics premiumMetrics = premiumMetricsService.since(
                    now.minus(java.time.Duration.ofDays(30)),
                    now,
                    now.plus(java.time.Duration.ofDays(7))
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
                    - runStats.filteredBaseBazos()
                    - runStats.filteredBaseDigireality();
            int finalOther = runStats.finalFiltered()
                    - runStats.finalSreality()
                    - runStats.finalIdnes()
                    - runStats.finalBezrealitky()
                    - runStats.finalBazos()
                    - runStats.finalDigireality();

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

💙 Підтримка за 14 днів:
Відкрили екран: %d (%d користувачів)
Обрали спосіб: Raiffeisen %d · PrivatBank %d · PayPal %d · Revolut %d

🔄 Повернення неактивних за 30 днів:
Надіслано: %d (%d користувачів)
Натиснули: %d (%d користувачів)
Відновили пошук: %d (%d користувачів)

🔎 Статус пошуку за 14 днів:
Надіслано: %d (%d користувачів)
Переглянули пошук: %d (%d користувачів)
Відкрили зміну параметрів: %d (%d користувачів)

💎 Premium за 30 днів:
Відкрили: %d · Контекстна пропозиція: %d / натиснули: %d
Обрали оплату: %d · Подали заявку: %d
Підтверджено: %d · Відхилено: %d
Активний зараз: %d · Закінчується за 7 днів: %d · Другий пошук налаштовано: %d

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
DigiReality owners raw: %d

🔁 Після дедуплікації:
By link: %d
By signature: %d

🧪 Останній повний цикл до diversify:
Всього: %d
Sreality: %d
iDNES: %d
Bezrealitky: %d
Bazoš: %d
DigiReality owners: %d
Власник — попадань у підбірки: %d

🎯 Останній повний цикл у фінальній видачі:
Всього: %d
Sreality: %d
iDNES: %d
Bezrealitky: %d
Bazoš: %d
DigiReality owners: %d
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
                            supportMetrics.opened(),
                            supportMetrics.uniqueOpenedUsers(),
                            supportMetrics.raiffeisen(),
                            supportMetrics.privatBank(),
                            supportMetrics.paypal(),
                            supportMetrics.revolut(),
                            reactivationMetrics.sent(),
                            reactivationMetrics.uniqueSentUsers(),
                            reactivationMetrics.clicked(),
                            reactivationMetrics.uniqueClickedUsers(),
                            reactivationMetrics.activated(),
                            reactivationMetrics.uniqueActivatedUsers(),
                            searchStatusMetrics.sent(),
                            searchStatusMetrics.uniqueSentUsers(),
                            searchStatusMetrics.viewed(),
                            searchStatusMetrics.uniqueViewedUsers(),
                            searchStatusMetrics.editOpened(),
                            searchStatusMetrics.uniqueEditOpenedUsers(),
                            premiumMetrics.opened(),
                            premiumMetrics.contextualOfferShown(),
                            premiumMetrics.contextualOfferClicked(),
                            premiumMetrics.paymentMethodSelected(),
                            premiumMetrics.requestSubmitted(),
                            premiumMetrics.approved(),
                            premiumMetrics.rejected(),
                            premiumMetrics.activeNow(),
                            premiumMetrics.expiringWithin7Days(),
                            premiumMetrics.secondSearchConfigured(),
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
                            runStats.digirealityRaw(),
                            runStats.afterDedupeByLink(),
                            runStats.afterDedupeBySignature(),

                            runStats.filteredBaseTotal(),
                            runStats.filteredBaseSreality(),
                            runStats.filteredBaseIdnes(),
                            runStats.filteredBaseBezrealitky(),
                            runStats.filteredBaseBazos(),
                            runStats.filteredBaseDigireality(),
                            filteredBaseOther,

                            runStats.finalFiltered(),
                            runStats.finalSreality(),
                            runStats.finalIdnes(),
                            runStats.finalBezrealitky(),
                            runStats.finalBazos(),
                            runStats.finalDigireality(),
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

        if (text.toLowerCase().startsWith("/admin_premium_status")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            Long targetUserId = parseAdminIdArgument(text);
            if (targetUserId == null) {
                send(chatId,
                        "Вкажи Telegram ID. Приклад: /admin_premium_status 123456789",
                        Keyboards.persistentNavKeyboard(lang));
                return;
            }

            send(chatId, premiumStatusText(targetUserId), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (text.toLowerCase().startsWith("/admin_premium_payments")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            List<PremiumPaymentRequest> pending = premiumPaymentService.pendingRequests();
            List<PremiumPaymentRequest> recent = premiumPaymentService.recentRequests();
            send(chatId, premiumPaymentsText(pending, recent), Keyboards.persistentNavKeyboard(lang));
            for (PremiumPaymentRequest request : pending) {
                send(chatId, premiumPaymentRequestText(request), Keyboards.premiumPaymentAdminKeyboard(request.getId()));
            }
            return;
        }

        if (text.equalsIgnoreCase("/admin_health")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }
            send(chatId, healthStatusText(), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (text.toLowerCase().startsWith("/admin_reactivate_inactive")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            int limit = parseAdminLimit(text, 50, 100);
            ReactivationResult result = sendInactiveReactivationMessages(limit);

            send(chatId,
                    """
                    🔄 Inactive-user reactivation finished

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

        if (text.toLowerCase().startsWith("/admin_search_status")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            int limit = parseAdminLimit(text, 20, 100);
            ReactivationResult result = sendSearchStatusMessages(limit);
            send(chatId,
                    """
                    🔎 Search status messages finished

                    Candidates checked: %d
                    Sent: %d
                    Skipped (recent listing): %d
                    Deactivated: %d
                    Failed: %d
                    """.formatted(result.checked, result.sent, result.skipped, result.deactivated, result.failed),
                    Keyboards.persistentNavKeyboard(lang));
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
            showSearches(chatId, f, lang);
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
            sendOwnerListingFormResult(chatId, ownerListingFormHandler.start(userId, update.getMessage().getFrom().getUserName(), lang), lang);
            return;
        }

        if (text.equals("💎 Преміум")
                || text.equals("💎 Премиум")
                || text.equals("💎 Premium")) {
            showPremium(chatId, userId, flowService.getOrCreate(userId), lang);
            return;
        }

        if (text.equals(msg(userId, "menu.support.project"))
                || text.equals("💙 Підтримати бота")
                || text.equals("💙 Поддержать бота")
                || text.equals("💙 Podpořit bota")
                || text.equals("💙 Support the bot")) {
            showSupport(chatId, userId, lang);
            return;
        }

        if (text.equalsIgnoreCase("/menu")) {
            send(chatId, msg(userId, "menu.title"), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (text.equalsIgnoreCase("/start")) {
            UserFilter f = flowService.getOrCreate(userId);

            if (!f.isOnboarded()) {
                sendAutumnBanner(chatId);
            }

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
                log.error("Manual listing check failed for user={}", userId, e);
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
                log.error("Test listing check failed for user={}", userId, e);
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
                || text.equals("💙 Підтримати бота")
                || text.equals("💙 Поддержать бота")
                || text.equals("💙 Podpořit bota")
                || text.equals("💙 Support the bot")
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

    private void onPhoto(Update update) throws TelegramApiException {
        long chatId = update.getMessage().getChatId();
        long userId = update.getMessage().getFrom().getId();
        Language lang = getUserLanguage(userId);
        if (!ownerListingFormHandler.hasDraft(userId)) {
            return;
        }
        List<PhotoSize> photos = update.getMessage().getPhoto();
        String photoFileId = photos == null || photos.isEmpty() ? null : photos.get(photos.size() - 1).getFileId();
        sendOwnerListingFormResult(chatId, ownerListingFormHandler.handlePhoto(userId, photoFileId, lang), lang);
    }

    private void sendOwnerListingFormResult(long chatId,
                                            OwnerListingFormHandler.Result result,
                                            Language lang) throws TelegramApiException {
        if (result == null) {
            return;
        }
        send(chatId, result.text(), result.keyboard() == OwnerListingFormHandler.ReplyKeyboard.CONFIRM
                ? Keyboards.ownerListingConfirmKeyboard(lang)
                : Keyboards.persistentNavKeyboard(lang));
        if (result.submittedListing() != null) {
            try {
                sendOwnerListingToAdmin(result.submittedListing());
            } catch (Exception e) {
                log.warn("Owner listing admin notification failed for listing={}", result.submittedListing().getId(), e);
            }
        }
    }

    private void sendOwnerListingsList(long chatId, int limit) throws TelegramApiException {
        List<OwnerListing> listings = ownerListingService.listRecent(limit);
        if (listings.isEmpty()) {
            send(chatId, ownerListingAdminMessages.emptyList(), Keyboards.persistentNavKeyboard(Language.UA));
            return;
        }

        send(chatId,
                ownerListingAdminMessages.listHeader(listings.size()),
                Keyboards.persistentNavKeyboard(Language.UA));

        for (OwnerListing listing : listings) {
            boolean approved = listing.getStatus() == OwnerListing.Status.APPROVED;
            send(chatId,
                    ownerListingAdminMessages.summary(listing),
                    Keyboards.ownerListingAdminKeyboard(listing.getId(), approved));
        }
    }

    private void showOwnerListingById(long chatId, Long listingId, Language lang) throws TelegramApiException {
        if (listingId == null) {
            send(chatId, ownerListingAdminMessages.idRequired("admin_owner_view"), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        Optional<OwnerListing> listing = ownerListingService.findById(listingId);
        if (listing.isEmpty()) {
            send(chatId, ownerListingAdminMessages.notFound(listingId), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        sendOwnerListingAdminView(chatId, listing.get());
    }

    private void archiveOwnerListingById(long chatId, Long listingId, Language lang) throws TelegramApiException {
        if (listingId == null) {
            send(chatId, ownerListingAdminMessages.idRequired("admin_owner_archive"), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        Optional<OwnerListing> archived = ownerListingModerationService.archive(listingId);
        if (archived.isEmpty()) {
            send(chatId, ownerListingAdminMessages.notFound(listingId), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        send(chatId, ownerListingAdminMessages.archived(archived.get()), Keyboards.persistentNavKeyboard(lang));
    }

    private void sendOwnerListingAdminView(long chatId, OwnerListing listing) throws TelegramApiException {
        boolean approved = listing.getStatus() == OwnerListing.Status.APPROVED;
        String text = ownerListingAdminMessages.details(listing);

        if (ownerListingAdminMessages.hasPhoto(listing)) {
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
                log.warn("Owner listing admin view photo failed, falling back to text: listing={}", listing.getId(), e);
            }
        }

        send(chatId, text, Keyboards.ownerListingAdminKeyboard(listing.getId(), approved));
    }

    private void sendOwnerListingToAdmin(OwnerListing listing) throws TelegramApiException {
        String text = ownerListingAdminMessages.moderationRequest(listing);

        if (ownerListingAdminMessages.hasPhoto(listing)) {
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
                log.warn("Owner listing photo notification failed, falling back to text: listing={}", listing.getId(), e);
            }
        }

        send(adminId, text, Keyboards.ownerListingModerationKeyboard(listing.getId()));
    }

    private void notifyOwnerListingAuthor(OwnerListing listing, boolean approved) {
        if (listing.getCreatedByTelegramId() == null) {
            return;
        }

        Language lang = getUserLanguage(listing.getCreatedByTelegramId());
        String text = ownerListingAdminMessages.authorNotification(lang, approved);

        try {
            send(listing.getCreatedByTelegramId(), text, Keyboards.persistentNavKeyboard(lang));
        } catch (Exception e) {
            log.warn("Owner listing author notification failed: listing={}, user={}",
                    listing.getId(), listing.getCreatedByTelegramId(), e);
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

        if (data.equals("PREMIUM:CONTEXTUAL_OFFER")) {
            premiumMetricsService.record(userId, PremiumEvent.Type.CONTEXTUAL_OFFER_CLICKED);
            premiumMetricsService.record(userId, PremiumEvent.Type.OPENED);
            send(chatId, premiumViewService.paymentIntro(lang), Keyboards.premiumPaymentMethodsKeyboard(lang));
            return;
        }

        if (data.equals("PREMIUM:REQUEST")) {
            send(chatId, premiumViewService.paymentIntro(lang), Keyboards.premiumPaymentMethodsKeyboard(lang));
            return;
        }

        if (data.equals("PREMIUM:PAY")) {
            send(chatId, premiumViewService.paymentIntro(lang), Keyboards.premiumPaymentMethodsKeyboard(lang));
            return;
        }

        if (data.equals("PREMIUM:MANAGE_SEARCHES")) {
            showSearches(chatId, f, lang);
            return;
        }

        if (data.startsWith("PREMIUM:METHOD:")) {
            String method = data.substring("PREMIUM:METHOD:".length());
            if (!premiumViewService.isPaymentMethod(method)) return;
            premiumMetricsService.record(userId, PremiumEvent.Type.PAYMENT_METHOD_SELECTED);
            send(chatId, premiumViewService.paymentInstructions(lang, method, userId),
                    Keyboards.premiumPaymentConfirmationKeyboard(premiumViewService.paymentMethodTitle(method), premiumViewService.paymentUrl(method), lang));
            return;
        }

        if (data.startsWith("PREMIUM:PAID:")) {
            String method = data.substring("PREMIUM:PAID:".length());
            if (!premiumViewService.isPaymentMethod(method)) return;
            String username = update.getCallbackQuery().getFrom().getUserName();
            String requester = username == null || username.isBlank() ? String.valueOf(userId) : "@" + username + " / " + userId;
            var paymentRequest = premiumPaymentService.submit(userId, method);
            premiumMetricsService.record(userId, PremiumEvent.Type.REQUEST_SUBMITTED);
            send(adminId,
                    "💎 Заявка на активацію платного Premium\nКористувач: " + requester
                            + "\nСпосіб: " + premiumViewService.paymentMethodTitle(method)
                            + "\nСума: 99 Kč / 30 днів\n\nПеревір оплату та активуй доступ.",
                    Keyboards.premiumPaymentAdminKeyboard(paymentRequest.getId()));
            send(chatId, premiumViewService.submitted(lang), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (data.startsWith("PREMIUM:APPROVE:")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }
            send(chatId, "ℹ️ Ця застаріла дія не змінює Premium. Використовуйте актуальну платіжну заявку.", Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (data.startsWith("PREMIUM:PAYMENT_APPROVE:")) {
            if (chatId != adminId) return;
            Long requestId = parseLongOrNull(data.substring("PREMIUM:PAYMENT_APPROVE:".length()));
            if (requestId == null) return;
            var approvedUserId = premiumPaymentService.approve(requestId);
            if (approvedUserId.isEmpty()) { send(chatId, "ℹ️ Ця заявка вже оброблена або не знайдена.", Keyboards.persistentNavKeyboard(lang)); return; }
            premiumMetricsService.record(approvedUserId.get(), PremiumEvent.Type.APPROVED);
            Language targetLang = getUserLanguage(approvedUserId.get());
            send(approvedUserId.get(), premiumViewService.activated(targetLang), Keyboards.premiumActiveKeyboard(targetLang));
            send(chatId, "✅ Premium активовано на 30 днів для " + approvedUserId.get(), Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (data.startsWith("PREMIUM:PAYMENT_REJECT:")) {
            if (chatId != adminId) return;
            Long requestId = parseLongOrNull(data.substring("PREMIUM:PAYMENT_REJECT:".length()));
            if (requestId == null) return;
            var rejectedUserId = premiumPaymentService.reject(requestId);
            if (rejectedUserId.isEmpty()) {
                send(chatId, "ℹ️ Ця заявка вже оброблена або не знайдена.", Keyboards.persistentNavKeyboard(lang));
                return;
            }
            premiumMetricsService.record(rejectedUserId.get(), PremiumEvent.Type.REJECTED);
            Language targetLang = getUserLanguage(rejectedUserId.get());
            send(rejectedUserId.get(), premiumViewService.rejected(targetLang), Keyboards.persistentNavKeyboard(targetLang));
            send(chatId, "❌ Заявку відхилено.", Keyboards.persistentNavKeyboard(lang));
            return;
        }

        if (data.startsWith("PREMIUM:REJECT:")) {
            if (chatId == adminId) {
                send(chatId, "ℹ️ Ця стара заявка більше не використовується.", Keyboards.persistentNavKeyboard(lang));
            }
            return;
        }

        if (data.equals("PREMIUM:SETUP")) {
            if (!premiumService.isActive(f)) {
                send(chatId, premiumViewService.paymentIntro(lang), Keyboards.premiumPaymentMethodsKeyboard(lang));
                return;
            }
            premiumService.getOrCreateSearch(f);
            send(chatId, premiumViewService.chooseRegion(lang), Keyboards.premiumRegionsKeyboard(regionRepo.findAll()));
            return;
        }

        if (data.equals("SEARCH:LIST")) {
            showSearches(chatId, f, lang);
            return;
        }

        if (data.equals("SEARCH:MAIN")) {
            UserFilter fullFilter = userFilterRepo.findFullById(userId).orElseGet(() -> f);
            send(chatId, flowService.pretty(fullFilter, lang), Keyboards.filterActionsKeyboard(lang));
            return;
        }

        if (data.equals("SEARCH:PREMIUM")) {
            PremiumSearch search = premiumService.findActiveSearch(userId).orElse(null);
            if (search == null) {
                send(chatId, premiumViewService.searchNotConfigured(lang), Keyboards.searchesKeyboard(false, lang));
            } else {
                send(chatId, premiumViewService.searchReady(lang, search), Keyboards.premiumSearchActionsKeyboard(lang));
            }
            return;
        }

        if (data.startsWith("PREMIUM:REGION:")) {
            if (!premiumService.isActive(f)) return;
            String code = data.substring("PREMIUM:REGION:".length());
            Region region = regionRepo.findByCode(code).orElse(null);
            if (region == null) return;
            PremiumSearch search = premiumService.getOrCreateSearch(f);
            search.setRegion(region);
            search.setRegionGroup(null);
            premiumService.save(search);
            List<RegionGroup> groups = region.isHasDistricts()
                    ? regionGroupRepo.findByRegionId(region.getId()) : List.of();
            if (groups.isEmpty()) {
                send(chatId, premiumViewService.chooseLayout(lang), Keyboards.premiumLayoutKeyboard(lang));
            } else {
                send(chatId, premiumViewService.chooseDistrict(lang), Keyboards.premiumRegionGroupsKeyboard(groups));
            }
            return;
        }

        if (data.startsWith("PREMIUM:GROUP:")) {
            if (!premiumService.isActive(f)) return;
            RegionGroup group = regionGroupRepo.findByCode(data.substring("PREMIUM:GROUP:".length())).orElse(null);
            if (group == null) return;
            PremiumSearch search = premiumService.getOrCreateSearch(f);
            if (search.getRegion() == null || !search.getRegion().getId().equals(group.getRegion().getId())) return;
            search.setRegionGroup(group);
            premiumService.save(search);
            send(chatId, premiumViewService.chooseLayout(lang), Keyboards.premiumLayoutKeyboard(lang));
            return;
        }

        if (data.startsWith("PREMIUM:LAYOUT:")) {
            if (!premiumService.isActive(f)) return;
            PremiumSearch search = premiumService.getOrCreateSearch(f);
            search.setLayout(data.substring("PREMIUM:LAYOUT:".length()));
            premiumService.save(search);
            send(chatId, premiumViewService.choosePrice(lang), Keyboards.premiumPriceKeyboard(lang));
            return;
        }

        if (data.startsWith("PREMIUM:PRICE:")) {
            if (!premiumService.isActive(f)) return;
            Integer price = parsePrice(data.substring("PREMIUM:PRICE:".length()));
            PremiumSearch search = premiumService.getOrCreateSearch(f);
            if (price == null || search.getRegion() == null || search.getLayout() == null) return;
            search.setMaxPrice(price);
            search.setActive(true);
            premiumService.save(search);
            send(chatId, premiumViewService.searchReady(lang, search), Keyboards.premiumSearchActionsKeyboard(lang));
            return;
        }

        if (data.equals("OWNER:SUBMIT")) {
            sendOwnerListingFormResult(chatId, ownerListingFormHandler.submit(userId, lang), lang);
            return;
        }

        if (data.equals("OWNER:CANCEL")) {
            sendOwnerListingFormResult(chatId, ownerListingFormHandler.cancel(userId, lang), lang);
            return;
        }

        if (data.startsWith("OWNER:APPROVE:")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            Long listingId = parseLongOrNull(data.substring("OWNER:APPROVE:".length()));
            Optional<OwnerListing> approved = ownerListingModerationService.approvePending(listingId);
            if (approved.isEmpty()) {
                send(chatId, "Заявку не знайдено або вона вже оброблена.", Keyboards.persistentNavKeyboard(lang));
                return;
            }

            OwnerListing approvedListing = approved.get();
            send(chatId,
                    "✅ Оголошення опубліковане.\nID: " + approvedListing.getId()
                            + "\n\nВоно тепер бере участь у фільтрах як джерело «Власник».",
                    Keyboards.persistentNavKeyboard(lang));
            notifyOwnerListingAuthor(approvedListing, true);
            return;
        }

        if (data.startsWith("OWNER:REJECT:")) {
            if (chatId != adminId) {
                send(chatId, msg(userId, "access.denied"), Keyboards.persistentNavKeyboard(lang));
                return;
            }

            Long listingId = parseLongOrNull(data.substring("OWNER:REJECT:".length()));
            Optional<OwnerListing> archived = ownerListingModerationService.rejectPending(listingId);
            if (archived.isEmpty()) {
                send(chatId, "Заявку не знайдено або вона вже оброблена.", Keyboards.persistentNavKeyboard(lang));
                return;
            }

            OwnerListing archivedListing = archived.get();
            send(chatId,
                    "❌ Оголошення відхилене / відправлене в архів.\nID: " + archivedListing.getId(),
                    Keyboards.persistentNavKeyboard(lang));
            notifyOwnerListingAuthor(archivedListing, false);
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
                if (favoriteService.countForUser(userId) == 3 && !premiumService.isActive(f)) {
                    premiumMetricsService.record(userId, PremiumEvent.Type.CONTEXTUAL_OFFER_SHOWN);
                    send(chatId, premiumViewService.contextualOffer(lang), Keyboards.premiumContextualOfferKeyboard(lang));
                }
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
                log.error("Favorite removal failed for user={}", userId, e);
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
                        log.error("Main menu listing check failed for user={}", userId, e);
                        send(chatId, msg(userId, "search.error"), Keyboards.mainMenuKeyboard(lang));
                    }
                }

                case "FILTER" -> {
                    UserFilter fullFilter = userFilterRepo.findFullById(userId)
                            .orElseGet(() -> f);
                    send(chatId, flowService.pretty(fullFilter, lang), Keyboards.filterActionsKeyboard(lang));
                }

                case "FAVORITES" -> showFavorites(chatId, userId);

                case "SUPPORT" -> showSupport(chatId, userId, lang);

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
            showPremium(chatId, userId, f, lang);
            return;
        }

        if (data.startsWith("SERVICE:OWNER_LISTING")) {
            sendOwnerListingFormResult(chatId, ownerListingFormHandler.start(userId, update.getCallbackQuery().getFrom().getUserName(), lang), lang);
            return;
        }

        if (data.equals("SERVICE:SUPPORT")) {
            showSupport(chatId, userId, lang);
            return;
        }

        if (data.equals("STATUS:VIEW")) {
            searchStatusMetricsService.record(userId, SearchStatusEvent.Type.VIEWED);
            UserFilter fullFilter = userFilterRepo.findFullById(userId).orElseGet(() -> f);
            send(chatId, flowService.pretty(fullFilter, lang), Keyboards.filterActionsKeyboard(lang));
            return;
        }

        if (data.equals("STATUS:EDIT")) {
            searchStatusMetricsService.record(userId, SearchStatusEvent.Type.EDIT_OPENED);
            data = "EDIT:FILTER";
        }

        if (data.equals("SUPPORT:RAIFFEISEN")) {
            supportMetricsService.record(userId, SupportEvent.Type.RAIFFEISEN);
            send(chatId, raiffeisenSupportInfo(lang), Keyboards.supportKeyboard(lang));
            return;
        }

        if (data.equals("SUPPORT:PRIVATBANK")) {
            showSupportPayment(chatId, userId, lang, SupportEvent.Type.PRIVATBANK,
                    "PrivatBank", "https://www.privat24.ua/send/47m35");
            return;
        }

        if (data.equals("SUPPORT:PAYPAL")) {
            showSupportPayment(chatId, userId, lang, SupportEvent.Type.PAYPAL,
                    "PayPal", "https://www.paypal.me/YEVHENSHKUROPAT");
            return;
        }

        if (data.equals("SUPPORT:REVOLUT")) {
            showSupportPayment(chatId, userId, lang, SupportEvent.Type.REVOLUT,
                    "Revolut", "https://revolut.me/evzen13");
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

        if (data.equals("REACTIVATE:RESUME")) {
            reactivationMetricsService.record(userId, ReactivationEvent.Type.CLICKED);
            UserFilter fullFilter = userFilterRepo.findFullById(userId).orElseGet(() -> f);
            if (!fullFilter.isOnboarded() || fullFilter.getRegion() == null || fullFilter.getLayout() == null) {
                sendRegionsEntry(chatId, userId, msg(userId, "filter.start"));
                return;
            }
            fullFilter.setActive(true);
            fullFilter.setStep(FlowStep.CONFIRM);
            flowService.save(fullFilter);
            reactivationMetricsService.record(userId, ReactivationEvent.Type.ACTIVATED);
            send(chatId, reactivationActivatedText(lang), Keyboards.mainMenuKeyboard(lang));
            return;
        }

        if (data.equals("REACTIVATE:EDIT")) {
            reactivationMetricsService.record(userId, ReactivationEvent.Type.CLICKED);
            data = "EDIT:FILTER";
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
                    log.warn("Reactivation message failed for user={}", user.getTelegramUserId(), e);
                }
            } catch (Exception e) {
                result.failed++;
                log.error("Unexpected reactivation failure for user={}", user.getTelegramUserId(), e);
            }
        }

        return result;
    }

    private ReactivationResult sendInactiveReactivationMessages(int limit) {
        ReactivationResult result = new ReactivationResult();
        Instant now = Instant.now();
        Instant staleBefore = now.minus(java.time.Duration.ofDays(30));
        Instant canSendAgainBefore = now.minus(java.time.Duration.ofDays(90));

        List<UserFilter> candidates = userFilterRepo.findInactiveReactivationCandidates(
                now,
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
                        inactiveReactivationText(user, userLang),
                        Keyboards.inactiveReactivationKeyboard(userLang));
                user.setInactiveReactivationSentAt(now);
                userFilterRepo.save(user);
                reactivationMetricsService.record(user.getTelegramUserId(), ReactivationEvent.Type.SENT);
                result.sent++;
            } catch (TelegramApiException e) {
                if (isUnreachableTelegramUser(e.getMessage())) {
                    result.deactivated++;
                } else {
                    result.failed++;
                    log.warn("Inactive reactivation message failed user={} error={}", user.getTelegramUserId(), e.getMessage());
                }
            } catch (Exception e) {
                result.failed++;
                log.warn("Unexpected inactive reactivation failure user={}", user.getTelegramUserId(), e.getMessage());
            }
        }

        return result;
    }

    private ReactivationResult sendSearchStatusMessages(int limit) {
        ReactivationResult result = new ReactivationResult();
        Instant now = Instant.now();
        List<UserFilter> candidates = userFilterRepo.findSearchStatusCandidates(
                now.minus(java.time.Duration.ofDays(7)),
                PageRequest.of(0, limit)
        );
        result.checked = candidates.size();

        for (UserFilter user : candidates) {
            Long targetUserId = user.getTelegramUserId();
            if (targetUserId == null) {
                result.skipped++;
                continue;
            }

            boolean premiumUser = premiumService.isActive(user);
            int quietDays = premiumUser ? 4 : 7;
            if (sentLogRepo.existsByTelegramUserIdAndSentAtAfter(
                    targetUserId,
                    now.minus(java.time.Duration.ofDays(quietDays)))) {
                result.skipped++;
                continue;
            }

            Language userLang = user.getLanguage() != null ? user.getLanguage() : Language.UA;
            try {
                send(targetUserId,
                        searchStatusText(user, premiumUser, quietDays, userLang),
                        Keyboards.searchStatusKeyboard(userLang));
                user.setSearchStatusSentAt(now);
                userFilterRepo.save(user);
                searchStatusMetricsService.record(targetUserId, SearchStatusEvent.Type.SENT);
                result.sent++;
            } catch (TelegramApiException e) {
                if (isUnreachableTelegramUser(e.getMessage())) {
                    user.setActive(false);
                    userFilterRepo.save(user);
                    result.deactivated++;
                } else {
                    result.failed++;
                    log.warn("Search status message failed user={} error={}", targetUserId, e.getMessage());
                }
            } catch (Exception e) {
                result.failed++;
                log.warn("Unexpected search status failure user={} error={}", targetUserId, e.getMessage());
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
                    log.warn("Milestone 1500 message failed for user={}", user.getTelegramUserId(), e);
                }
            } catch (Exception e) {
                result.failed++;
                log.error("Unexpected milestone 1500 failure for user={}", user.getTelegramUserId(), e);
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

    private String inactiveReactivationText(UserFilter user, Language lang) {
        return switch (lang) {
            case RU -> "Привет 👋\n\nВаш сохранённый поиск всё ещё ждёт вас. Включите уведомления одним нажатием или сначала измените параметры.\n\n" + flowService.pretty(user, lang);
            case CZ -> "Ahoj 👋\n\nVaše uložené hledání na vás stále čeká. Upozornění můžete znovu zapnout jedním kliknutím nebo nejdříve upravit parametry.\n\n" + flowService.pretty(user, lang);
            case EN -> "Hi 👋\n\nYour saved search is still here. Resume alerts with one tap or update the settings first.\n\n" + flowService.pretty(user, lang);
            default -> "Привіт 👋\n\nВаш збережений пошук усе ще чекає на вас. Увімкніть сповіщення одним натисканням або спочатку змініть параметри.\n\n" + flowService.pretty(user, lang);
        };
    }

    private String searchStatusText(UserFilter user, boolean premiumUser, int quietDays, Language lang) {
        String premiumNote = premiumUser ? switch (lang) {
            case RU -> "\n\n🏡 Проверенные варианты от владельцев отправлю первыми, как только они появятся.";
            case CZ -> "\n\n🏡 Ověřené nabídky přímo od majitelů pošlu jako první, jakmile se objeví.";
            case EN -> "\n\n🏡 I will send verified owner listings first as soon as they appear.";
            default -> "\n\n🏡 Перевірені варіанти від власників надішлю першими, щойно вони з’являться.";
        } : "";

        return switch (lang) {
            case RU -> "🔎 Я продолжаю искать варианты по вашему фильтру.\n\nЗа последние " + quietDays
                    + " дней новых подходящих объявлений не появилось. Проверяю Sreality, Bezrealitky, Bazoš и объявления от владельцев. Как только найдётся подходящий вариант — сразу пришлю его."
                    + premiumNote + "\n\n" + flowService.pretty(user, lang);
            case CZ -> "🔎 Stále hledám nabídky podle vašeho filtru.\n\nZa posledních " + quietDays
                    + " dní se neobjevily žádné nové vhodné nabídky. Kontroluji Sreality, Bezrealitky, Bazoš a nabídky od majitelů. Jakmile najdu vhodnou nabídku, ihned ji pošlu."
                    + premiumNote + "\n\n" + flowService.pretty(user, lang);
            case EN -> "🔎 I am still searching for listings that match your filter.\n\nNo new suitable listings appeared in the last " + quietDays
                    + " days. I am checking Sreality, Bezrealitky, Bazoš, and owner listings. As soon as a suitable listing appears, I will send it right away."
                    + premiumNote + "\n\n" + flowService.pretty(user, lang);
            default -> "🔎 Я продовжую шукати варіанти за вашим фільтром.\n\nЗа останні " + quietDays
                    + " днів нових відповідних оголошень не з’явилося. Перевіряю Sreality, Bezrealitky, Bazoš та оголошення від власників. Щойно знайду відповідний варіант — одразу надішлю."
                    + premiumNote + "\n\n" + flowService.pretty(user, lang);
        };
    }

    private String reactivationActivatedText(Language lang) {
        return switch (lang) {
            case RU -> "✅ Поиск снова включён. Новые подходящие объявления будут приходить автоматически.";
            case CZ -> "✅ Hledání je znovu zapnuté. Nové vhodné nabídky vám budou chodit automaticky.";
            case EN -> "✅ Your search is active again. New matching listings will arrive automatically.";
            default -> "✅ Пошук знову увімкнено. Нові відповідні оголошення приходитимуть автоматично.";
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

    private String premiumStatusText(Long telegramUserId) {
        UserFilter user = userFilterRepo.findFullById(telegramUserId).orElse(null);
        if (user == null) {
            return "❌ Користувача " + telegramUserId + " не знайдено.";
        }

        PremiumSearch search = premiumService.findSearch(telegramUserId).orElse(null);
        boolean premiumActive = premiumService.isActive(user);
        boolean secondSearchActive = search != null && search.isActive();
        boolean schedulerEligible = user.isActive() || (premiumActive && secondSearchActive);

        String mainSearch = "🏠 Основний пошук\n"
                + "Активний: " + yesNo(user.isActive())
                + "\nОнбординг: " + yesNo(user.isOnboarded())
                + "\nКрок: " + user.getStep()
                + "\nМісто: " + regionTitle(user.getRegion())
                + "\nРайон: " + regionGroupTitle(user.getRegionGroup())
                + "\nТип: " + valueOrDash(user.getLayout())
                + "\nБюджет: " + priceText(user.getMaxPrice());

        String premiumSearch = search == null
                ? "💎 Другий Premium-пошук\nНе створений"
                : "💎 Другий Premium-пошук\n"
                        + "Активний: " + yesNo(search.isActive())
                        + "\nМісто: " + regionTitle(search.getRegion())
                        + "\nРайон: " + regionGroupTitle(search.getRegionGroup())
                        + "\nТип: " + valueOrDash(search.getLayout())
                        + "\nБюджет: " + priceText(search.getMaxPrice());

        return "🔎 Premium status\n\n"
                + "Telegram ID: " + telegramUserId
                + "\nPremium активний: " + yesNo(premiumActive)
                + "\nДіє до: " + (user.getPremiumUntil() == null ? "—" : user.getPremiumUntil())
                + "\nПотрапить у наступний цикл: " + yesNo(schedulerEligible)
                + "\n\n" + mainSearch + "\n\n" + premiumSearch;
    }

    private String premiumPaymentsText(List<PremiumPaymentRequest> pending, List<PremiumPaymentRequest> recent) {
        StringBuilder text = new StringBuilder("💳 Premium payments\n\nОчікують перевірки: ")
                .append(pending.size());
        if (pending.isEmpty()) {
            text.append("\nНемає заявок, що очікують на підтвердження.");
        }
        text.append("\n\nОстанні операції:");
        recent.stream().limit(10).forEach(request -> text.append("\n#")
                .append(request.getId()).append(" · ")
                .append(request.getStatus()).append(" · ")
                .append(request.getTelegramUserId()).append(" · ")
                .append(premiumViewService.paymentMethodTitle(request.getPaymentMethod())).append(" · ")
                .append(formatInstant(request.getCreatedAt())));
        return text.toString();
    }

    private String healthStatusText() {
        SchedulerRunStats scheduler = schedulerService.getLastRunStats();
        ParserRunStats parser = parserService.getLastRunStats();
        Instant completedAt = schedulerService.getLastCompletedAt();
        boolean recent = completedAt != null
                && java.time.Duration.between(completedAt, Instant.now()).toMinutes() < 20;
        boolean hasResults = scheduler.aggregateFilteredBase() > 0 && scheduler.aggregateFinal() > 0;
        String status = recent && hasResults ? "✅ Норма" : "⚠️ Потребує перевірки";

        return "🩺 Стан бота: " + status
                + "\nОстанній повний цикл: " + formatInstant(completedAt)
                + "\n\n👥 Оброблено: " + scheduler.usersProcessed()
                + "\n🔎 Зі співпадіннями: " + scheduler.usersWithMatches()
                + "\n📤 Нових надіслано: " + scheduler.totalSent()
                + "\n📦 У фінальній видачі: " + scheduler.aggregateFinal()
                + "\n🧪 До diversify: " + scheduler.aggregateFilteredBase()
                + "\n\n📡 Останній парсинг:"
                + "\nSreality: " + parser.srealityRaw()
                + "\niDNES: " + parser.idnesRaw()
                + "\nBezrealitky: " + parser.bezrealitkyRaw()
                + "\nBazoš: " + parser.bazosRaw()
                + "\nDigiReality owners: " + parser.digirealityRaw();
    }

    private String premiumPaymentRequestText(PremiumPaymentRequest request) {
        return "💎 Заявка на Premium #" + request.getId()
                + "\nКористувач: " + request.getTelegramUserId()
                + "\nСпосіб: " + premiumViewService.paymentMethodTitle(request.getPaymentMethod())
                + "\nСума: " + request.getAmountCzk() + " Kč / 30 днів"
                + "\nСтворено: " + formatInstant(request.getCreatedAt());
    }

    private String premiumExpiryReminderText(Language lang, Instant premiumUntil) {
        String expires = formatInstant(premiumUntil);
        return switch (lang) {
            case RU -> "💎 Premium действует до " + expires + ".\n\nПродлите доступ, чтобы сохранить второй поиск, приоритетную обработку, до 10 уведомлений за цикл и проверенные предложения от владельцев первыми.";
            case CZ -> "💎 Premium platí do " + expires + ".\n\nProdloužením si zachováte druhé hledání, prioritní zpracování, až 10 upozornění za cyklus a ověřené nabídky od majitelů jako první.";
            case EN -> "💎 Premium is active until " + expires + ".\n\nRenew to keep your second search, priority processing, up to 10 alerts per cycle, and verified owner listings first.";
            default -> "💎 Premium діє до " + expires + ".\n\nПродовжте доступ, щоб зберегти другий пошук, пріоритетну обробку, до 10 сповіщень за цикл та перевірені пропозиції від власників першими.";
        };
    }

    private String premiumSecondSearchReminderText(Language lang) {
        return switch (lang) {
            case RU -> "💎 Ваш Premium уже активен. Настройте второй независимый поиск — например, для другого района, планировки или бюджета. Так вы получите главную возможность Premium.";
            case CZ -> "💎 Váš Premium už je aktivní. Nastavte si druhé nezávislé hledání — například pro jinou lokalitu, dispozici nebo rozpočet. Získáte tak hlavní výhodu Premium.";
            case EN -> "💎 Your Premium is already active. Set up your second independent search—for another area, layout, or budget—to use the main Premium benefit.";
            default -> "💎 Ваш Premium уже активний. Налаштуйте другий незалежний пошук — наприклад, для іншого району, планування або бюджету. Так ви отримаєте головну можливість Premium.";
        };
    }

    private String formatInstant(Instant value) {
        return value == null ? "—" : DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
                .withZone(ZoneId.of("Europe/Prague"))
                .format(value);
    }

    private String yesNo(boolean value) {
        return value ? "✅ так" : "❌ ні";
    }

    private String regionTitle(Region region) {
        return region == null ? "—" : valueOrDash(region.getTitle());
    }

    private String regionGroupTitle(RegionGroup regionGroup) {
        return regionGroup == null ? "—" : valueOrDash(regionGroup.getTitle());
    }

    private String priceText(Integer price) {
        return price == null || price == 0 ? "без ліміту" : price + " Kč";
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
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
            log.error("Manual notification check failed for user={}", userId, e);
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

    private void showSupport(long chatId, long userId, Language lang) throws TelegramApiException {
        supportMetricsService.record(userId, SupportEvent.Type.OPENED);
        send(chatId, supportText(lang), Keyboards.supportKeyboard(lang));
    }

    private void showSupportPayment(long chatId,
                                    long userId,
                                    Language lang,
                                    SupportEvent.Type type,
                                    String paymentMethod,
                                    String url) throws TelegramApiException {
        supportMetricsService.record(userId, type);
        String text = switch (lang) {
            case RU -> "Спасибо за поддержку 💙\n\nМожно выбрать сумму 50, 100 или 200 Kč — любая помощь приближает месячную цель.";
            case CZ -> "Děkujeme za podporu 💙\n\nMůžete zvolit částku 50, 100 nebo 200 Kč — každá pomoc přibližuje měsíční cíl.";
            case EN -> "Thank you for your support 💙\n\nYou can choose 50, 100, or 200 Kč — every contribution helps reach the monthly goal.";
            default -> "Дякуємо за підтримку 💙\n\nМожна обрати 50, 100 або 200 Kč — кожна допомога наближає місячну ціль.";
        };
        send(chatId, text, Keyboards.supportPaymentKeyboard(paymentMethod, url, lang));
    }

    private String supportText(Language lang) {
        return switch (lang) {
            case RU -> """
                    Спасибо, что пользуетесь ботом 💙

                    Ежемесячная цель на сервер, парсеры и развитие: %d Kč.

                    Даже 50, 100 или 200 Kč помогают боту работать дальше. Выберите удобный способ поддержки:""".formatted(supportMonthlyGoalCzk);
            case CZ -> """
                    Děkujeme, že používáte bota 💙

                    Měsíční cíl na server, parsery a vývoj: %d Kč.

                    Už 50, 100 nebo 200 Kč pomůže botovi fungovat dál. Vyberte si způsob podpory:""".formatted(supportMonthlyGoalCzk);
            case EN -> """
                    Thank you for using the bot 💙

                    Monthly goal for the server, parsers, and development: %d Kč.

                    Even 50, 100, or 200 Kč helps keep the bot running. Choose a convenient support option:""".formatted(supportMonthlyGoalCzk);
            default -> """
                    Дякую, що користуєтеся ботом 💙

                    Щомісячна ціль на сервер, парсери та розвиток: %d Kč.

                    Навіть 50, 100 або 200 Kč допомагають боту працювати далі. Оберіть зручний спосіб підтримки:""".formatted(supportMonthlyGoalCzk);
        };
    }

    private String supportPromptText(Language lang) {
        return switch (lang) {
            case RU -> "⭐ Вы добавили несколько объявлений в избранное. Если бот помогает с поиском, его можно поддержать любой суммой.";
            case CZ -> "⭐ Uložili jste několik inzerátů do oblíbených. Pokud vám bot pomáhá s hledáním, můžete jej podpořit libovolnou částkou.";
            case EN -> "⭐ You saved several listings. If the bot is helping with your search, you can support it with any amount.";
            default -> "⭐ Ви додали кілька оголошень в обране. Якщо бот допомагає у пошуку, його можна підтримати будь-якою сумою.";
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

    private void showPremium(long chatId, long userId, UserFilter user, Language lang) throws TelegramApiException {
        if (premiumService.isActive(user)) {
            send(chatId, premiumViewService.overview(lang, user.getPremiumUntil()), Keyboards.premiumOverviewKeyboard(lang));
        } else {
            premiumMetricsService.record(userId, PremiumEvent.Type.OPENED);
            send(chatId, premiumViewService.paymentIntro(lang), Keyboards.premiumPaymentMethodsKeyboard(lang));
        }
    }

    private void showSearches(long chatId, UserFilter user, Language lang) throws TelegramApiException {
        if (!premiumService.isActive(user)) {
            send(chatId, flowService.pretty(user, lang), Keyboards.filterActionsKeyboard(lang));
            return;
        }

        PremiumSearch secondSearch = premiumService.findActiveSearch(user.getTelegramUserId()).orElse(null);
        String mainLabel = switch (lang) {
            case RU -> "1️⃣ Основной поиск";
            case CZ -> "1️⃣ Hlavní hledání";
            case EN -> "1️⃣ Main search";
            default -> "1️⃣ Основний пошук";
        };
        String premiumLabel = switch (lang) {
            case RU -> "2️⃣ Premium-поиск";
            case CZ -> "2️⃣ Premium hledání";
            case EN -> "2️⃣ Premium search";
            default -> "2️⃣ Premium-пошук";
        };
        String secondDetails = secondSearch == null
                ? premiumViewService.searchNotConfigured(lang)
                : premiumViewService.searchReady(lang, secondSearch);

        send(chatId,
                "📋 " + switch (lang) {
                    case RU -> "Мои поиски";
                    case CZ -> "Moje hledání";
                    case EN -> "My searches";
                    default -> "Мої пошуки";
                } + "\n\n" + mainLabel + "\n" + flowService.pretty(user, lang)
                        + "\n\n" + premiumLabel + "\n" + secondDetails,
                Keyboards.searchesKeyboard(secondSearch != null, lang));
    }

    private Integer parsePrice(String value) {
        try {
            int price = Integer.parseInt(value);
            return price >= 0 && price <= 100_000 ? price : null;
        } catch (NumberFormatException e) {
            return null;
        }
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
                log.warn("AnswerCallbackQuery failed", e);
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
                log.warn("AnswerCallbackQuery with text failed", e);
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

    private void sendAutumnBanner(long chatId) {
        try (InputStream image = getClass().getResourceAsStream("/images/autumn-prague-banner.png")) {
            if (image == null) {
                log.warn("Autumn banner resource is missing");
                return;
            }
            telegramClient.execute(SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(image, "autumn-prague-banner.png"))
                    .build());
        } catch (Exception e) {
            log.warn("Could not send autumn banner", e);
        }
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
                log.warn("Listing photo send failed, falling back to text: link={}", l.link(), e);
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
                log.warn("Favorite photo send failed, falling back to text: link={}", fav.getLink(), e);
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
        if (normalized.contains("owner") || normalized.contains("власник")) {
            return switch (lang) {
                case RU -> "Владелец (проверено)";
                case CZ -> "Majitel (ověřeno)";
                case EN -> "Owner (verified)";
                default -> "Власник (перевірено)";
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
                log.warn("Paged listing photo send failed, falling back to text: link={}", l.link(), e);
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

}
