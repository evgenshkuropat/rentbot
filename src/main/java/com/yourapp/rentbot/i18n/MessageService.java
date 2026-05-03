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
                Map.entry("language.updated", "🌐 Мову оновлено"),

                Map.entry("menu.pinned", "Меню закріплено внизу 👇"),
                Map.entry("city.choose", "Обери місто 👇"),
                Map.entry("district.choose", "Обери район:"),
                Map.entry("layout.choose", "Обери тип житла:"),
                Map.entry("layout.room", "🚪 Окрема кімната / співжитло"),
                Map.entry("price.choose", "Обери максимальну ціну:"),
                Map.entry("search.new", "Починаємо новий пошук 🔍\nОбери місто:"),
                Map.entry("filter.reset", "Ок, давай заново. Обери місто:"),
                Map.entry("filter.start", "Супер, почнемо 🔍\nОбери місто:"),

                Map.entry("subscribe.not.enabled",
                        "🔔 Підписка ще не увімкнена\n\nНатисни «Підписатися», щоб активувати пошук квартир автоматично."),
                Map.entry("subscribe.enabled", "🔔 Сповіщення увімкнено!"),
                Map.entry("notifications.disabled", "⛔ Сповіщення вимкнено"),

                Map.entry("search.stopped", "⛔ Пошук зупинено"),
                Map.entry("search.stopped.already", "Пошук вже зупинено 🙂"),

                Map.entry("favorites.empty", "У тебе ще немає збережених оголошень ⭐"),
                Map.entry("favorites.title", "⭐ Твоє обране:"),
                Map.entry("favorites.add.failed", "Не вдалося додати в обране 😕"),
                Map.entry("favorites.added", "⭐ Додано в обране"),
                Map.entry("favorites.already.exists", "Це оголошення вже є в обраному 🙂"),
                Map.entry("favorites.remove.failed", "Не вдалося знайти оголошення для видалення 😕"),
                Map.entry("favorites.removed", "❌ Видалено з обраного"),
                Map.entry("favorites.already.removed", "Оголошення вже відсутнє в обраному 🙂"),
                Map.entry("favorites.remove.error", "Помилка при видаленні з обраного 😕"),

                Map.entry("search.test.empty", "Нічого не знайшов 😕"),
                Map.entry("search.new.empty", "Нічого нового не знайшов 😕"),
                Map.entry("search.results.saved.empty", "Немає збережених результатів пошуку 😕"),
                Map.entry("search.all.shown", "Це всі оголошення ✅"),
                Map.entry("search.more", "Показати ще?"),
                Map.entry("search.error", "Помилка при пошуку квартир 😕"),
                Map.entry("search.test.error.prefix", "Помилка тесту: "),
                Map.entry("search.found.prefix", "Знайшов "),
                Map.entry("search.found.middle", " оголошень. Показую перші "),
                Map.entry("search.found.suffix", " 👇"),

                Map.entry("callback.unknown", "Невідомий callback: "),
                Map.entry("menu.unknown.action", "Невідома дія меню 😅"),
                Map.entry("unknown.command", "Користуйся кнопками 🙂\nНатисни /start щоб почати."),
                Map.entry("access.denied", "⛔ У тебе немає доступу"),

                Map.entry("share.text",
                        "Поділитися ботом можна за цим посиланням:\nhttps://t.me/share/url?url=https://t.me/zhytloCZ_bot&text=Знайди житло в Чехії 🇨🇿"),
                Map.entry("support.text",
                        "Підтримати розвиток проєкту можна тут 💙\nhttps://revolut.me/evzen13"),
                Map.entry("menu.title", "Головне меню:"),

                Map.entry("notify.fetch.failed",
                        "⚠️ Не вдалося зараз отримати оголошення, але підписка вже увімкнена."),

                Map.entry("listing.source", "Джерело"),
                Map.entry("listing.location", "Локація"),
                Map.entry("listing.link", "Посилання"),

                Map.entry("menu.new.search", "🔄 Новий пошук"),
                Map.entry("menu.my.filter", "📋 Мій фільтр"),
                Map.entry("menu.favorites", "⭐ Обране"),
                Map.entry("menu.stop.search", "⛔ Зупинити пошук"),
                Map.entry("menu.share.bot", "📤 Поширити бота"),
                Map.entry("menu.support.project", "💙 Підтримати проєкт"),
                Map.entry("menu.language", "🌐 Мова / Language"),

                Map.entry("layout.1", "1 кімната"),
                Map.entry("layout.2", "2 кімнати"),
                Map.entry("layout.3", "3 кімнати"),
                Map.entry("layout.4", "4+"),

                Map.entry("price.unlimited", "Без ліміту"),

                Map.entry("confirm.subscribe", "✅ Підписатися"),
                Map.entry("confirm.stop", "⛔ Зупинити"),
                Map.entry("confirm.reset", "🔄 Змінити фільтр"),
                Map.entry("confirm.show", "📋 Мій фільтр"),

                Map.entry("more.show", "⬇️ Показати ще"),
                Map.entry("favorites.add", "⭐ В обране"),
                Map.entry("favorites.remove", "❌ Прибрати з обраного")
        );

        Map<String, String> ru = Map.ofEntries(
                Map.entry("language.choose", "Выберите язык 👇"),
                Map.entry("language.updated", "🌐 Язык обновлён"),

                Map.entry("menu.pinned", "Меню закреплено внизу 👇"),
                Map.entry("city.choose", "Выберите город 👇"),
                Map.entry("district.choose", "Выберите район:"),
                Map.entry("layout.choose", "Выберите тип жилья:"),
                Map.entry("layout.room", "🚪 Отдельная комната / подселение"),
                Map.entry("price.choose", "Выберите максимальную цену:"),
                Map.entry("search.new", "Начинаем новый поиск 🔍\nВыберите город:"),
                Map.entry("filter.reset", "Ок, давайте заново. Выберите город:"),
                Map.entry("filter.start", "Отлично, начнём 🔍\nВыберите город:"),

                Map.entry("subscribe.not.enabled",
                        "🔔 Подписка ещё не включена\n\nНажмите «Подписаться», чтобы получать новые квартиры автоматически."),
                Map.entry("subscribe.enabled", "🔔 Уведомления включены!"),
                Map.entry("notifications.disabled", "⛔ Уведомления отключены"),

                Map.entry("search.stopped", "⛔ Поиск остановлен"),
                Map.entry("search.stopped.already", "Поиск уже остановлен 🙂"),

                Map.entry("favorites.empty", "У вас ещё нет сохранённых объявлений ⭐"),
                Map.entry("favorites.title", "⭐ Избранное:"),
                Map.entry("favorites.add.failed", "Не удалось добавить в избранное 😕"),
                Map.entry("favorites.added", "⭐ Добавлено в избранное"),
                Map.entry("favorites.already.exists", "Это объявление уже есть в избранном 🙂"),
                Map.entry("favorites.remove.failed", "Не удалось найти объявление для удаления 😕"),
                Map.entry("favorites.removed", "❌ Удалено из избранного"),
                Map.entry("favorites.already.removed", "Объявление уже отсутствует в избранном 🙂"),
                Map.entry("favorites.remove.error", "Ошибка при удалении из избранного 😕"),

                Map.entry("search.test.empty", "Ничего не найдено 😕"),
                Map.entry("search.new.empty", "Ничего нового не найдено 😕"),
                Map.entry("search.results.saved.empty", "Нет сохранённых результатов поиска 😕"),
                Map.entry("search.all.shown", "Это все объявления ✅"),
                Map.entry("search.more", "Показать ещё?"),
                Map.entry("search.error", "Ошибка при поиске квартир 😕"),
                Map.entry("search.test.error.prefix", "Ошибка теста: "),
                Map.entry("search.found.prefix", "Найдено "),
                Map.entry("search.found.middle", " объявлений. Показываю первые "),
                Map.entry("search.found.suffix", " 👇"),

                Map.entry("callback.unknown", "Неизвестный callback: "),
                Map.entry("menu.unknown.action", "Неизвестное действие меню 😅"),
                Map.entry("unknown.command", "Используйте кнопки 🙂\nНажмите /start чтобы начать."),
                Map.entry("access.denied", "⛔ У вас нет доступа"),

                Map.entry("share.text",
                        "Поделиться ботом можно по этой ссылке:\nhttps://t.me/share/url?url=https://t.me/zhytloCZ_bot&text=Знайди житло в Чехії 🇨🇿"),
                Map.entry("support.text",
                        "Поддержать развитие проекта можно здесь 💙\nhttps://revolut.me/evzen13"),
                Map.entry("menu.title", "Главное меню:"),

                Map.entry("notify.fetch.failed",
                        "⚠️ Сейчас не удалось получить объявления, но подписка уже включена."),

                Map.entry("listing.source", "Источник"),
                Map.entry("listing.location", "Локация"),
                Map.entry("listing.link", "Ссылка"),

                Map.entry("menu.new.search", "🔄 Новый поиск"),
                Map.entry("menu.my.filter", "📋 Мой фильтр"),
                Map.entry("menu.favorites", "⭐ Избранное"),
                Map.entry("menu.stop.search", "⛔ Остановить поиск"),
                Map.entry("menu.share.bot", "📤 Поделиться ботом"),
                Map.entry("menu.support.project", "💙 Поддержать проект"),
                Map.entry("menu.language", "🌐 Язык / Language"),

                Map.entry("layout.1", "1 комната"),
                Map.entry("layout.2", "2 комнаты"),
                Map.entry("layout.3", "3 комнаты"),
                Map.entry("layout.4", "4+"),

                Map.entry("price.unlimited", "Без лимита"),

                Map.entry("confirm.subscribe", "✅ Подписаться"),
                Map.entry("confirm.stop", "⛔ Остановить"),
                Map.entry("confirm.reset", "🔄 Изменить фильтр"),
                Map.entry("confirm.show", "📋 Мой фильтр"),

                Map.entry("more.show", "⬇️ Показать ещё"),
                Map.entry("favorites.add", "⭐ В избранное"),
                Map.entry("favorites.remove", "❌ Убрать из избранного")
        );

        Map<String, String> cz = Map.ofEntries(
                Map.entry("language.choose", "Vyberte jazyk 👇"),
                Map.entry("language.updated", "🌐 Jazyk byl změněn"),

                Map.entry("menu.pinned", "Menu je připnuté dole 👇"),
                Map.entry("city.choose", "Vyber město 👇"),
                Map.entry("district.choose", "Vyber oblast:"),
                Map.entry("layout.choose", "Vyber typ bydlení:"),
                Map.entry("layout.room", "🚪 Samostatný pokoj / spolubydlení"),
                Map.entry("price.choose", "Vyber maximální cenu:"),
                Map.entry("search.new", "Začínáme nové hledání 🔍\nVyber město:"),
                Map.entry("filter.reset", "Dobře, začneme znovu. Vyber město:"),
                Map.entry("filter.start", "Super, začneme 🔍\nVyber město:"),

                Map.entry("subscribe.not.enabled",
                        "🔔 Odběr ještě není aktivní\n\nKlikni na „Odebírat“, aby ses dozvěděl o nových bytech automaticky."),
                Map.entry("subscribe.enabled", "🔔 Odběr zapnut!"),
                Map.entry("notifications.disabled", "⛔ Upozornění vypnuta"),

                Map.entry("search.stopped", "⛔ Hledání zastaveno"),
                Map.entry("search.stopped.already", "Hledání už je zastavené 🙂"),

                Map.entry("favorites.empty", "Zatím nemáš žádné uložené inzeráty ⭐"),
                Map.entry("favorites.title", "⭐ Oblíbené:"),
                Map.entry("favorites.add.failed", "Nepodařilo se přidat do oblíbených 😕"),
                Map.entry("favorites.added", "⭐ Přidáno do oblíbených"),
                Map.entry("favorites.already.exists", "Tento inzerát už je v oblíbených 🙂"),
                Map.entry("favorites.remove.failed", "Nepodařilo se najít inzerát pro odstranění 😕"),
                Map.entry("favorites.removed", "❌ Odebráno z oblíbených"),
                Map.entry("favorites.already.removed", "Inzerát už v oblíbených není 🙂"),
                Map.entry("favorites.remove.error", "Chyba při odebírání z oblíbených 😕"),

                Map.entry("search.test.empty", "Nic nebylo nalezeno 😕"),
                Map.entry("search.new.empty", "Žádné nové inzeráty 😕"),
                Map.entry("search.results.saved.empty", "Nejsou uloženy žádné výsledky hledání 😕"),
                Map.entry("search.all.shown", "To jsou všechny inzeráty ✅"),
                Map.entry("search.more", "Zobrazit více?"),
                Map.entry("search.error", "Chyba při hledání bytů 😕"),
                Map.entry("search.test.error.prefix", "Chyba testu: "),
                Map.entry("search.found.prefix", "Nalezeno "),
                Map.entry("search.found.middle", " inzerátů. Zobrazuji prvních "),
                Map.entry("search.found.suffix", " 👇"),

                Map.entry("callback.unknown", "Neznámý callback: "),
                Map.entry("menu.unknown.action", "Neznámá akce menu 😅"),
                Map.entry("unknown.command", "Použij tlačítka 🙂\nStiskni /start pro začátek."),
                Map.entry("access.denied", "⛔ Nemáš přístup"),

                Map.entry("share.text",
                        "Sdílet bota můžeš pomocí tohoto odkazu:\nhttps://t.me/share/url?url=https://t.me/zhytloCZ_bot&text=Знайди житло в Чехії 🇨🇿"),
                Map.entry("support.text",
                        "Podpořit projekt můžeš tady 💙\nhttps://revolut.me/evzen13"),
                Map.entry("menu.title", "Hlavní menu:"),

                Map.entry("notify.fetch.failed",
                        "⚠️ Teď se nepodařilo načíst inzeráty, ale odběr je už zapnutý."),

                Map.entry("listing.source", "Zdroj"),
                Map.entry("listing.location", "Lokalita"),
                Map.entry("listing.link", "Odkaz"),

                Map.entry("menu.new.search", "🔄 Nové hledání"),
                Map.entry("menu.my.filter", "📋 Můj filtr"),
                Map.entry("menu.favorites", "⭐ Oblíbené"),
                Map.entry("menu.stop.search", "⛔ Zastavit hledání"),
                Map.entry("menu.share.bot", "📤 Sdílet bota"),
                Map.entry("menu.support.project", "💙 Podpořit projekt"),
                Map.entry("menu.language", "🌐 Jazyk / Language"),

                Map.entry("layout.1", "1 pokoj"),
                Map.entry("layout.2", "2 pokoje"),
                Map.entry("layout.3", "3 pokoje"),
                Map.entry("layout.4", "4+"),

                Map.entry("price.unlimited", "Bez limitu"),

                Map.entry("confirm.subscribe", "✅ Odebírat"),
                Map.entry("confirm.stop", "⛔ Zastavit"),
                Map.entry("confirm.reset", "🔄 Změnit filtr"),
                Map.entry("confirm.show", "📋 Můj filtr"),

                Map.entry("more.show", "⬇️ Zobrazit více"),
                Map.entry("favorites.add", "⭐ Do oblíbených"),
                Map.entry("favorites.remove", "❌ Odebrat z oblíbených")
        );

        Map<String, String> en = Map.ofEntries(
                Map.entry("language.choose", "Choose language 👇"),
                Map.entry("language.updated", "🌐 Language updated"),

                Map.entry("menu.pinned", "Menu pinned below 👇"),
                Map.entry("city.choose", "Choose city 👇"),
                Map.entry("district.choose", "Choose district:"),
                Map.entry("layout.choose", "Choose housing type:"),
                Map.entry("layout.room", "🚪 Separate room / shared housing"),
                Map.entry("price.choose", "Choose maximum price:"),
                Map.entry("search.new", "Starting new search 🔍\nChoose city:"),
                Map.entry("filter.reset", "Okay, let’s start again. Choose city:"),
                Map.entry("filter.start", "Great, let's start 🔍\nChoose city:"),

                Map.entry("subscribe.not.enabled",
                        "🔔 Subscription is not enabled yet\n\nClick Subscribe to receive new listings automatically."),
                Map.entry("subscribe.enabled", "🔔 Notifications enabled!"),
                Map.entry("notifications.disabled", "⛔ Notifications disabled"),

                Map.entry("search.stopped", "⛔ Search stopped"),
                Map.entry("search.stopped.already", "Search already stopped 🙂"),

                Map.entry("favorites.empty", "You have no saved listings yet ⭐"),
                Map.entry("favorites.title", "⭐ Favorites:"),
                Map.entry("favorites.add.failed", "Failed to add to favorites 😕"),
                Map.entry("favorites.added", "⭐ Added to favorites"),
                Map.entry("favorites.already.exists", "This listing is already in favorites 🙂"),
                Map.entry("favorites.remove.failed", "Failed to find listing for removal 😕"),
                Map.entry("favorites.removed", "❌ Removed from favorites"),
                Map.entry("favorites.already.removed", "Listing is already missing from favorites 🙂"),
                Map.entry("favorites.remove.error", "Error removing from favorites 😕"),

                Map.entry("search.test.empty", "Nothing found 😕"),
                Map.entry("search.new.empty", "No new listings found 😕"),
                Map.entry("search.results.saved.empty", "No saved search results 😕"),
                Map.entry("search.all.shown", "These are all listings ✅"),
                Map.entry("search.more", "Show more?"),
                Map.entry("search.error", "Error searching apartments 😕"),
                Map.entry("search.test.error.prefix", "Test error: "),
                Map.entry("search.found.prefix", "Found "),
                Map.entry("search.found.middle", " listings. Showing first "),
                Map.entry("search.found.suffix", " 👇"),

                Map.entry("callback.unknown", "Unknown callback: "),
                Map.entry("menu.unknown.action", "Unknown menu action 😅"),
                Map.entry("unknown.command", "Use buttons 🙂\nPress /start to begin."),
                Map.entry("access.denied", "⛔ Access denied"),

                Map.entry("share.text",
                        "You can share the bot using this link:\nhttps://t.me/share/url?url=https://t.me/zhytloCZ_bot&text=Знайди житло в Чехії 🇨🇿"),
                Map.entry("support.text",
                        "You can support the project here 💙\nhttps://revolut.me/evzen13"),
                Map.entry("menu.title", "Main menu:"),

                Map.entry("notify.fetch.failed",
                        "⚠️ Could not fetch listings right now, but subscription is already enabled."),

                Map.entry("listing.source", "Source"),
                Map.entry("listing.location", "Location"),
                Map.entry("listing.link", "Link"),

                Map.entry("menu.new.search", "🔄 New search"),
                Map.entry("menu.my.filter", "📋 My filter"),
                Map.entry("menu.favorites", "⭐ Favorites"),
                Map.entry("menu.stop.search", "⛔ Stop search"),
                Map.entry("menu.share.bot", "📤 Share bot"),
                Map.entry("menu.support.project", "💙 Support project"),
                Map.entry("menu.language", "🌐 Language"),

                Map.entry("layout.1", "1 room"),
                Map.entry("layout.2", "2 rooms"),
                Map.entry("layout.3", "3 rooms"),
                Map.entry("layout.4", "4+"),

                Map.entry("price.unlimited", "No limit"),

                Map.entry("confirm.subscribe", "✅ Subscribe"),
                Map.entry("confirm.stop", "⛔ Stop"),
                Map.entry("confirm.reset", "🔄 Change filter"),
                Map.entry("confirm.show", "📋 My filter"),

                Map.entry("more.show", "⬇️ Show more"),
                Map.entry("favorites.add", "⭐ Add to favorites"),
                Map.entry("favorites.remove", "❌ Remove from favorites")
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