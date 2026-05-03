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
                case RU -> "1 комната";
                case CZ -> "1 pokoj";
                case EN -> "1 room";
                default -> "1 кімната";
            };
            case "2" -> switch (lang) {
                case RU -> "2 комнаты";
                case CZ -> "2 pokoje";
                case EN -> "2 rooms";
                default -> "2 кімнати";
            };
            case "3" -> switch (lang) {
                case RU -> "3 комнаты";
                case CZ -> "3 pokoje";
                case EN -> "3 rooms";
                default -> "3 кімнати";
            };
            case "4", "4+" -> switch (lang) {
                case RU -> "4+ комнаты";
                case CZ -> "4+ pokoje";
                case EN -> "4+ rooms";
                default -> "4+ кімнати";
            };
            default -> layout;
        };
    }
}