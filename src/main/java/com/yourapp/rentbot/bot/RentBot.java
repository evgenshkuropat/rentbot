package com.yourapp.rentbot.bot;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.RegionGroup;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.flow.FlowService;
import com.yourapp.rentbot.flow.FlowStep;
import com.yourapp.rentbot.repo.RegionGroupRepo;
import com.yourapp.rentbot.repo.RegionRepo;
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

import java.util.List;

@Component
public class RentBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final FlowService flowService;
    private final RegionRepo regionRepo;
    private final RegionGroupRepo regionGroupRepo;
    private final ParserService parserService;
    private final NotificationService notificationService;

    private final String token;

    public RentBot(
            @Value("${telegram.bot.token}") String token,
            FlowService flowService,
            RegionRepo regionRepo,
            RegionGroupRepo regionGroupRepo,
            ParserService parserService,
            NotificationService notificationService
    ) {
        this.token = token;
        this.flowService = flowService;
        this.regionRepo = regionRepo;
        this.regionGroupRepo = regionGroupRepo;
        this.parserService = parserService;
        this.notificationService = notificationService;
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
            System.out.println("UPDATE: " + update);

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

        if (text.equalsIgnoreCase("/menu")) {
            send(chatId, "Головне меню:", Keyboards.mainMenuKeyboard());
            return;
        }

        if (text.equalsIgnoreCase("/start")) {
            flowService.reset(userId);

            List<Region> regions = regionRepo.findAll();
            send(chatId,
                    "Привіт! Знайдемо квартиру в Чехії 🇨🇿\nОбери місто:",
                    Keyboards.regionsKeyboard(regions)
            );
            return;
        }

        if (text.equalsIgnoreCase("/test")) {
            try {
                List<ListingDto> listings = parserService.findNewListings(userId);

                if (listings.isEmpty()) {
                    send(chatId, "Нічого не знайшов 😕", null);
                    return;
                }

                send(chatId, "Знайшов " + listings.size() + " оголошень:", null);

                for (ListingDto l : listings) {
                    sendListing(chatId, l);
                }

            } catch (Exception e) {
                e.printStackTrace();
                send(chatId, "Помилка тесту: " + e.getMessage(), null);
            }
            return;
        }

        send(chatId, "Користуйся кнопками 🙂\nНатисни /start щоб почати.\nДля тесту: /test", null);
    }

    private void onCallback(Update update) throws TelegramApiException {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        long userId = update.getCallbackQuery().getFrom().getId();
        String data = update.getCallbackQuery().getData();
        String callbackId = update.getCallbackQuery().getId();

        answerCallback(callbackId);
        disableInlineKeyboard(update);

        UserFilter f = flowService.getOrCreate(userId);

        if (!isCallbackAllowedForStep(data, f.getStep())) {
            send(chatId, "⚠️ Ця кнопка вже неактуальна.\nНатисни /start", null);
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
            f.setLayout(null);
            f.setMaxPrice(null);
            f.setStep(FlowStep.LAYOUT);
            flowService.save(f);

            send(chatId, "Обери тип квартири:", Keyboards.layoutKeyboard());
            return;
        }

        if (data.startsWith("LAYOUT:")) {
            String layout = data.substring("LAYOUT:".length());

            f.setLayout(layout);
            f.setMaxPrice(null);
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

            UserFilter fullFilter = flowService.getOrCreate(userId);

            send(chatId, "Готово ✅\n" + flowService.pretty(fullFilter), Keyboards.confirmKeyboard());
            return;
        }

        if (data.startsWith("CONFIRM:")) {
            String action = data.substring("CONFIRM:".length());

            switch (action) {
                case "SUBSCRIBE" -> {
                    f.setActive(true);
                    flowService.save(f);

                    send(chatId,
                            "🔔 Сповіщення увімкнено!\n\n" + flowService.pretty(f),
                            Keyboards.mainMenuKeyboard()
                    );

                    try {
                        List<ListingDto> listings = parserService.findNewListings(userId);

                        if (listings.isEmpty()) {
                            send(chatId,
                                    "Поки немає оголошень під твій фільтр 😕\n" +
                                            "Спробуй:\n" +
                                            "• збільшити ліміт ціни\n" +
                                            "• змінити район\n" +
                                            "• вибрати «Всі райони»",
                                    null);
                        } else {
                            send(chatId, "Ось що знайшов прямо зараз 👇", null);

                            int count = 0;
                            for (ListingDto l : listings) {
                                if (count >= 5) break;
                                notificationService.sendIfNotSent(f, l);
                                count++;
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        send(chatId,
                                "⚠️ Не вдалося зараз отримати оголошення, але підписка вже увімкнена.",
                                null);
                    }
                }
                case "STOP" -> {
                    f.setActive(false);
                    flowService.save(f);
                    send(chatId, "⛔ Сповіщення вимкнено.\n" + flowService.pretty(f), Keyboards.confirmKeyboard());
                }
                case "RESET" -> {
                    flowService.reset(userId);
                    List<Region> regions = regionRepo.findAll();
                    send(chatId, "Ок, давай заново. Обери місто:", Keyboards.regionsKeyboard(regions));
                }
                case "SHOW" -> send(chatId, flowService.pretty(f), Keyboards.confirmKeyboard());
                default -> send(chatId, "Невідома дія 😅", Keyboards.confirmKeyboard());
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

                        send(chatId, "Ось що знайшов 🔍", Keyboards.mainMenuKeyboard());

                        for (ListingDto l : listings) {
                            sendListing(chatId, l);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        send(chatId, "Помилка при пошуку квартир 😕", Keyboards.mainMenuKeyboard());
                    }
                }

                case "SETTINGS" -> send(chatId, flowService.pretty(f), Keyboards.mainMenuKeyboard());

                default -> send(chatId, "Невідома дія меню 😅", Keyboards.mainMenuKeyboard());
            }
            return;
        }

        send(chatId, "Невідомий callback: " + data, null);
    }

    private boolean isCallbackAllowedForStep(String data, FlowStep step) {
        if (data.startsWith("REGION:")) return step == FlowStep.CITY;
        if (data.startsWith("GROUP:")) return step == FlowStep.DISTRICT_GROUP;
        if (data.startsWith("LAYOUT:")) return step == FlowStep.LAYOUT;
        if (data.startsWith("PRICE:")) return step == FlowStep.MAX_PRICE;
        if (data.startsWith("CONFIRM:")) return step == FlowStep.CONFIRM;
        return true;
    }

    private void disableInlineKeyboard(Update update) {
        try {
            var msg = update.getCallbackQuery().getMessage();
            telegramClient.execute(EditMessageReplyMarkup.builder()
                    .chatId(msg.getChatId())
                    .messageId(msg.getMessageId())
                    .replyMarkup(null)
                    .build());
        } catch (Exception ignored) {
        }
    }

    private void answerCallback(String callbackQueryId) throws TelegramApiException {
        telegramClient.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .build());
    }

    private void send(long chatId, String text, ReplyKeyboard keyboardOrNull) throws TelegramApiException {
        SendMessage.SendMessageBuilder b = SendMessage.builder()
                .chatId(chatId)
                .text(text);

        if (keyboardOrNull != null) {
            b.replyMarkup(keyboardOrNull);
        }

        telegramClient.execute(b.build());
    }

    private void sendListing(long chatId, ListingDto l) throws TelegramApiException {
        String caption =
                "🏠 " + nvl(l.title()) + "\n" +
                        "💰 " + (l.priceCzk() > 0 ? l.priceCzk() + " Kč" : "—") + "\n" +
                        "📍 " + nvl(l.locality()) + "\n" +
                        "🔗 " + nvl(l.link());

        if (l.photoUrl() != null && !l.photoUrl().isBlank()) {
            telegramClient.execute(SendPhoto.builder()
                    .chatId(chatId)
                    .photo(new InputFile(l.photoUrl()))
                    .caption(caption)
                    .build());
        } else {
            send(chatId, caption, null);
        }
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
}