package com.yourapp.rentbot.service;

import com.yourapp.rentbot.service.dto.ListingSourceCounts;
import com.yourapp.rentbot.service.dto.ParserRunStats;
import com.yourapp.rentbot.service.dto.SchedulerRunStats;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AdminStatisticsMessagesTest {
    @Test
    void rendersRealFreeAndOwnerCountsFromCompletedCycleInsteadOfLastFetch() {
        var counts = ListingSourceCounts.from(List.of(ListingSourceCountsTest.row("Owner"),
                ListingSourceCountsTest.row("RealFree|possible-owner"), ListingSourceCountsTest.row("DigiReality Owner")));
        var cycle = new SchedulerRunStats(1, 1, 1, 3, 3, 1, 0, 3, 3, 1, 1, counts, counts);
        var parser = new ParserRunStats(100, 0, 0, 0, 0, 100, 100, 25,
                ListingSourceCounts.empty(), ListingSourceCounts.empty());
        String text = AdminStatisticsMessages.parsingStats(parser, cycle);
        assertThat(text).contains("RealFree raw: 25", "RealFree: 1", "Всього: 3", "DigiReality: 1",
                "Підтверджені власники (оголошення бота): 1", "З ознаками власника (частина джерел вище): 1",
                "Підтверджені власники — попадань у фінальну видачу: 1");
        assertThat(text).doesNotContain("Всього: 100");
    }
}
