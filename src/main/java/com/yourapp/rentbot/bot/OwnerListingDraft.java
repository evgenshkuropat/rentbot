package com.yourapp.rentbot.bot;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.i18n.Language;

final class OwnerListingDraft {
    enum Step { CITY, LOCALITY, LAYOUT, PRICE, TITLE, DESCRIPTION, CONTACT, PHOTO, CONFIRM }

    Step step = Step.CITY;
    Region region;
    String locality;
    String layout;
    Integer priceCzk;
    String title;
    String description;
    String contact;
    String photoFileId;
    String createdByUsername;

    boolean readyToPublish() {
        return step == Step.CONFIRM
                && region != null
                && locality != null
                && layout != null
                && priceCzk != null
                && title != null
                && contact != null
                && photoFileId != null;
    }

    String stepLabel(Language lang) {
        return switch (step) {
            case CITY -> switch (lang) { case RU -> "город"; case CZ -> "město"; case EN -> "city"; default -> "місто"; };
            case LOCALITY -> switch (lang) { case RU -> "локация"; case CZ -> "lokalita"; case EN -> "location"; default -> "локація"; };
            case LAYOUT -> switch (lang) { case RU -> "тип жилья"; case CZ -> "typ bydlení"; case EN -> "housing type"; default -> "тип житла"; };
            case PRICE -> switch (lang) { case RU -> "цена"; case CZ -> "cena"; case EN -> "price"; default -> "ціна"; };
            case TITLE -> switch (lang) { case RU -> "название"; case CZ -> "název"; case EN -> "title"; default -> "назва"; };
            case DESCRIPTION -> switch (lang) { case RU -> "описание"; case CZ -> "popis"; case EN -> "description"; default -> "опис"; };
            case CONTACT -> switch (lang) { case RU -> "контакт"; case CZ -> "kontakt"; case EN -> "contact"; default -> "контакт"; };
            case PHOTO -> switch (lang) { case RU -> "фото"; case CZ -> "foto"; case EN -> "photo"; default -> "фото"; };
            case CONFIRM -> switch (lang) { case RU -> "подтверждение"; case CZ -> "potvrzení"; case EN -> "confirmation"; default -> "підтвердження"; };
        };
    }
}
