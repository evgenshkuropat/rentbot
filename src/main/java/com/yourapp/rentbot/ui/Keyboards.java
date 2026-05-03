package com.yourapp.rentbot.ui;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.RegionGroup;
import com.yourapp.rentbot.i18n.Language;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Keyboards {

    public static InlineKeyboardMarkup onboardingKeyboard(Language lang) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "🔍 Начать поиск";
                    case CZ -> "🔍 Začít hledání";
                    case EN -> "🔍 Start search";
                    default -> "🔍 Почати пошук";
                })
                .callbackData("ONBOARDING:START")
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
    }

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

    public static InlineKeyboardMarkup layoutKeyboard(Language lang) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(switch (lang) {
                            case RU -> "🚪 Комната / подселение";
                            case CZ -> "🚪 Pokoj / spolubydlení";
                            case EN -> "🚪 Room / shared housing";
                            default -> "🚪 Кімната / співжитло";
                        })
                        .callbackData("LAYOUT:ROOM")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(switch (lang) {
                            case RU -> "1 комната";
                            case CZ -> "1 pokoj";
                            case EN -> "1 room";
                            default -> "1 кімната";
                        })
                        .callbackData("LAYOUT:1")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(switch (lang) {
                            case RU -> "2 комнаты";
                            case CZ -> "2 pokoje";
                            case EN -> "2 rooms";
                            default -> "2 кімнати";
                        })
                        .callbackData("LAYOUT:2")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(switch (lang) {
                            case RU -> "3 комнаты";
                            case CZ -> "3 pokoje";
                            case EN -> "3 rooms";
                            default -> "3 кімнати";
                        })
                        .callbackData("LAYOUT:3")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("4+")
                        .callbackData("LAYOUT:4")
                        .build()
        ));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup priceKeyboard(Language lang) {
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
                button(switch (lang) {
                    case RU -> "Без лимита";
                    case CZ -> "Bez limitu";
                    case EN -> "No limit";
                    default -> "Без ліміту";
                }, "PRICE:0")
        ));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup confirmKeyboard(Language lang) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(switch (lang) {
                            case RU -> "✅ Подписаться";
                            case CZ -> "✅ Odebírat";
                            case EN -> "✅ Subscribe";
                            default -> "✅ Підписатися";
                        })
                        .callbackData("CONFIRM:SUBSCRIBE")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(switch (lang) {
                            case RU -> "⛔ Остановить";
                            case CZ -> "⛔ Zastavit";
                            case EN -> "⛔ Stop";
                            default -> "⛔ Зупинити";
                        })
                        .callbackData("CONFIRM:STOP")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(switch (lang) {
                            case RU -> "🔄 Изменить фильтр";
                            case CZ -> "🔄 Změnit filtr";
                            case EN -> "🔄 Change filter";
                            default -> "🔄 Змінити фільтр";
                        })
                        .callbackData("CONFIRM:RESET")
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(switch (lang) {
                            case RU -> "📋 Мой фильтр";
                            case CZ -> "📋 Můj filtr";
                            case EN -> "📋 My filter";
                            default -> "📋 Мій фільтр";
                        })
                        .callbackData("CONFIRM:SHOW")
                        .build()
        ));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static ReplyKeyboardMarkup persistentNavKeyboard(Language lang) {
        KeyboardRow row1 = new KeyboardRow();
        row1.add(switch (lang) {
            case RU -> "🔄 Новый поиск";
            case CZ -> "🔄 Nové hledání";
            case EN -> "🔄 New search";
            default -> "🔄 Новий пошук";
        });
        row1.add(switch (lang) {
            case RU -> "📋 Мой фильтр";
            case CZ -> "📋 Můj filtr";
            case EN -> "📋 My filter";
            default -> "📋 Мій фільтр";
        });

        KeyboardRow row2 = new KeyboardRow();
        row2.add(switch (lang) {
            case RU -> "⭐ Избранное";
            case CZ -> "⭐ Oblíbené";
            case EN -> "⭐ Favorites";
            default -> "⭐ Обране";
        });
        row2.add(switch (lang) {
            case RU -> "⛔ Остановить поиск";
            case CZ -> "⛔ Zastavit hledání";
            case EN -> "⛔ Stop search";
            default -> "⛔ Зупинити пошук";
        });
        KeyboardRow row3 = new KeyboardRow();
        row3.add(switch (lang) {
            case RU -> "🚗 Найти авто";
            case CZ -> "🚗 Najít auto";
            case EN -> "🚗 Find a car";
            default -> "🚗 Знайти авто";
        });
        row3.add(switch (lang) {
            case RU -> "💙 Поддержать проект";
            case CZ -> "💙 Podpořit projekt";
            case EN -> "💙 Support project";
            default -> "💙 Підтримати проєкт";
        });

        KeyboardRow row4 = new KeyboardRow();
        row4.add(switch (lang) {
            case RU -> "🌐 Язык / Language";
            case CZ -> "🌐 Jazyk / Language";
            case EN -> "🌐 Language";
            default -> "🌐 Мова / Language";
        });

        List<KeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboard.add(row4);

        return ReplyKeyboardMarkup.builder()
                .keyboard(keyboard)
                .resizeKeyboard(true)
                .isPersistent(true)
                .build();
    }

    public static InlineKeyboardMarkup mainMenuKeyboard(Language lang) {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "🔍 Новые квартиры";
                    case CZ -> "🔍 Nové byty";
                    case EN -> "🔍 New listings";
                    default -> "🔍 Нові квартири";
                })
                .callbackData("MENU:NEW")
                .build());
        row1.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "📋 Мой фильтр";
                    case CZ -> "📋 Můj filtr";
                    case EN -> "📋 My filter";
                    default -> "📋 Мій фільтр";
                })
                .callbackData("MENU:FILTER")
                .build());

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "⭐ Избранное";
                    case CZ -> "⭐ Oblíbené";
                    case EN -> "⭐ Favorites";
                    default -> "⭐ Обране";
                })
                .callbackData("MENU:FAVORITES")
                .build());

        InlineKeyboardRow row3 = new InlineKeyboardRow();
        row3.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "⛔ Остановить поиск";
                    case CZ -> "⛔ Zastavit hledání";
                    case EN -> "⛔ Stop search";
                    default -> "⛔ Зупинити пошук";
                })
                .callbackData("MENU:STOP")
                .build());

        InlineKeyboardRow row4 = new InlineKeyboardRow();
        row4.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "🚗 Найти авто в ЧР";
                    case CZ -> "🚗 Najít auto v ČR";
                    case EN -> "🚗 Find a car in CZ";
                    default -> "🚗 Знайти авто в ЧР";
                })
                .url("https://t.me/CarRadarCZ_bot")
                .build());

        row4.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "💙 Поддержать проект";
                    case CZ -> "💙 Podpořit projekt";
                    case EN -> "💙 Support project";
                    default -> "💙 Підтримати проєкт";
                })
                .url("https://revolut.me/evzen13")
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2, row3, row4))
                .build();
    }

    public static InlineKeyboardMarkup addToFavoritesKeyboard(String token, Language lang) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "⭐ В избранное";
                    case CZ -> "⭐ Do oblíbených";
                    case EN -> "⭐ Add to favorites";
                    default -> "⭐ В обране";
                })
                .callbackData("FAV:ADD:" + token)
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
    }

    public static InlineKeyboardMarkup removeFromFavoritesKeyboard(String key, Language lang) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "❌ Убрать из избранного";
                    case CZ -> "❌ Odebrat z oblíbených";
                    case EN -> "❌ Remove from favorites";
                    default -> "❌ Прибрати з обраного";
                })
                .callbackData("FAV:REMOVE:" + key)
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
    }

    public static InlineKeyboardMarkup moreKeyboard(Language lang) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "⬇️ Показать ещё";
                    case CZ -> "⬇️ Zobrazit více";
                    case EN -> "⬇️ Show more";
                    default -> "⬇️ Показати ще";
                })
                .callbackData("MENU:MORE")
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
    }

    public static InlineKeyboardMarkup languageKeyboard() {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(InlineKeyboardButton.builder()
                .text("🇺🇦 Українська")
                .callbackData("LANG:UA")
                .build());
        row1.add(InlineKeyboardButton.builder()
                .text("🇷🇺 Русский")
                .callbackData("LANG:RU")
                .build());

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(InlineKeyboardButton.builder()
                .text("🇨🇿 Čeština")
                .callbackData("LANG:CZ")
                .build());
        row2.add(InlineKeyboardButton.builder()
                .text("🇬🇧 English")
                .callbackData("LANG:EN")
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2))
                .build();
    }

    private static InlineKeyboardButton button(String text, String data) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(data)
                .build();
    }

    public static InlineKeyboardMarkup listingKeyboard(String token, String link, Language lang) {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "🔗 Открыть объявление";
                    case CZ -> "🔗 Otevřít inzerát";
                    case EN -> "🔗 Open listing";
                    default -> "🔗 Відкрити оголошення";
                })
                .url(link)
                .build());

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "⭐ В избранное";
                    case CZ -> "⭐ Do oblíbených";
                    case EN -> "⭐ Add to favorites";
                    default -> "⭐ В обране";
                })
                .callbackData("FAV:ADD:" + token)
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2))
                .build();
    }

    public static InlineKeyboardMarkup favoriteKeyboard(String key, String link, Language lang) {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "🔗 Открыть объявление";
                    case CZ -> "🔗 Otevřít inzerát";
                    case EN -> "🔗 Open listing";
                    default -> "🔗 Відкрити оголошення";
                })
                .url(link)
                .build());

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "❌ Убрать из избранного";
                    case CZ -> "❌ Odebrat z oblíbených";
                    case EN -> "❌ Remove from favorites";
                    default -> "❌ Прибрати з обраного";
                })
                .callbackData("FAV:REMOVE:" + key)
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2))
                .build();
    }
}