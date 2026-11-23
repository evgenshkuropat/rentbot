package com.yourapp.rentbot.service;

import com.yourapp.rentbot.service.dto.ListingSourceCounts;
import com.yourapp.rentbot.service.dto.ParserRunStats;
import com.yourapp.rentbot.service.dto.SchedulerRunStats;

public final class AdminStatisticsMessages {
    private AdminStatisticsMessages() { }

    public static String parsingStats(ParserRunStats parser, SchedulerRunStats cycle) {
        return """
                📡 Останній збір оголошень (один регіон):
                Sreality raw: %d
                iDNES raw: %d
                Bezrealitky raw: %d
                Bazoš raw: %d
                DigiReality raw: %d
                RealFree raw: %d

                🔁 Після дедуплікації цього збору:
                By link: %d
                By signature: %d

                🧪 Останній завершений цикл до diversify:
                %s

                🎯 Останній завершений цикл у фінальній видачі:
                %s

                📬 Останній завершений цикл розсилки:
                Оброблено користувачів: %d
                Зі співпадіннями: %d
                Запусків збору за регіонами: %d
                Кандидатів у фінальній видачі: %d
                Викликів перевірки та надсилання: %d
                Нових успішно надіслано: %d
                Пропущено через ліміт надсилання: %d
                Після фільтрів: %d
                У фінальній видачі: %d
                Підтверджені власники — попадань у фінальну видачу: %d
                Користувачів зі співпадіннями від підтверджених власників: %d

                Попадання рахуються для кожного користувача; до diversify — для кожного пошуку.
                """.formatted(parser.srealityRaw(), parser.idnesRaw(), parser.bezrealitkyRaw(), parser.bazosRaw(),
                parser.digirealityRaw(), parser.realfreeRaw(), parser.afterDedupeByLink(), parser.afterDedupeBySignature(),
                sourceCounts(cycle.filteredBaseSources()), sourceCounts(cycle.finalSources()),
                cycle.usersProcessed(), cycle.usersWithMatches(), cycle.parserRuns(), cycle.totalCandidates(),
                cycle.totalSendAttempts(), cycle.totalSent(), cycle.totalSkippedByLimit(), cycle.aggregateFilteredBase(),
                cycle.aggregateFinal(), cycle.ownerMatches(), cycle.usersWithOwnerMatches());
    }

    private static String sourceCounts(ListingSourceCounts counts) {
        return """
                Всього: %d
                Sreality: %d
                iDNES: %d
                Bezrealitky: %d
                Bazoš: %d
                DigiReality: %d
                RealFree: %d
                Підтверджені власники (оголошення бота): %d
                Інші джерела: %d
                З ознаками власника (частина джерел вище): %d"""
                .formatted(counts.total(), counts.sreality(), counts.idnes(), counts.bezrealitky(), counts.bazos(),
                        counts.digireality(), counts.realfree(), counts.verifiedOwner(), counts.other(), counts.possibleOwner());
    }
}
