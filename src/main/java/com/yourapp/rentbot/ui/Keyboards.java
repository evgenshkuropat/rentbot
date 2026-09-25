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
            int leftOrder = prahaGroupOrder(a.getCode());
            int rightOrder = prahaGroupOrder(b.getCode());
            if (leftOrder != rightOrder) return Integer.compare(leftOrder, rightOrder);
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

    private static int prahaGroupOrder(String code) {
        return switch (code) {
            case "PRAHA_ALL" -> 0;
            case "PRAHA_1_3" -> 1;
            case "PRAHA_4_6" -> 2;
            case "PRAHA_7_10" -> 3;
            case "PRAHA_11_15" -> 4;
            case "PRAHA_16_18" -> 5;
            case "PRAHA_19_22" -> 6;
            default -> 100;
        };
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

    public static ReplyKeyboardMarkup persistentNavKeyboard(Language lang) {
        KeyboardRow row1 = new KeyboardRow();
        row1.add(switch (lang) {
            case RU -> "🔍 Проверить новые";
            case CZ -> "🔍 Zkontrolovat nové";
            case EN -> "🔍 Check new";
            default -> "🔍 Перевірити нові";
        });
        row1.add(switch (lang) {
            case RU -> "📋 Мой поиск";
            case CZ -> "📋 Moje hledání";
            case EN -> "📋 My search";
            default -> "📋 Мій пошук";
        });

        KeyboardRow row2 = new KeyboardRow();
        row2.add(switch (lang) {
            case RU -> "⭐ Избранное";
            case CZ -> "⭐ Oblíbené";
            case EN -> "⭐ Favorites";
            default -> "⭐ Обране";
        });
        row2.add(switch (lang) {
            case RU -> "⚙️ Настроить поиск";
            case CZ -> "⚙️ Nastavit hledání";
            case EN -> "⚙️ Set up search";
            default -> "⚙️ Налаштувати пошук";
        });

        KeyboardRow row3 = new KeyboardRow();
        row3.add(switch (lang) {
            case RU -> "🏠 Добавить жильё";
            case CZ -> "🏠 Přidat bydlení";
            case EN -> "🏠 Add listing";
            default -> "🏠 Додати житло";
        });
        row3.add(switch (lang) {
            case RU -> "💎 Премиум";
            case CZ -> "💎 Premium";
            case EN -> "💎 Premium";
            default -> "💎 Преміум";
        });

        KeyboardRow row4 = new KeyboardRow();
        row4.add(switch (lang) {
            case RU -> "🤝 Другие сервисы";
            case CZ -> "🤝 Další služby";
            case EN -> "🤝 Other services";
            default -> "🤝 Інші сервіси";
        });

        KeyboardRow row5 = new KeyboardRow();
        row5.add(switch (lang) {
            case RU -> "💙 Поддержать бота";
            case CZ -> "💙 Podpořit bota";
            case EN -> "💙 Support the bot";
            default -> "💙 Підтримати бота";
        });
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
        keyboard.add(row5);

        return ReplyKeyboardMarkup.builder()
                .keyboard(keyboard)
                .resizeKeyboard(true)
                .build();
    }

    public static InlineKeyboardMarkup premiumRegionsKeyboard(List<Region> regions) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        List<Region> sorted = new ArrayList<>(regions);
        sorted.sort(Comparator.comparing(Region::getTitle));
        for (int i = 0; i < sorted.size(); i += 2) {
            InlineKeyboardRow row = new InlineKeyboardRow();
            row.add(button(sorted.get(i).getTitle(), "PREMIUM:REGION:" + sorted.get(i).getCode()));
            if (i + 1 < sorted.size()) row.add(button(sorted.get(i + 1).getTitle(), "PREMIUM:REGION:" + sorted.get(i + 1).getCode()));
            rows.add(row);
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup premiumRegionGroupsKeyboard(List<RegionGroup> groups) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (RegionGroup group : groups) {
            rows.add(new InlineKeyboardRow(button(group.getTitle(), "PREMIUM:GROUP:" + group.getCode())));
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup searchesKeyboard(boolean secondSearchConfigured, Language lang) {
        InlineKeyboardRow main = new InlineKeyboardRow(button(switch (lang) {
            case RU -> "1️⃣ Основной поиск";
            case CZ -> "1️⃣ Hlavní hledání";
            case EN -> "1️⃣ Main search";
            default -> "1️⃣ Основний пошук";
        }, "SEARCH:MAIN"));
        InlineKeyboardRow premium = new InlineKeyboardRow(button(switch (lang) {
            case RU -> secondSearchConfigured ? "2️⃣ Premium-поиск" : "➕ Настроить Premium-поиск";
            case CZ -> secondSearchConfigured ? "2️⃣ Premium hledání" : "➕ Nastavit Premium hledání";
            case EN -> secondSearchConfigured ? "2️⃣ Premium search" : "➕ Set up Premium search";
            default -> secondSearchConfigured ? "2️⃣ Premium-пошук" : "➕ Налаштувати Premium-пошук";
        }, secondSearchConfigured ? "SEARCH:PREMIUM" : "PREMIUM:SETUP"));
        return InlineKeyboardMarkup.builder().keyboard(List.of(main, premium)).build();
    }

    public static InlineKeyboardMarkup premiumSearchActionsKeyboard(Language lang) {
        return InlineKeyboardMarkup.builder().keyboard(List.of(
                new InlineKeyboardRow(button(switch (lang) {
                    case RU -> "✏️ Изменить Premium-поиск";
                    case CZ -> "✏️ Změnit Premium hledání";
                    case EN -> "✏️ Edit Premium search";
                    default -> "✏️ Змінити Premium-пошук";
                }, "PREMIUM:SETUP")),
                new InlineKeyboardRow(button(switch (lang) {
                    case RU -> "↩️ Все поиски";
                    case CZ -> "↩️ Všechna hledání";
                    case EN -> "↩️ All searches";
                    default -> "↩️ Усі пошуки";
                }, "SEARCH:LIST"))
        )).build();
    }

    public static InlineKeyboardMarkup premiumLayoutKeyboard(Language lang) {
        return InlineKeyboardMarkup.builder().keyboard(List.of(
                new InlineKeyboardRow(button("🚪 " + switch (lang) { case RU -> "Комната"; case CZ -> "Pokoj"; case EN -> "Room"; default -> "Кімната"; }, "PREMIUM:LAYOUT:ROOM")),
                new InlineKeyboardRow(button("🏠 1+kk / 1+1", "PREMIUM:LAYOUT:1"), button("🏠 2+kk / 2+1", "PREMIUM:LAYOUT:2")),
                new InlineKeyboardRow(button("🏠 3+kk / 3+1", "PREMIUM:LAYOUT:3"), button("🏠 4+", "PREMIUM:LAYOUT:4"))
        )).build();
    }

    public static InlineKeyboardMarkup premiumPriceKeyboard(Language lang) {
        return InlineKeyboardMarkup.builder().keyboard(List.of(
                new InlineKeyboardRow(button(switch (lang) { case RU -> "Без лимита"; case CZ -> "Bez limitu"; case EN -> "No limit"; default -> "Без ліміту"; }, "PREMIUM:PRICE:0")),
                new InlineKeyboardRow(button("15 000 Kč", "PREMIUM:PRICE:15000"), button("18 000 Kč", "PREMIUM:PRICE:18000")),
                new InlineKeyboardRow(button("20 000 Kč", "PREMIUM:PRICE:20000"), button("25 000 Kč", "PREMIUM:PRICE:25000")),
                new InlineKeyboardRow(button("30 000 Kč", "PREMIUM:PRICE:30000"))
        )).build();
    }

    public static InlineKeyboardMarkup premiumActiveKeyboard(Language lang) {
        return InlineKeyboardMarkup.builder().keyboard(List.of(new InlineKeyboardRow(button(switch (lang) {
            case RU -> "➕ Настроить второй поиск";
            case CZ -> "➕ Nastavit druhé hledání";
            case EN -> "➕ Set up second search";
            default -> "➕ Налаштувати другий пошук";
        }, "PREMIUM:SETUP")))).build();
    }

    public static InlineKeyboardMarkup premiumRequestKeyboard(Language lang) {
        return InlineKeyboardMarkup.builder().keyboard(List.of(new InlineKeyboardRow(button(switch (lang) {
            case RU -> "✨ Запросить тестовый доступ";
            case CZ -> "✨ Požádat o testovací přístup";
            case EN -> "✨ Request test access";
            default -> "✨ Запросити тестовий доступ";
        }, "PREMIUM:REQUEST")))).build();
    }

    public static InlineKeyboardMarkup premiumPaymentMethodsKeyboard(Language lang) {
        return InlineKeyboardMarkup.builder().keyboard(List.of(
                new InlineKeyboardRow(button("💳 Raiffeisenbank", "PREMIUM:METHOD:RAIFFEISEN")),
                new InlineKeyboardRow(button("PrivatBank", "PREMIUM:METHOD:PRIVATBANK")),
                new InlineKeyboardRow(button("PayPal", "PREMIUM:METHOD:PAYPAL")),
                new InlineKeyboardRow(button("Revolut", "PREMIUM:METHOD:REVOLUT"))
        )).build();
    }

    public static InlineKeyboardMarkup premiumPaymentConfirmationKeyboard(String method, String url, Language lang) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (url != null && !url.isBlank()) {
            rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text(switch (lang) {
                        case RU -> "Открыть " + method;
                        case CZ -> "Otevřít " + method;
                        case EN -> "Open " + method;
                        default -> "Відкрити " + method;
                    })
                    .url(url)
                    .build()));
        }
        rows.add(new InlineKeyboardRow(button(switch (lang) {
            case RU -> "✅ Я оплатил 99 Kč";
            case CZ -> "✅ Zaplatil/a jsem 99 Kč";
            case EN -> "✅ I paid 99 Kč";
            default -> "✅ Я сплатив/ла 99 Kč";
        }, "PREMIUM:PAID:" + method.toUpperCase())));
        rows.add(new InlineKeyboardRow(button(switch (lang) {
            case RU -> "⬅️ Выбрать другой способ";
            case CZ -> "⬅️ Vybrat jiný způsob";
            case EN -> "⬅️ Choose another method";
            default -> "⬅️ Обрати інший спосіб";
        }, "PREMIUM:PAY")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup premiumAdminKeyboard(long userId) {
        return InlineKeyboardMarkup.builder().keyboard(List.of(new InlineKeyboardRow(
                button("✅ Активувати на 30 днів", "PREMIUM:APPROVE:" + userId),
                button("❌ Відхилити", "PREMIUM:REJECT:" + userId)
        ))).build();
    }

    public static InlineKeyboardMarkup premiumPaymentAdminKeyboard(long requestId) {
        return InlineKeyboardMarkup.builder().keyboard(List.of(new InlineKeyboardRow(
                button("✅ Активувати на 30 днів", "PREMIUM:PAYMENT_APPROVE:" + requestId),
                button("❌ Відхилити", "PREMIUM:PAYMENT_REJECT:" + requestId)
        ))).build();
    }

    public static InlineKeyboardMarkup mainMenuKeyboard(Language lang) {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(button(switch (lang) {
            case RU -> "🔍 Проверить новые";
            case CZ -> "🔍 Zkontrolovat nové";
            case EN -> "🔍 Check new";
            default -> "🔍 Перевірити нові";
        }, "MENU:NEW"));

        row1.add(button(switch (lang) {
            case RU -> "📋 Мой поиск";
            case CZ -> "📋 Moje hledání";
            case EN -> "📋 My search";
            default -> "📋 Мій пошук";
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

        InlineKeyboardRow row3 = new InlineKeyboardRow();
        row3.add(button(switch (lang) {
            case RU -> "💙 Поддержать бота";
            case CZ -> "💙 Podpořit bota";
            case EN -> "💙 Support the bot";
            default -> "💙 Підтримати бота";
        }, "MENU:SUPPORT"));

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2, row3))
                .build();
    }

    public static InlineKeyboardMarkup editFilterKeyboard(boolean hasDistricts, Language lang) {
        List<InlineKeyboardRow> rows = new ArrayList<>();

        InlineKeyboardRow locationRow = new InlineKeyboardRow();
        locationRow.add(button(switch (lang) {
            case RU -> "🏙 Город";
            case CZ -> "🏙 Město";
            case EN -> "🏙 City";
            default -> "🏙 Місто";
        }, "EDIT:CITY"));
        if (hasDistricts) {
            locationRow.add(button(switch (lang) {
                case RU -> "📍 Район";
                case CZ -> "📍 Oblast";
                case EN -> "📍 District";
                default -> "📍 Район";
            }, "EDIT:DISTRICT"));
        }
        rows.add(locationRow);

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
                    case RU -> "🔄 Начать поиск заново";
                    case CZ -> "🔄 Začít hledání znovu";
                    case EN -> "🔄 Start search again";
                    default -> "🔄 Почати пошук заново";
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
            case RU -> "🔄 Начать поиск заново";
            case CZ -> "🔄 Začít hledání znovu";
            case EN -> "🔄 Start search again";
            default -> "🔄 Почати пошук заново";
        }, "CONFIRM:RESET"));

        row1.add(button(switch (lang) {
            case RU -> "⛔ Остановить";
            case CZ -> "⛔ Zastavit";
            case EN -> "⛔ Stop";
            default -> "⛔ Зупинити";
        }, "MENU:STOP"));

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(button(switch (lang) {
            case RU -> "✏️ Изменить параметры";
            case CZ -> "✏️ Změnit parametry";
            case EN -> "✏️ Change parameters";
            default -> "✏️ Змінити параметри";
        }, "EDIT:FILTER"));

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2))
                .build();
    }

    public static InlineKeyboardMarkup stopConfirmationKeyboard(Language lang) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(button(switch (lang) {
            case RU -> "✅ Да, остановить";
            case CZ -> "✅ Ano, zastavit";
            case EN -> "✅ Yes, stop";
            default -> "✅ Так, зупинити";
        }, "STOP:CONFIRM"));
        row.add(button(switch (lang) {
            case RU -> "↩️ Отмена";
            case CZ -> "↩️ Zrušit";
            case EN -> "↩️ Cancel";
            default -> "↩️ Скасувати";
        }, "STOP:CANCEL"));

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
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
            case RU -> "📋 Мой поиск";
            case CZ -> "📋 Moje hledání";
            case EN -> "📋 My search";
            default -> "📋 Мій пошук";
        }, "CONFIRM:SHOW"));

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2))
                .build();
    }

    public static InlineKeyboardMarkup milestone1500Keyboard(Language lang) {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "📤 Поделиться ботом";
                    case CZ -> "📤 Sdílet bot";
                    case EN -> "📤 Share bot";
                    default -> "📤 Поширити бота";
                })
                .url("https://t.me/share/url?url=https%3A%2F%2Ft.me%2FzhytloCZ_bot")
                .build());

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(button(switch (lang) {
            case RU -> "💙 Поддержать проект";
            case CZ -> "💙 Podpořit projekt";
            case EN -> "💙 Support project";
            default -> "💙 Підтримати проєкт";
        }, "SERVICE:SUPPORT"));

        InlineKeyboardRow row3 = new InlineKeyboardRow();
        row3.add(button(switch (lang) {
            case RU -> "💎 Premium";
            case CZ -> "💎 Premium";
            case EN -> "💎 Premium";
            default -> "💎 Преміум";
        }, "SERVICE:NO_AGENT"));

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row1, row2, row3))
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
        row2.add(button(switch (lang) {
            case RU -> "🇺🇦 Запись в ДП Документ";
            case CZ -> "🇺🇦 Rezervace DP Dokument";
            case EN -> "🇺🇦 DP Document appointments";
            default -> "🇺🇦 Запис у ДП Документ";
        }, "SERVICE:DP_DOCUMENT"));

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

    public static InlineKeyboardMarkup dpDocumentKeyboard(Language lang) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "📲 Открыть канал";
                    case CZ -> "📲 Otevřít kanál";
                    case EN -> "📲 Open channel";
                    default -> "📲 Відкрити канал";
                })
                .url("https://t.me/dpdoc_prague")
                .build());

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
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

    public static InlineKeyboardMarkup supportKeyboard(Language lang) {
        InlineKeyboardRow raiffeisen = new InlineKeyboardRow();
        raiffeisen.add(button("💳 Raiffeisenbank", "SUPPORT:RAIFFEISEN"));

        InlineKeyboardRow privatBank = new InlineKeyboardRow();
        privatBank.add(button("PrivatBank", "SUPPORT:PRIVATBANK"));

        InlineKeyboardRow paypal = new InlineKeyboardRow();
        paypal.add(button("PayPal", "SUPPORT:PAYPAL"));

        InlineKeyboardRow revolut = new InlineKeyboardRow();
        revolut.add(button("Revolut", "SUPPORT:REVOLUT"));

        InlineKeyboardRow back = new InlineKeyboardRow();
        back.add(button(switch (lang) {
            case RU -> "⬅️ Сервисы";
            case CZ -> "⬅️ Služby";
            case EN -> "⬅️ Services";
            default -> "⬅️ Сервіси";
        }, "SUPPORT:BACK"));

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(raiffeisen, privatBank, paypal, revolut, back))
                .build();
    }

    public static InlineKeyboardMarkup supportPaymentKeyboard(String paymentMethod, String url, Language lang) {
        InlineKeyboardRow open = new InlineKeyboardRow();
        open.add(InlineKeyboardButton.builder()
                .text(switch (lang) {
                    case RU -> "Открыть " + paymentMethod;
                    case CZ -> "Otevřít " + paymentMethod;
                    case EN -> "Open " + paymentMethod;
                    default -> "Відкрити " + paymentMethod;
                })
                .url(url)
                .build());

        InlineKeyboardRow back = new InlineKeyboardRow();
        back.add(button(switch (lang) {
            case RU -> "⬅️ Способы поддержки";
            case CZ -> "⬅️ Možnosti podpory";
            case EN -> "⬅️ Support options";
            default -> "⬅️ Способи підтримки";
        }, "SERVICE:SUPPORT"));

        return InlineKeyboardMarkup.builder().keyboard(List.of(open, back)).build();
    }

    public static InlineKeyboardMarkup supportPromptKeyboard(Language lang) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(button(switch (lang) {
            case RU -> "💙 Поддержать бота";
            case CZ -> "💙 Podpořit bota";
            case EN -> "💙 Support the bot";
            default -> "💙 Підтримати бота";
        }, "SERVICE:SUPPORT"));
        return InlineKeyboardMarkup.builder().keyboard(List.of(row)).build();
    }

    public static InlineKeyboardMarkup ownerListingConfirmKeyboard() {
        return ownerListingConfirmKeyboard(Language.UA);
    }

    public static InlineKeyboardMarkup ownerListingConfirmKeyboard(Language lang) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(button(switch (lang) {
            case RU -> "✅ Отправить";
            case CZ -> "✅ Odeslat";
            case EN -> "✅ Send";
            default -> "✅ Надіслати";
        }, "OWNER:SUBMIT"));
        row.add(button(switch (lang) {
            case RU -> "❌ Отменить";
            case CZ -> "❌ Zrušit";
            case EN -> "❌ Cancel";
            default -> "❌ Скасувати";
        }, "OWNER:CANCEL"));

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
    }

    public static InlineKeyboardMarkup ownerListingModerationKeyboard(Long listingId) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(button("✅ Опублікувати", "OWNER:APPROVE:" + listingId));
        row.add(button("❌ Відхилити", "OWNER:REJECT:" + listingId));

        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
    }

    public static InlineKeyboardMarkup ownerListingAdminKeyboard(Long listingId, boolean approved) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(button("👁 Переглянути", "OWNER:VIEW:" + listingId));
        if (approved) {
            row.add(button("🗄 Приховати", "OWNER:ARCHIVE:" + listingId));
        }

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
            case RU -> "✏️ Изменить параметры";
            case CZ -> "✏️ Změnit parametry";
            case EN -> "✏️ Change parameters";
            default -> "✏️ Змінити параметри";
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
