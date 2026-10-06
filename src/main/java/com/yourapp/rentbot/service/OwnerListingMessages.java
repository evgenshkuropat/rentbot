package com.yourapp.rentbot.service;

import com.yourapp.rentbot.i18n.Language;
import org.springframework.stereotype.Service;

@Service
public class OwnerListingMessages {
    public String start(Language lang) {
        return switch (lang) {
            case RU -> "🏠 Добавить жильё\n\nЗаполните короткую анкету. После проверки объявление сможет появиться в боте для людей, которым оно подходит по фильтру.\n\n1/8 Напишите город или округ, например: Praha, Brno, Kolín, Plzeň.\n\nОтменить: /cancel";
            case CZ -> "🏠 Přidat bydlení\n\nVyplňte krátký formulář. Po kontrole se nabídka může zobrazit lidem, kterým odpovídá podle filtru.\n\n1/8 Napište město nebo okres, například: Praha, Brno, Kolín, Plzeň.\n\nZrušit: /cancel";
            case EN -> "🏠 Add listing\n\nFill in a short form. After review, the listing can appear in the bot for people whose filter matches it.\n\n1/8 Send the city or district, for example: Praha, Brno, Kolín, Plzeň.\n\nCancel: /cancel";
            default -> "🏠 Додати житло\n\nЗаповніть коротку анкету. Після перевірки оголошення може зʼявитися в боті для людей, яким воно підходить за фільтром.\n\n1/8 Напишіть місто або округ, наприклад: Praha, Brno, Kolín, Plzeň.\n\nСкасувати: /cancel";
        };
    }
    public String cancelled(Language lang) { return switch (lang) { case RU -> "Добавление объявления отменено."; case CZ -> "Přidání nabídky bylo zrušeno."; case EN -> "Listing submission cancelled."; default -> "Додавання оголошення скасовано."; }; }
    public String photoRequired(Language lang) { return switch (lang) { case RU -> "8/8 Пришлите фото квартиры. Фото обязательно для отправки на проверку."; case CZ -> "8/8 Pošlete fotku bytu. Fotka je povinná pro odeslání ke kontrole."; case EN -> "8/8 Send an apartment photo. A photo is required before review."; default -> "8/8 Надішліть фото квартири. Фото обовʼязкове для відправки на перевірку."; }; }
    public String unexpectedPhoto(Language lang, String expectedStep) { return switch (lang) { case RU -> "Фото нужно будет отправить на последнем шаге. Сейчас ожидаю: " + expectedStep + "."; case CZ -> "Fotku pošlete až v posledním kroku. Teď očekávám: " + expectedStep + "."; case EN -> "You will send the photo in the last step. Right now I am waiting for: " + expectedStep + "."; default -> "Фото потрібно буде надіслати на останньому кроці. Зараз очікую: " + expectedStep + "."; }; }
    public String regionNotFound(Language l) { return switch(l){case RU->"Не нашёл такой город/округ в базе. Напишите как в боте, например: Praha, Brno, Kolín, Plzeň.";case CZ->"Takové město nebo okres jsem v databázi nenašel. Napište ho jako v botu, například: Praha, Brno, Kolín, Plzeň.";case EN->"I could not find that city or district in the database. Write it as in the bot, for example: Praha, Brno, Kolín, Plzeň.";default->"Не знайшов таке місто/округ у базі. Напишіть як у боті, наприклад: Praha, Brno, Kolín, Plzeň.";}; }
    public String localityPrompt(Language l){return switch(l){case RU->"2/8 Локация или адрес. Например: Kolín - Kolín II, Masarykova.";case CZ->"2/8 Lokalita nebo adresa. Například: Kolín - Kolín II, Masarykova.";case EN->"2/8 Location or address. For example: Kolín - Kolín II, Masarykova.";default->"2/8 Локація або адреса. Наприклад: Kolín - Kolín II, Masarykova.";};}
    public String localityRequired(Language l){return switch(l){case RU->"Локация не может быть пустой. Напишите район, город или адрес.";case CZ->"Lokalita nesmí být prázdná. Napište část města, město nebo adresu.";case EN->"Location cannot be empty. Send the district, city, or address.";default->"Локація не може бути пустою. Напишіть район, місто або адресу.";};}
    public String layoutPrompt(Language l){return switch(l){case RU->"3/8 Тип жилья: room, 1, 2, 3 или 4.";case CZ->"3/8 Typ bydlení: room, 1, 2, 3 nebo 4.";case EN->"3/8 Housing type: room, 1, 2, 3, or 4.";default->"3/8 Тип житла: room, 1, 2, 3 або 4.";};}
    public String layoutInvalid(Language l){return switch(l){case RU->"Не понял тип. Напишите: room, 1, 2, 3 или 4.";case CZ->"Nerozumím typu. Napište: room, 1, 2, 3 nebo 4.";case EN->"I did not understand the type. Send: room, 1, 2, 3, or 4.";default->"Не зрозумів тип. Напишіть: room, 1, 2, 3 або 4.";};}
}
