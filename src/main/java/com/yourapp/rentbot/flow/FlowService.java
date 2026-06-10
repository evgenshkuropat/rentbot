package com.yourapp.rentbot.flow;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.i18n.Language;
import com.yourapp.rentbot.repo.RegionRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class FlowService {

    private final UserFilterRepo repo;
    private final RegionRepo regionRepo;

    public FlowService(UserFilterRepo repo, RegionRepo regionRepo) {
        this.repo = repo;
        this.regionRepo = regionRepo;
    }

    public UserFilter getOrCreate(long userId) {
        return repo.findById(userId).orElseGet(() -> {
            UserFilter f = new UserFilter();
            f.setTelegramUserId(userId);
            f.setRegion(defaultRegion());
            f.setStep(FlowStep.CITY);
            f.setActive(false);
            f.setOnboarded(false);
            f.setUpdatedAt(Instant.now());
            return repo.save(f);
        });
    }

    public UserFilter save(UserFilter f) {
        f.setUpdatedAt(Instant.now());
        return repo.save(f);
    }

    public UserFilter reset(long userId) {
        UserFilter f = getOrCreate(userId);

        f.setActive(false);
        f.setRegion(defaultRegion());
        f.setRegionGroup(null);
        f.setLayout(null);
        f.setMaxPrice(null);
        f.setStep(FlowStep.CITY);
        f.setUpdatedAt(Instant.now());

        return repo.save(f);
    }

    private Region defaultRegion() {
        return regionRepo.findByCode("PRAHA")
                .orElseThrow(() -> new IllegalStateException("Region PRAHA not found in DB"));
    }

    public String confirmationPreview(UserFilter f, Language lang) {
        String city = f.getRegion() == null ? "-" : f.getRegion().getTitle();
        String districtLine = f.getRegionGroup() == null
                ? ""
                : switch (lang) {
                    case RU -> "📍 Район: " + f.getRegionGroup().getTitle() + "\n";
                    case CZ -> "📍 Oblast: " + f.getRegionGroup().getTitle() + "\n";
                    case EN -> "📍 Area: " + f.getRegionGroup().getTitle() + "\n";
                    default -> "📍 Район: " + f.getRegionGroup().getTitle() + "\n";
                };
        String layout = prettyLayout(f.getLayout(), lang);
        String price = prettyPrice(f.getMaxPrice(), lang);

        return switch (lang) {
            case RU -> """
✅ Почти готово!

Я буду присылать тебе новые квартиры, которые подходят под твой фильтр:

🏙 Город: %s
%s🏠 Тип: %s
💰 Бюджет: %s

Как только появится новое объявление — ты получишь сообщение.
""".formatted(city, districtLine, layout, price);
            case CZ -> """
✅ Skoro hotovo!

Budu vám posílat nové byty, které odpovídají vašemu filtru:

🏙 Město: %s
%s🏠 Typ: %s
💰 Rozpočet: %s

Jakmile se objeví nová nabídka, dostanete zprávu.
""".formatted(city, districtLine, layout, price);
            case EN -> """
✅ Almost ready!

I will send you new apartments that match your filter:

🏙 City: %s
%s🏠 Type: %s
💰 Budget: %s

As soon as a new listing appears, you will get a message.
""".formatted(city, districtLine, layout, price);
            default -> """
✅ Майже готово!

Я буду надсилати тобі нові квартири, які підходять під твій фільтр:

🏙 Місто: %s
%s🏠 Тип: %s
💰 Бюджет: %s

Щойно зʼявиться нове оголошення — ти отримаєш повідомлення.
""".formatted(city, districtLine, layout, price);
        };
    }

    public String pretty(UserFilter f, Language lang) {
        String regionTitle = f.getRegion() == null ? "—" : f.getRegion().getTitle();
        String groupTitle = f.getRegionGroup() == null ? "—" : f.getRegionGroup().getTitle();
        String layoutTitle = prettyLayout(f.getLayout(), lang);

        String priceTitle;
        if (f.getMaxPrice() == null) {
            priceTitle = "—";
        } else if (f.getMaxPrice() == 0) {
            priceTitle = switch (lang) {
                case RU -> "Без лимита";
                case CZ -> "Bez limitu";
                case EN -> "No limit";
                default -> "Без ліміту";
            };
        } else {
            priceTitle = f.getMaxPrice() + " Kč";
        }

        String activeTitle = switch (lang) {
            case RU -> f.isActive() ? "включены" : "выключены";
            case CZ -> f.isActive() ? "zapnuto" : "vypnuto";
            case EN -> f.isActive() ? "enabled" : "disabled";
            default -> f.isActive() ? "увімкнено" : "вимкнено";
        };

        String title = switch (lang) {
            case RU -> "Ваши настройки:";
            case CZ -> "Vaše nastavení:";
            case EN -> "Your settings:";
            default -> "Ваші налаштування:";
        };

        String cityLabel = switch (lang) {
            case RU -> "Город";
            case CZ -> "Město";
            case EN -> "City";
            default -> "Місто";
        };

        String districtLabel = switch (lang) {
            case RU -> "Районы";
            case CZ -> "Oblast";
            case EN -> "District";
            default -> "Райони";
        };

        String typeLabel = switch (lang) {
            case RU -> "Тип";
            case CZ -> "Typ";
            case EN -> "Type";
            default -> "Тип";
        };

        String priceLabel = switch (lang) {
            case RU -> "Цена до";
            case CZ -> "Cena do";
            case EN -> "Price up to";
            default -> "Ціна до";
        };

        String notificationsLabel = switch (lang) {
            case RU -> "Уведомления";
            case CZ -> "Upozornění";
            case EN -> "Notifications";
            default -> "Сповіщення";
        };

        return """
🧾 %s
🏙 %s: %s
📍 %s: %s
🏠 %s: %s
💰 %s: %s
🔔 %s: %s
""".formatted(
                title,
                cityLabel, regionTitle,
                districtLabel, groupTitle,
                typeLabel, layoutTitle,
                priceLabel, priceTitle,
                notificationsLabel, activeTitle
        );
    }

    private String prettyLayout(String layout, Language lang) {
        if (layout == null || layout.isBlank()) {
            return "—";
        }

        return switch (layout) {
            case "ROOM" -> switch (lang) {
                case RU -> "Комната";
                case CZ -> "Pokoj";
                case EN -> "Room";
                default -> "Кімната";
            };
            case "1" -> switch (lang) {
                case RU -> "1+kk / 1+1";
                case CZ -> "1+kk / 1+1";
                case EN -> "1+kk / 1+1";
                default -> "1+kk / 1+1";
            };
            case "2" -> switch (lang) {
                case RU -> "2+kk / 2+1";
                case CZ -> "2+kk / 2+1";
                case EN -> "2+kk / 2+1";
                default -> "2+kk / 2+1";
            };
            case "3" -> switch (lang) {
                case RU -> "3+kk / 3+1";
                case CZ -> "3+kk / 3+1";
                case EN -> "3+kk / 3+1";
                default -> "3+kk / 3+1";
            };
            case "4", "4+" -> switch (lang) {
                case RU -> "4+ и больше";
                case CZ -> "4+ a více";
                case EN -> "4+ and more";
                default -> "4+ і більше";
            };
            default -> layout;
        };
    }

    private String prettyPrice(Integer maxPrice, Language lang) {
        if (maxPrice == null) {
            return "-";
        }
        if (maxPrice == 0) {
            return switch (lang) {
                case RU -> "Без лимита";
                case CZ -> "Bez limitu";
                case EN -> "No limit";
                default -> "Без ліміту";
            };
        }
        return switch (lang) {
            case EN -> "up to " + maxPrice + " Kč";
            default -> "до " + maxPrice + " Kč";
        };
    }
}
