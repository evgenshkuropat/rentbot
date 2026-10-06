package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.OwnerListing;
import com.yourapp.rentbot.i18n.Language;
import org.springframework.stereotype.Service;

@Service
public class OwnerListingAdminMessages {

    public String emptyList() {
        return "Поки немає оголошень від власників.";
    }

    public String listHeader(int count) {
        return "🏠 Оголошення від власників\n\nПоказую останні: " + count
                + "\n\nКоманди:\n/admin_owner_view ID\n/admin_owner_archive ID";
    }

    public String idRequired(String command) {
        return "Вкажи ID. Приклад: /" + command + " 7";
    }

    public String notFound(long id) {
        return "Оголошення не знайдено. ID: " + id;
    }

    public String archived(OwnerListing listing) {
        return "🗄 Оголошення приховано.\nID: " + listing.getId()
                + "\n\nВоно більше не потрапляє у видачу.";
    }

    public String alreadyProcessed() {
        return "Заявку не знайдено або вона вже оброблена.";
    }

    public String approved(OwnerListing listing) {
        return "✅ Оголошення опубліковане.\nID: " + listing.getId()
                + "\n\nВоно тепер бере участь у фільтрах як джерело «Власник».";
    }

    public String rejected(OwnerListing listing) {
        return "❌ Оголошення відхилене / відправлене в архів.\nID: " + listing.getId();
    }

    public String summary(OwnerListing listing) {
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
                listing.getId(), status(listing), region(listing), value(listing.getLocality()),
                value(listing.getLayout()), price(listing.getPriceCzk()), value(listing.getTitle()));
    }

    public String details(OwnerListing listing) {
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
                listing.getId(), status(listing), author(listing), region(listing), value(listing.getLocality()),
                value(listing.getLayout()), price(listing.getPriceCzk()), value(listing.getTitle()),
                value(listing.getDescription()), value(listing.getContact()), hasPhoto(listing) ? "є" : "немає");
    }

    public String moderationRequest(OwnerListing listing) {
        return """
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
                listing.getId(), author(listing), region(listing), value(listing.getLocality()),
                value(listing.getLayout()), price(listing.getPriceCzk()), value(listing.getTitle()),
                value(listing.getDescription()), value(listing.getContact()));
    }

    public String authorNotification(Language lang, boolean approved) {
        if (approved) {
            return switch (lang) {
                case RU -> "✅ Ваше объявление опубликовано и теперь может появляться в выдаче.";
                case CZ -> "✅ Vaše nabídka byla zveřejněna a může se zobrazovat ve výsledcích.";
                case EN -> "✅ Your listing has been published and can now appear in search results.";
                default -> "✅ Ваше оголошення опубліковано і тепер може зʼявлятися у видачі.";
            };
        }
        return switch (lang) {
            case RU -> "❌ Ваше объявление не было опубликовано после проверки.";
            case CZ -> "❌ Vaše nabídka nebyla po kontrole zveřejněna.";
            case EN -> "❌ Your listing was not published after review.";
            default -> "❌ Ваше оголошення не було опубліковано після перевірки.";
        };
    }

    public boolean hasPhoto(OwnerListing listing) {
        return listing.getPhotoFileId() != null && !listing.getPhotoFileId().isBlank();
    }

    private String status(OwnerListing listing) {
        if (listing.getStatus() == OwnerListing.Status.APPROVED) return "опубліковано";
        return listing.getApprovedAt() == null ? "очікує модерації" : "приховано";
    }

    private String author(OwnerListing listing) {
        if (listing.getCreatedByUsername() == null || listing.getCreatedByUsername().isBlank()) {
            return String.valueOf(listing.getCreatedByTelegramId());
        }
        return "@" + listing.getCreatedByUsername() + " / " + listing.getCreatedByTelegramId();
    }

    private String region(OwnerListing listing) {
        return listing.getRegion() == null ? "—" : listing.getRegion().getTitle();
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String price(Integer price) {
        return price == null ? "—" : String.format("%,d Kč", price).replace(',', ' ');
    }
}
