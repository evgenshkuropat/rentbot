package com.yourapp.rentbot.ui;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.RegionGroup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Keyboards {

    public static InlineKeyboardMarkup regionsKeyboard(List<Region> regions) {
        List<Region> sorted = new ArrayList<>(regions);
        sorted.sort(Comparator.comparing(Region::getTitle));

        List<InlineKeyboardRow> rows = new ArrayList<>();

        for (Region r : sorted) {
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder()
                            .text(r.getTitle())
                            .callbackData("REGION:" + r.getCode())
                            .build()
            ));
        }

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup regionGroupsKeyboard(List<RegionGroup> groups) {
        List<RegionGroup> sorted = new ArrayList<>(groups);

        sorted.sort((a, b) -> {
            if ("PRAHA_ALL".equals(a.getCode())) return -1;
            if ("PRAHA_ALL".equals(b.getCode())) return 1;
            return a.getTitle().compareToIgnoreCase(b.getTitle());
        });

        List<InlineKeyboardRow> rows = new ArrayList<>();

        for (RegionGroup g : sorted) {
            rows.add(new InlineKeyboardRow(
                    InlineKeyboardButton.builder()
                            .text(g.getTitle())
                            .callbackData("GROUP:" + g.getCode())
                            .build()
            ));
        }

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup layoutKeyboard() {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("1 кімната")
                        .callbackData("LAYOUT:1")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("2 кімнати")
                        .callbackData("LAYOUT:2")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("3 кімнати")
                        .callbackData("LAYOUT:3")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("4+")
                        .callbackData("LAYOUT:4+")
                        .build()
        ));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup priceKeyboard() {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                button("15000", "PRICE:15000"),
                button("18000", "PRICE:18000")
        ));

        rows.add(new InlineKeyboardRow(
                button("20000", "PRICE:20000"),
                button("25000", "PRICE:25000")
        ));

        rows.add(new InlineKeyboardRow(
                button("30000", "PRICE:30000")
        ));

        rows.add(new InlineKeyboardRow(
                button("Без ліміту", "PRICE:0")
        ));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup confirmKeyboard() {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("✅ Підписатися")
                        .callbackData("CONFIRM:SUBSCRIBE")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("⛔ Зупинити")
                        .callbackData("CONFIRM:STOP")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("🔄 Змінити фільтр")
                        .callbackData("CONFIRM:RESET")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("📋 Мій фільтр")
                        .callbackData("CONFIRM:SHOW")
                        .build()
        ));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static ReplyKeyboardMarkup persistentNavKeyboard() {
        KeyboardRow row1 = new KeyboardRow();
        row1.add("🔄 Новий пошук");
        row1.add("📋 Мій фільтр");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("⭐ Обране");
        row2.add("⛔ Зупинити пошук");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("📤 Поширити бота");
        row3.add("💙 Підтримати проєкт");

        List<KeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        return ReplyKeyboardMarkup.builder()
                .keyboard(keyboard)
                .resizeKeyboard(true)
                .isPersistent(true)
                .build();
    }

    public static InlineKeyboardMarkup mainMenuKeyboard() {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(InlineKeyboardButton.builder()
                .text("🔍 Нові квартири")
                .callbackData("MENU:NEW")
                .build());
        row1.add(InlineKeyboardButton.builder()
                .text("📋 Мій фільтр")
                .callbackData("MENU:FILTER")
                .build());

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(InlineKeyboardButton.builder()
                .text("⭐ Обране")
                .callbackData("MENU:FAVORITES")
                .build());

        InlineKeyboardRow row3 = new InlineKeyboardRow();
        row3.add(InlineKeyboardButton.builder()
                .text("⛔ Зупинити пошук")
                .callbackData("MENU:STOP")
                .build());

        InlineKeyboardRow row4 = new InlineKeyboardRow();
        row4.add(InlineKeyboardButton.builder()
                .text("📤 Поширити бота")
                .url("https://t.me/share/url?url=https://t.me/zhytloCZ_bot&text=Знайди житло в Чехії 🇨🇿")
                .build());
        row4.add(InlineKeyboardButton.builder()
                .text("💙 Підтримати проєкт")
                .url("https://revolut.me/evzen13")
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2, row3, row4))
                .build();
    }

    public static InlineKeyboardMarkup addToFavoritesKeyboard(String token) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder()
                .text("⭐ В обране")
                .callbackData("FAV:ADD:" + token)
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
    }

    public static InlineKeyboardMarkup removeFromFavoritesKeyboard(String link) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder()
                .text("❌ Прибрати з обраного")
                .callbackData("FAV:REMOVE:" + link.hashCode())
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
    }

    private static InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(data)
                .build();
    }
}