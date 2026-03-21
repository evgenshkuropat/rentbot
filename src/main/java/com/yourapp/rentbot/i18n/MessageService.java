package com.yourapp.rentbot.i18n;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class MessageService {

    private final Map<Language, Map<String, String>> messages = new HashMap<>();

    public MessageService() {

        Map<String, String> ua = Map.ofEntries(
                Map.entry("language.choose", "Оберіть мову 👇"),
                Map.entry("menu.pinned", "Меню закріплено внизу 👇"),
                Map.entry("city.choose", "Обери місто 👇"),
                Map.entry("search.new", "Починаємо новий пошук 🔍\nОбери місто:"),
                Map.entry("subscribe.not.enabled",
                        "🔔 Підписка ще не увімкнена\n\nНатисни «Підписатися», щоб активувати пошук квартир автоматично."),
                Map.entry("subscribe.enabled", "🔔 Сповіщення увімкнено!"),
                Map.entry("search.stopped", "⛔ Пошук зупинено"),
                Map.entry("search.stopped.already", "Пошук вже зупинено 🙂"),
                Map.entry("favorites.empty", "У тебе ще немає збережених оголошень ⭐"),
                Map.entry("favorites.title", "⭐ Твоє обране:"),
                Map.entry("unknown.command", "Користуйся кнопками 🙂\nНатисни /start щоб почати."),
                Map.entry("access.denied", "⛔ У тебе немає доступу"),
                Map.entry("filter.start", "Супер, почнемо 🔍\nОбери місто:")
        );

        Map<String, String> ru = Map.ofEntries(
                Map.entry("language.choose", "Выберите язык 👇"),
                Map.entry("menu.pinned", "Меню закреплено внизу 👇"),
                Map.entry("city.choose", "Выберите город 👇"),
                Map.entry("search.new", "Начинаем новый поиск 🔍\nВыберите город:"),
                Map.entry("subscribe.not.enabled",
                        "🔔 Подписка ещё не включена\n\nНажмите «Подписаться», чтобы получать новые квартиры автоматически."),
                Map.entry("subscribe.enabled", "🔔 Уведомления включены!"),
                Map.entry("search.stopped", "⛔ Поиск остановлен"),
                Map.entry("search.stopped.already", "Поиск уже остановлен 🙂"),
                Map.entry("favorites.empty", "У вас ещё нет сохранённых объявлений ⭐"),
                Map.entry("favorites.title", "⭐ Избранное:"),
                Map.entry("unknown.command", "Используйте кнопки 🙂\nНажмите /start чтобы начать."),
                Map.entry("access.denied", "⛔ У вас нет доступа"),
                Map.entry("filter.start", "Отлично, начнём 🔍\nВыберите город:")
        );

        Map<String, String> cz = Map.ofEntries(
                Map.entry("language.choose", "Vyberte jazyk 👇"),
                Map.entry("menu.pinned", "Menu je připnuté dole 👇"),
                Map.entry("city.choose", "Vyber město 👇"),
                Map.entry("search.new", "Začínáme nové hledání 🔍\nVyber město:"),
                Map.entry("subscribe.not.enabled",
                        "🔔 Odběr ještě není aktivní\n\nKlikni na „Odebírat“, aby ses dozvěděl o nových bytech automaticky."),
                Map.entry("subscribe.enabled", "🔔 Odběr zapnut!"),
                Map.entry("search.stopped", "⛔ Hledání zastaveno"),
                Map.entry("search.stopped.already", "Hledání už je zastavené 🙂"),
                Map.entry("favorites.empty", "Zatím nemáš žádné uložené inzeráty ⭐"),
                Map.entry("favorites.title", "⭐ Oblíbené:"),
                Map.entry("unknown.command", "Použij tlačítka 🙂\nStiskni /start pro začátek."),
                Map.entry("access.denied", "⛔ Nemáš přístup"),
                Map.entry("filter.start", "Super, začneme 🔍\nVyber město:")
        );

        Map<String, String> en = Map.ofEntries(
                Map.entry("language.choose", "Choose language 👇"),
                Map.entry("menu.pinned", "Menu pinned below 👇"),
                Map.entry("city.choose", "Choose city 👇"),
                Map.entry("search.new", "Starting new search 🔍\nChoose city:"),
                Map.entry("subscribe.not.enabled",
                        "🔔 Subscription is not enabled yet\n\nClick Subscribe to receive new listings automatically."),
                Map.entry("subscribe.enabled", "🔔 Notifications enabled!"),
                Map.entry("search.stopped", "⛔ Search stopped"),
                Map.entry("search.stopped.already", "Search already stopped 🙂"),
                Map.entry("favorites.empty", "You have no saved listings yet ⭐"),
                Map.entry("favorites.title", "⭐ Favorites:"),
                Map.entry("unknown.command", "Use buttons 🙂\nPress /start to begin."),
                Map.entry("access.denied", "⛔ Access denied"),
                Map.entry("filter.start", "Great, let's start 🔍\nChoose city:")
        );

        messages.put(Language.UA, ua);
        messages.put(Language.RU, ru);
        messages.put(Language.CZ, cz);
        messages.put(Language.EN, en);
    }

    public String get(Language lang, String key) {
        return messages
                .getOrDefault(lang, messages.get(Language.UA))
                .getOrDefault(key, key);
    }
}