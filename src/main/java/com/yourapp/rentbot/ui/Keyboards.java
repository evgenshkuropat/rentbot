package com.yourapp.rentbot.ui;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.RegionGroup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Keyboards {

    // ✅ Города из БД (динамически) + сортировка безопасно (через копию списка)
    public static InlineKeyboardMarkup regionsKeyboard(List<Region> regions) {
        List<Region> sorted = new ArrayList<>(regions);
        sorted.sort(Comparator.comparing(Region::getTitle, String.CASE_INSENSITIVE_ORDER));

        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Region r : sorted) {
            InlineKeyboardButton btn = InlineKeyboardButton.builder()
                    .text(r.getTitle())
                    .callbackData("REGION:" + r.getCode())
                    .build();
            rows.add(new InlineKeyboardRow(btn));
        }

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    // ✅ Группы районов из БД (динамически) + UX: "Всі райони" сверху
    public static InlineKeyboardMarkup regionGroupsKeyboard(List<RegionGroup> groups) {
        List<RegionGroup> sorted = new ArrayList<>(groups);

        // 1) "Всі райони" (code заканчивается на _ALL) всегда сверху
        // 2) потом по title
        sorted.sort((a, b) -> {
            boolean aAll = a.getCode() != null && a.getCode().endsWith("_ALL");
            boolean bAll = b.getCode() != null && b.getCode().endsWith("_ALL");
            if (aAll && !bAll) return -1;
            if (!aAll && bAll) return 1;
            return String.valueOf(a.getTitle()).compareToIgnoreCase(String.valueOf(b.getTitle()));
        });

        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (RegionGroup g : sorted) {
            InlineKeyboardButton btn = InlineKeyboardButton.builder()
                    .text(g.getTitle())
                    .callbackData("GROUP:" + g.getCode())
                    .build();
            rows.add(new InlineKeyboardRow(btn));
        }

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup layoutKeyboard() {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder().text("1+kk").callbackData("LAYOUT:1+kk").build()));
        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder().text("2+kk").callbackData("LAYOUT:2+kk").build()));
        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder().text("3+kk").callbackData("LAYOUT:3+kk").build()));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup priceKeyboard() {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder().text("15000").callbackData("PRICE:15000").build()));
        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder().text("18000").callbackData("PRICE:18000").build()));
        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder().text("20000").callbackData("PRICE:20000").build()));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup confirmKeyboard() {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("✅ Підписатися").callbackData("CONFIRM:SUBSCRIBE").build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("⛔ Зупинити").callbackData("CONFIRM:STOP").build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("🔄 Змінити фільтр").callbackData("CONFIRM:RESET").build()
        ));
        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("📋 Мої налаштування").callbackData("CONFIRM:SHOW").build()
        ));

        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }
}