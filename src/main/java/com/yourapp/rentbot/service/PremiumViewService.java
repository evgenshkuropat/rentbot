package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.PremiumSearch;
import com.yourapp.rentbot.i18n.Language;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class PremiumViewService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")
            .withZone(ZoneId.of("Europe/Prague"));

    public String overview(Language lang, Instant premiumUntil) {
        long daysLeft = Math.max(0, java.time.Duration.between(Instant.now(), premiumUntil).toDays());
        String expiry = formatInstant(premiumUntil);
        return switch (lang) {
            case RU -> "💎 Ваш Premium активен\n\nДействует до: " + expiry + "\nОсталось дней: " + daysLeft + "\n\nВключено: два независимых поиска, приоритетная обработка, до 10 новых уведомлений за цикл и проверенные предложения от владельцев первыми.";
            case CZ -> "💎 Váš Premium je aktivní\n\nPlatí do: " + expiry + "\nZbývá dní: " + daysLeft + "\n\nZahrnuje: dvě nezávislá hledání, prioritní zpracování, až 10 nových upozornění za cyklus a ověřené nabídky od majitelů jako první.";
            case EN -> "💎 Your Premium is active\n\nValid until: " + expiry + "\nDays remaining: " + daysLeft + "\n\nIncluded: two independent searches, priority processing, up to 10 new alerts per cycle, and verified owner listings first.";
            default -> "💎 Ваш Premium активний\n\nДіє до: " + expiry + "\nЗалишилось днів: " + daysLeft + "\n\nВключено: два незалежні пошуки, пріоритетна обробка, до 10 нових сповіщень за цикл та перевірені пропозиції від власників першими.";
        };
    }

    public String paymentIntro(Language lang) {
        return switch (lang) {
            case RU -> "💎 Premium — 99 Kč / месяц\n\nНе пропускайте новые варианты: два независимых поиска, приоритетная обработка, до 10 новых уведомлений за цикл и проверенные варианты от владельцев — первыми.\n\nВыберите удобный способ оплаты. Доступ активируется на 30 дней после проверки оплаты.";
            case CZ -> "💎 Premium — 99 Kč / měsíc\n\nNenechte si ujít nové nabídky: dvě nezávislá hledání, prioritní zpracování, až 10 nových upozornění za cyklus a ověřené nabídky přímo od majitelů jako první.\n\nVyberte si způsob platby. Přístup aktivuji na 30 dní po ověření platby.";
            case EN -> "💎 Premium — 99 Kč / month\n\nDo not miss new listings: two independent searches, priority processing, up to 10 new alerts per cycle, and verified owner listings first.\n\nChoose a payment method. Access is activated for 30 days after payment is verified.";
            default -> "💎 Premium — 99 Kč / місяць\n\nНе пропускайте нові варіанти: два незалежні пошуки, пріоритетна обробка, до 10 нових сповіщень за цикл і перевірені варіанти від власників — першими.\n\nОберіть зручний спосіб оплати. Доступ активується на 30 днів після перевірки оплати.";
        };
    }

    public String contextualOffer(Language lang) {
        return switch (lang) {
            case RU -> "⭐ У вас уже 3 сохранённых варианта. Хотите искать сразу по двум настройкам — например, в другом районе или с другим бюджетом?\n\n💎 Premium даёт второй независимый поиск, приоритетную обработку и до 10 новых уведомлений за цикл.";
            case CZ -> "⭐ Už máte 3 uložené nabídky. Chcete hledat podle dvou nastavení najednou — například v jiné lokalitě nebo s jiným rozpočtem?\n\n💎 Premium nabízí druhé nezávislé hledání, prioritní zpracování a až 10 nových upozornění za cyklus.";
            case EN -> "⭐ You already have 3 saved listings. Want to search with two settings at once—for another area or budget?\n\n💎 Premium includes a second independent search, priority processing, and up to 10 new alerts per cycle.";
            default -> "⭐ У вас уже 3 збережені варіанти. Хочете шукати одразу за двома налаштуваннями — наприклад, в іншому районі або з іншим бюджетом?\n\n💎 Premium дає другий незалежний пошук, пріоритетну обробку та до 10 нових сповіщень за цикл.";
        };
    }

    public String paymentInstructions(Language lang, String method, long userId) {
        String details = "RAIFFEISEN".equals(method) ? switch (lang) {
            case RU -> "Реквизиты: 972026002/5500"; case CZ -> "Účet: 972026002/5500";
            case EN -> "Account: 972026002/5500"; default -> "Рахунок: 972026002/5500";
        } : switch (lang) {
            case RU -> "Нажмите кнопку ниже и укажите сумму 99 Kč."; case CZ -> "Otevřete platební odkaz níže a zadejte částku 99 Kč.";
            case EN -> "Open the payment link below and enter 99 Kč."; default -> "Відкрийте посилання нижче та вкажіть суму 99 Kč.";
        };
        return switch (lang) {
            case RU -> "💎 Premium на 30 дней — 99 Kč\n\n" + details + "\n\nВ комментарии к платежу укажите Telegram ID: " + userId + ". После оплаты нажмите кнопку ниже.";
            case CZ -> "💎 Premium na 30 dní — 99 Kč\n\n" + details + "\n\nDo poznámky k platbě uveďte Telegram ID: " + userId + ". Po zaplacení klikněte na tlačítko níže.";
            case EN -> "💎 Premium for 30 days — 99 Kč\n\n" + details + "\n\nAdd your Telegram ID to the payment note: " + userId + ". After paying, press the button below.";
            default -> "💎 Premium на 30 днів — 99 Kč\n\n" + details + "\n\nУ коментарі до платежу вкажіть Telegram ID: " + userId + ". Після оплати натисніть кнопку нижче.";
        };
    }

    public String submitted(Language lang) { return switch (lang) {
        case RU -> "✅ Заявка отправлена. После проверки оплаты Premium будет активирован на 30 дней.";
        case CZ -> "✅ Žádost byla odeslána. Po ověření platby bude Premium aktivováno na 30 dní.";
        case EN -> "✅ Your request was sent. Premium will be activated for 30 days after payment is verified.";
        default -> "✅ Заявку надіслано. Після перевірки оплати Premium буде активовано на 30 днів.";
    }; }
    public String rejected(Language lang) { return switch (lang) {
        case RU -> "Не удалось подтвердить оплату Premium. Проверьте перевод или напишите в поддержку — поможем разобраться.";
        case CZ -> "Platbu Premium se nepodařilo potvrdit. Zkontrolujte prosím převod nebo napište podpoře — pomůžeme to vyřešit.";
        case EN -> "We could not confirm your Premium payment. Please check the transfer or contact support and we will help.";
        default -> "Не вдалося підтвердити оплату Premium. Перевірте переказ або напишіть у підтримку — допоможемо розібратися.";
    }; }
    public String activated(Language lang) { return switch (lang) {
        case RU -> "🎉 Premium-доступ активен на 30 дней. У вас до 10 новых уведомлений за цикл, приоритетная обработка и проверенные варианты от владельцев первыми. Настройте второй независимый поиск ниже.";
        case CZ -> "🎉 Premium přístup je aktivní na 30 dní. Máte až 10 nových upozornění za cyklus, prioritní zpracování a ověřené nabídky přímo od majitelů jako první. Níže si nastavte druhé samostatné hledání.";
        case EN -> "🎉 Premium access is active for 30 days. You have up to 10 new alerts per cycle, priority processing, and verified owner listings first. Set up your second independent search below.";
        default -> "🎉 Premium-доступ активний на 30 днів. У вас до 10 нових сповіщень за цикл, пріоритетна обробка та перевірені варіанти від власників першими. Нижче налаштуйте другий незалежний пошук.";
    }; }
    public String chooseRegion(Language lang) { return switch (lang) { case RU -> "💎 Второй поиск: выберите город."; case CZ -> "💎 Druhé hledání: vyberte město."; case EN -> "💎 Second search: choose a city."; default -> "💎 Другий пошук: оберіть місто."; }; }
    public String chooseLayout(Language lang) { return switch (lang) { case RU -> "💎 Второй поиск: выберите тип жилья."; case CZ -> "💎 Druhé hledání: vyberte typ bydlení."; case EN -> "💎 Second search: choose a property type."; default -> "💎 Другий пошук: оберіть тип житла."; }; }
    public String chooseDistrict(Language lang) { return switch (lang) { case RU -> "💎 Второй поиск: выберите район."; case CZ -> "💎 Druhé hledání: vyberte oblast."; case EN -> "💎 Second search: choose a district."; default -> "💎 Другий пошук: оберіть район."; }; }
    public String choosePrice(Language lang) { return switch (lang) { case RU -> "💎 Второй поиск: выберите максимальную цену."; case CZ -> "💎 Druhé hledání: vyberte maximální cenu."; case EN -> "💎 Second search: choose the maximum price."; default -> "💎 Другий пошук: оберіть максимальну ціну."; }; }
    public String searchReady(Language lang, PremiumSearch search) {
        String price = search.getMaxPrice() != null && search.getMaxPrice() > 0 ? search.getMaxPrice() + " Kč" : "—";
        String district = search.getRegionGroup() == null ? "" : "\n📍 " + search.getRegionGroup().getTitle();
        return "✅ " + switch (lang) { case RU -> "Второй поиск активен"; case CZ -> "Druhé hledání je aktivní"; case EN -> "Second search is active"; default -> "Другий пошук активний"; } + ":\n🏙 " + search.getRegion().getTitle() + district + "\n🏠 " + search.getLayout() + "\n💰 " + price;
    }
    public String searchNotConfigured(Language lang) { return switch (lang) {
        case RU -> "Второй Premium-поиск ещё не настроен."; case CZ -> "Druhé Premium hledání ještě není nastaveno.";
        case EN -> "Your second Premium search is not set up yet."; default -> "Другий Premium-пошук ще не налаштований.";
    }; }
    public boolean isPaymentMethod(String method) { return "RAIFFEISEN".equals(method) || "PRIVATBANK".equals(method) || "PAYPAL".equals(method) || "REVOLUT".equals(method); }
    public String paymentMethodTitle(String method) { return switch (method) { case "RAIFFEISEN" -> "Raiffeisenbank"; case "PRIVATBANK" -> "PrivatBank"; case "PAYPAL" -> "PayPal"; case "REVOLUT" -> "Revolut"; default -> method; }; }
    public String paymentUrl(String method) { return switch (method) { case "PRIVATBANK" -> "https://www.privat24.ua/send/47m35"; case "PAYPAL" -> "https://www.paypal.me/YEVHENSHKUROPAT"; case "REVOLUT" -> "https://revolut.me/evzen13"; default -> null; }; }
    private String formatInstant(Instant value) { return value == null ? "—" : DATE_FORMAT.format(value); }
}
