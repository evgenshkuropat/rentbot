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

    public static InlineKeyboardMarkup regionsEntryKeyboard(List<Region> popularRegions, Language lang) {
        List<Region> sorted = new ArrayList<>(popularRegions);
        sorted.sort(Comparator.comparing(Region::getTitle));

        List<InlineKeyboardRow> rows = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i += 2) {
            InlineKeyboardRow row = new InlineKeyboardRow();

            Region first = sorted.get(i);
            row.add(button(first.getTitle(), "REGION:" + first.getCode()));

            if (i + 1 < sorted.size()) {
                Region second = sorted.get(i + 1);
                row.add(button(second.getTitle(), "REGION:" + second.getCode()));
            }

            rows.add(row);
        }

        rows.add(new InlineKeyboardRow(
                button(switch (lang) {
                    case RU -> "📍 Другие города";
                    case CZ -> "📍 Další města";
                    case EN -> "📍 Other cities";
                    default -> "📍 Інші міста";
                }, "REGION:OTHER")
        ));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup regionsKeyboard(List<Region> regions) {
        List<Region> sorted = new ArrayList<>(regions);
        sorted.sort(Comparator.comparing(Region::getTitle));

        List<InlineKeyboardRow> rows = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i += 2) {
            InlineKeyboardRow row = new InlineKeyboardRow();

            Region first = sorted.get(i);
            row.add(InlineKeyboardButton.builder()
                    .text(first.getTitle())
                    .callbackData("REGION:" + first.getCode())
                    .build());

            if (i + 1 < sorted.size()) {
                Region second = sorted.get(i + 1);
                row.add(InlineKeyboardButton.builder()
                        .text(second.getTitle())
                        .callbackData("REGION:" + second.getCode())
                        .build());
            }

            rows.add(row);
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
                button(switch (lang) {
                    case RU -> "🚪 Комната / подселение";
                    case CZ -> "🚪 Pokoj / spolubydlení";
                    case EN -> "🚪 Room / shared housing";
                    default -> "🚪 Кімната / співжитло";
                }, "LAYOUT:ROOM")
        ));

        rows.add(new InlineKeyboardRow(
                button(switch (lang) {
                    case RU -> "🏠 1+kk / 1+1";
                    case CZ -> "🏠 1+kk / 1+1";
                    case EN -> "🏠 1+kk / 1+1";
                    default -> "🏠 1+kk / 1+1";
                }, "LAYOUT:1"),
                button(switch (lang) {
                    case RU -> "🏠 2+kk / 2+1";
                    case CZ -> "🏠 2+kk / 2+1";
                    case EN -> "🏠 2+kk / 2+1";
                    default -> "🏠 2+kk / 2+1";
                }, "LAYOUT:2")
        ));

        rows.add(new InlineKeyboardRow(
                button(switch (lang) {
                    case RU -> "🏠 3+kk / 3+1";
                    case CZ -> "🏠 3+kk / 3+1";
                    case EN -> "🏠 3+kk / 3+1";
                    default -> "🏠 3+kk / 3+1";
                }, "LAYOUT:3"),
                button(switch (lang) {
                    case RU -> "🏠 4+ и больше";
                    case CZ -> "🏠 4+ a více";
                    case EN -> "🏠 4+ and more";
                    default -> "🏠 4+ і більше";
                }, "LAYOUT:4")
        ));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup priceKeyboard(Language lang) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                button(switch (lang) {
                    case RU -> "Без лимита";
                    case CZ -> "Bez limitu";
                    case EN -> "No limit";
                    default -> "Без ліміту";
                }, "PRICE:0")
        ));

        rows.add(new InlineKeyboardRow(
                button("15 000 Kč", "PRICE:15000"),
                button("18 000 Kč", "PRICE:18000")
        ));

        rows.add(new InlineKeyboardRow(
                button("20 000 Kč", "PRICE:20000"),
                button("25 000 Kč", "PRICE:25000")
        ));

        rows.add(new InlineKeyboardRow(
                button("30 000 Kč", "PRICE:30000")
        ));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup confirmKeyboard(Language lang) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                button(switch (lang) {
                    case RU -> "🔔 Включить уведомления";
                    case CZ -> "🔔 Zapnout upozornění";
                    case EN -> "🔔 Enable notifications";
                    default -> "🔔 Увімкнути сповіщення";
                }, "CONFIRM:SUBSCRIBE")
        ));

        rows.add(new InlineKeyboardRow(
                button(switch (lang) {
                    case RU -> "📋 Мой фильтр";
                    case CZ -> "📋 Můj filtr";
                    case EN -> "📋 My filter";
                    default -> "📋 Мій фільтр";
                }, "CONFIRM:SHOW"),
                button(switch (lang) {
                    case RU -> "🔄 Изменить";
                    case CZ -> "🔄 Změnit";
                    case EN -> "🔄 Change";
                    default -> "🔄 Змінити";
                }, "EDIT:FILTER")
        ));

        rows.add(new InlineKeyboardRow(
                button(switch (lang) {
                    case RU -> "⛔ Остановить";
                    case CZ -> "⛔ Zastavit";
                    case EN -> "⛔ Stop";
                    default -> "⛔ Зупинити";
                }, "CONFIRM:STOP")
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
            case RU -> "🔍 Новые квартиры";
            case CZ -> "🔍 Nové byty";
            case EN -> "🔍 New listings";
            default -> "🔍 Нові квартири";
        });
        row2.add(switch (lang) {
            case RU -> "⭐ Избранное";
            case CZ -> "⭐ Oblíbené";
            case EN -> "⭐ Favorites";
            default -> "⭐ Обране";
        });

        KeyboardRow row3 = new KeyboardRow();
        row3.add(switch (lang) {
            case RU -> "🌐 Язык / Language";
            case CZ -> "🌐 Jazyk / Language";
            case EN -> "🌐 Language";
            default -> "🌐 Мова / Language";
        });
        row3.add(switch (lang) {
            case RU -> "🤝 Другие сервисы";
            case CZ -> "🤝 Další služby";
            case EN -> "🤝 Other services";
            default -> "🤝 Інші сервіси";
        });

        KeyboardRow row4 = new KeyboardRow();
        row4.add(switch (lang) {
            case RU -> "💎 Премиум";
            case CZ -> "💎 Premium";
            case EN -> "💎 Premium";
            default -> "💎 Преміум";
        });

        List<KeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);
        keyboard.add(row4);

        return ReplyKeyboardMarkup.builder()
                .keyboard(keyboard)
                .resizeKeyboard(true)
                .build();
    }

    public static InlineKeyboardMarkup mainMenuKeyboard(Language lang) {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(button(switch (lang) {
            case RU -> "🔍 Новые квартиры";
            case CZ -> "🔍 Nové byty";
            case EN -> "🔍 New listings";
            default -> "🔍 Нові квартири";
        }, "MENU:NEW"));

        row1.add(button(switch (lang) {
            case RU -> "📋 Мой фильтр";
            case CZ -> "📋 Můj filtr";
            case EN -> "📋 My filter";
            default -> "📋 Мій фільтр";
        }, "MENU:FILTER"));

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(button(switch (lang) {
            case RU -> "⭐ Избранное";
            case CZ -> "⭐ Oblíbené";
            case EN -> "⭐ Favorites";
            default -> "⭐ Обране";
        }, "MENU:FAVORITES"));

        row2.add(button(switch (lang) {
            case RU -> "⛔ Остановить";
            case CZ -> "⛔ Zastavit";
            case EN -> "⛔ Stop";
            default -> "⛔ Зупинити";
        }, "MENU:STOP"));

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2))
                .build();
    }

    public static InlineKeyboardMarkup editFilterKeyboard(boolean hasDistricts, Language lang) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                button(switch (lang) {
                    case RU -> "🏙 Город";
                    case CZ -> "🏙 Město";
                    case EN -> "🏙 City";
                    default -> "🏙 Місто";
                }, "EDIT:CITY"),
                button(switch (lang) {
                    case RU -> "📍 Район";
                    case CZ -> "📍 Oblast";
                    case EN -> "📍 District";
                    default -> "📍 Район";
                }, "EDIT:DISTRICT")
        ));

        rows.add(new InlineKeyboardRow(
                button(switch (lang) {
                    case RU -> "🏠 Тип";
                    case CZ -> "🏠 Typ";
                    case EN -> "🏠 Type";
                    default -> "🏠 Тип";
                }, "EDIT:LAYOUT"),
                button(switch (lang) {
                    case RU -> "💰 Цена";
                    case CZ -> "💰 Cena";
                    case EN -> "💰 Price";
                    default -> "💰 Ціна";
                }, "EDIT:PRICE")
        ));

        rows.add(new InlineKeyboardRow(
                button(switch (lang) {
                    case RU -> "🔄 Новый поиск";
                    case CZ -> "🔄 Nové hledání";
                    case EN -> "🔄 New search";
                    default -> "🔄 Новий пошук";
                }, "CONFIRM:RESET"),
                button(switch (lang) {
                    case RU -> "⛔ Остановить";
                    case CZ -> "⛔ Zastavit";
                    case EN -> "⛔ Stop";
                    default -> "⛔ Зупинити";
                }, "MENU:STOP")
        ));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    public static InlineKeyboardMarkup filterActionsKeyboard(Language lang) {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(button(switch (lang) {
            case RU -> "🔄 Новый поиск";
            case CZ -> "🔄 Nové hledání";
            case EN -> "🔄 New search";
            default -> "🔄 Новий пошук";
        }, "CONFIRM:RESET"));

        row1.add(button(switch (lang) {
            case RU -> "⛔ Остановить";
            case CZ -> "⛔ Zastavit";
            case EN -> "⛔ Stop";
            default -> "⛔ Зупинити";
        }, "MENU:STOP"));

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(button(switch (lang) {
            case RU -> "⚙️ Изменить";
            case CZ -> "⚙️ Změnit";
            case EN -> "⚙️ Change";
            default -> "⚙️ Змінити";
        }, "EDIT:FILTER"));

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2))
                .build();
    }

    public static InlineKeyboardMarkup reactivationKeyboard(Language lang) {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(button(switch (lang) {
            case RU -> "🔄 Обновить фильтр";
            case CZ -> "🔄 Upravit filtr";
            case EN -> "🔄 Update filter";
            default -> "🔄 Оновити фільтр";
        }, "EDIT:FILTER"));

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(button(switch (lang) {
            case RU -> "📋 Мой фильтр";
            case CZ -> "📋 Můj filtr";
            case EN -> "📋 My filter";
            default -> "📋 Мій фільтр";
        }, "CONFIRM:SHOW"));

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2))
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

    public static InlineKeyboardMarkup servicesInlineKeyboard(Language lang) {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "🚗 Поиск авто";
                    case CZ -> "🚗 Hledání auta";
                    case EN -> "🚗 Car search";
                    default -> "🚗 Пошук авто";
                })
                .url("https://t.me/CarRadarCZ_bot")
                .build());

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "🏠 Добавить жильё от собственника";
                    case CZ -> "🏠 Přidat bydlení od majitele";
                    case EN -> "🏠 Add owner listing";
                    default -> "🏠 Додати житло від власника";
                })
                .url("https://t.me/evzen_cz")
                .build());

        InlineKeyboardRow row3 = new InlineKeyboardRow();
        row3.add(button(switch (lang) {
            case RU -> "💙 Поддержать проект";
            case CZ -> "💙 Podpořit projekt";
            case EN -> "💙 Support project";
            default -> "💙 Підтримати проєкт";
        }, "SERVICE:SUPPORT"));

        InlineKeyboardRow row4 = new InlineKeyboardRow();
        row4.add(button(switch (lang) {
            case RU -> "🏘 Поиск недвижимости";
            case CZ -> "🏘 Hledání nemovitostí";
            case EN -> "🏘 Real estate search";
            default -> "🏘 Пошук нерухомості";
        }, "SERVICE:REAL_ESTATE"));

        InlineKeyboardRow row5 = new InlineKeyboardRow();
        row5.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "📝 Помощь с оформлением аренды";
                    case CZ -> "📝 Pomoc s nájemní smlouvou";
                    case EN -> "📝 Help with rental paperwork";
                    default -> "📝 Допомога з оформленням оренди";
                })
                .url("https://t.me/evzen_cz")
                .build());

        InlineKeyboardRow row6 = new InlineKeyboardRow();
        row6.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "💬 Связаться с автором";
                    case CZ -> "💬 Kontaktovat autora";
                    case EN -> "💬 Contact the author";
                    default -> "💬 Зв'язатися з автором";
                })
                .url("https://t.me/evzen_cz")
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2, row3, row4, row5, row6))
                .build();
    }

    public static InlineKeyboardMarkup authorContactKeyboard(Language lang) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "💬 Связаться с автором";
                    case CZ -> "💬 Kontaktovat autora";
                    case EN -> "💬 Contact the author";
                    default -> "💬 Зв'язатися з автором";
                })
                .url("https://t.me/evzen_cz")
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
    }

    public static InlineKeyboardMarkup ownerListingConfirmKeyboard() {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(button("✅ Опублікувати", "OWNER:PUBLISH"));
        row.add(button("❌ Скасувати", "OWNER:CANCEL"));

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
    }

    public static InlineKeyboardMarkup listingPagerKeyboard(String token, String link, Language lang) {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(button(switch (lang) {
            case RU -> "⬅️ Назад";
            case CZ -> "⬅️ Zpět";
            case EN -> "⬅️ Back";
            default -> "⬅️ Назад";
        }, "LISTING:PREV"));

        row1.add(button(switch (lang) {
            case RU -> "Вперёд ➡️";
            case CZ -> "Dále ➡️";
            case EN -> "Next ➡️";
            default -> "Далі ➡️";
        }, "LISTING:NEXT"));

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(button(switch (lang) {
            case RU -> "⭐ В избранное";
            case CZ -> "⭐ Do oblíbených";
            case EN -> "⭐ Favorite";
            default -> "⭐ В обране";
        }, "FAV:ADD:" + token));

        row2.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "🔗 Открыть";
                    case CZ -> "🔗 Otevřít";
                    case EN -> "🔗 Open";
                    default -> "🔗 Відкрити";
                })
                .url(link)
                .build());

        InlineKeyboardRow row3 = new InlineKeyboardRow();
        row3.add(button(switch (lang) {
            case RU -> "🔄 Изменить";
            case CZ -> "🔄 Změnit";
            case EN -> "🔄 Change";
            default -> "🔄 Змінити";
        }, "EDIT:FILTER"));

        row3.add(button(switch (lang) {
            case RU -> "⛔ Остановить";
            case CZ -> "⛔ Zastavit";
            case EN -> "⛔ Stop";
            default -> "⛔ Зупинити";
        }, "MENU:STOP"));

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2, row3))
                .build();
    }
}
