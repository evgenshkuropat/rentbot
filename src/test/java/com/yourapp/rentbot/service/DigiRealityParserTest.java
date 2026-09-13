package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DigiRealityParserTest {

    private final DigiRealityParser parser = new DigiRealityParser(600_000);

    @Test
    void acceptsStrictOwnerRental() {
        var listings = parser.parseRss(rss(item(
                "Pronájem bytu 2+kk, 48 m², Brno",
                "Cena: 18 500 Kč, Pronajímám svůj byt přímo jako majitel, bez provize realitní kanceláři.",
                "https://www.digireality.cz/inzerat/owner1"
        )));

        assertThat(listings).hasSize(1);
        assertThat(listings.getFirst().priceCzk()).isEqualTo(18_500);
        assertThat(listings.getFirst().layout()).isEqualTo("2+kk");
        assertThat(listings.getFirst().photoUrl()).isEqualTo("https://img.example/flat.jpg");
    }

    @Test
    void rejectsAgencyAndExistingBezrealitkySource() {
        String agency = item(
                "Pronájem bytu 1+kk, Praha",
                "Cena: 15 000 Kč, V zastoupení majitele. Provize RK činí jeden nájem.",
                "https://www.digireality.cz/inzerat/agency"
        );
        String duplicate = item(
                "Pronájem bytu 1+1, Zlín | Bezrealitky",
                "Cena: 13 000 Kč, Pronajímám svůj byt přímo bez RK.",
                "https://www.digireality.cz/inzerat/duplicate"
        );
        String agencyWordingFromFeed = item(
                "Pronájem bytu 1+kk, Pardubice",
                "Cena: 11 800 Kč, K pronájmu nabízíme byt 1+kk. Naše společnost Vám zprostředkuje prohlídku.",
                "https://www.digireality.cz/inzerat/agency-wording"
        );

        assertThat(parser.parseRss(rss(agency + duplicate + agencyWordingFromFeed))).isEmpty();
    }

    @Test
    void returnsOnlyRequestedRegion() {
        var parsed = parser.parseRss(rss(item(
                "Pronájem bytu 2+kk, Brno",
                "Cena: 18 500 Kč, Pronajímám svůj byt přímo jako majitel.",
                "https://www.digireality.cz/inzerat/brno"
        )));

        Region brno = region("BRNO", "Brno");
        Region praha = region("PRAHA", "Praha");

        assertThat(parser.toListingsForRegion(parsed, brno)).hasSize(1);
        assertThat(parser.toListingsForRegion(parsed, praha)).isEmpty();
    }

    private static Region region(String code, String title) {
        Region region = new Region();
        region.setCode(code);
        region.setTitle(title);
        return region;
    }

    private static String rss(String items) {
        return "<rss version=\"2.0\"><channel>" + items + "</channel></rss>";
    }

    private static String item(String title, String description, String link) {
        return "<item><title><![CDATA[" + title + "]]></title>"
                + "<link>" + link + "</link>"
                + "<description><![CDATA[" + description + "]]></description>"
                + "<pubDate>Fri, 11 Sep 2026 12:24:34 GMT</pubDate>"
                + "<enclosure type=\"image/jpeg\" url=\"https://img.example/flat.jpg\"/>"
                + "</item>";
    }
}
