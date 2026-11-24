package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;

class IdnesParserTest {
    private static final String URL = "https://reality.idnes.cz/s/pronajem/byty/praha/";

    @Test
    void parsesCompleteCardsWithoutDetailsAndDeduplicatesTrackingLinks() throws Exception {
        class Stub extends IdnesParser {
            @Override Document loadDocument(String url, int timeout) { throw new AssertionError("Unexpected network request"); }
        }
        String html = card("/detail/pronajem/byt/praha/flat?tracking=1", "Pronájem bytu 2+kk", "Praha 5", "18&#8239;500 Kč")
                + card("/detail/pronajem/byt/praha/flat?tracking=2", "Pronájem bytu 2+kk", "Praha 5", "18500 Kč");
        var rows = new Stub().parsePage(Jsoup.parse(html, URL), region());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().link()).isEqualTo("https://reality.idnes.cz/detail/pronajem/byt/praha/flat");
        assertThat(rows.getFirst().priceCzk()).isEqualTo(18500);
        assertThat(rows.getFirst().layout()).isEqualTo("2+kk");
        assertThat(rows.getFirst().photoUrl()).isEqualTo("https://reality.idnes.cz/photo.jpg");
    }

    @Test
    void fillsMissingLayoutAndLocationFromDetailsWithoutInventingRequestedCity() throws Exception {
        class Stub extends IdnesParser {
            int requests;
            @Override Document loadDocument(String url, int timeout) {
                requests++;
                return Jsoup.parse("<h1>Pronájem bytu 3+kk, Brno</h1><p>19000 Kč</p>", url);
            }
        }
        Stub parser = new Stub();
        var rows = parser.parsePage(Jsoup.parse(card("/detail/pronajem/byt/praha/flat", "Pronájem bytu", "", "19000 Kč"), URL), region());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().layout()).isEqualTo("3+kk");
        assertThat(rows.getFirst().locality()).contains("Brno");
        assertThat(parser.requests).isEqualTo(1);
    }

    @Test
    void boundsDetailRequestsAndSkipsIncompleteAndExternalCards() throws Exception {
        class Stub extends IdnesParser {
            int requests;
            @Override Document loadDocument(String url, int timeout) throws IOException {
                requests++;
                throw new IOException("Unavailable");
            }
        }
        Stub parser = new Stub();
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < 12; i++) html.append(card("/detail/pronajem/byt/praha/" + i, "Pronájem bytu", "", "Dohodou"));
        html.append(card("https://external.invalid/detail/flat", "Pronájem bytu 2+kk", "Praha", "18000 Kč"));
        assertThat(parser.parsePage(Jsoup.parse(html.toString(), URL), region())).isEmpty();
        assertThat(parser.requests).isEqualTo(8);
    }

    @Test
    void cachesSearchResultsAndFetchesEntirePrahaForGroupedSearch() throws Exception {
        class Stub extends IdnesParser {
            int requests;
            @Override Document loadDocument(String url, int timeout) {
                requests++;
                assertThat(url).isEqualTo(URL);
                return Jsoup.parse(card("/detail/pronajem/byt/praha/flat", "Pronájem bytu 2+kk", "Praha 12", "18000 Kč"), url);
            }
        }
        Stub parser = new Stub();
        var group = new com.yourapp.rentbot.domain.RegionGroup(); group.setCode("PRAHA_11_15");
        assertThat(parser.fetchListings(region(), group)).hasSize(1);
        assertThat(parser.fetchListings(region(), null)).hasSize(1);
        assertThat(parser.requests).isEqualTo(1);
    }

    @Test
    void rateLimitedDetailDoesNotDiscardCompleteCardsBeforeOrAfterIt() throws Exception {
        class Stub extends IdnesParser {
            int requests;
            @Override Document loadDocument(String url, int timeout) throws IOException {
                requests++;
                throw new RateLimitedException("HTTP 429", true);
            }
        }
        Stub parser = new Stub();
        String html = card("/detail/pronajem/byt/praha/first", "Pronájem bytu 2+kk", "Praha", "18000 Kč")
                + card("/detail/pronajem/byt/praha/incomplete", "Pronájem bytu", "", "Dohodou")
                + card("/detail/pronajem/byt/praha/incomplete2", "Pronájem bytu", "", "Dohodou")
                + card("/detail/pronajem/byt/praha/last", "Pronájem bytu 3+kk", "Praha", "20000 Kč");
        assertThat(parser.parsePage(Jsoup.parse(html, URL), region())).hasSize(2);
        assertThat(parser.requests).isEqualTo(1);
    }

    @Test
    void acceptsStudioAndLocalitiesOutsideTheMajorCities() throws Exception {
        class Stub extends IdnesParser {
            @Override Document loadDocument(String url, int timeout) { throw new AssertionError("Unexpected detail"); }
        }
        var rows = new Stub().parsePage(Jsoup.parse(
                card("/detail/pronajem/byt/praha/studio", "Garsonka k pronájmu", "Beroun", "12000 Kč"), URL), region());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().layout()).isEqualTo("1+kk");
        assertThat(rows.getFirst().locality()).contains("Beroun");
    }

    @Test
    void excludesCategoryPagesEvenWhenTheyContainApartmentTitlesPricesAndLocalities() throws Exception {
        class Stub extends IdnesParser {
            int requests;
            @Override Document loadDocument(String url, int timeout) {
                requests++;
                assertThat(url).isEqualTo("https://reality.idnes.cz/detail/pronajem/byt/praha/valid");
                return Jsoup.parse("<h1>Pronájem bytu 2+kk Praha</h1><p>15000 Kč</p>", url);
            }
        }
        String html = card("/s/pronajem/komercni-nemovitosti/mlada-boleslav/", "Pronájem bytu 2+kk", "Praha", "15000 Kč")
                + card("/s/pronajem/male-objekty-garaze/nymburk/", "Pronájem bytu", "", "Dohodou")
                + card("/s/pronajem/pozemky/jihlava/", "Pronájem bytu", "", "Dohodou")
                + card("/s/pronajem/byty/praha/", "Pronájem bytu 2+kk", "Praha", "15000 Kč")
                + card("/detail/prodej/byt/praha/sale", "Pronájem bytu 2+kk", "Praha", "15000 Kč")
                + card("/detail/pronajem/komercni-nemovitosti/praha/commercial", "Pronájem bytu 2+kk", "Praha", "15000 Kč")
                + card("/detail/pronajem/byt/praha/valid", "Pronájem bytu 2+kk", "Praha", "Dohodou");
        Stub parser = new Stub();
        var rows = parser.parsePage(Jsoup.parse(html, URL), region());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().link()).endsWith("/valid");
        assertThat(rows.getFirst().priceCzk()).isEqualTo(15000);
        assertThat(parser.requests).isEqualTo(1);
    }

    @Test
    void ignoresDetailRedirectToCategoryInsteadOfUsingCategoryPrices() throws Exception {
        IdnesParser parser = new IdnesParser() {
            @Override Document loadDocument(String url, int timeout) {
                return Jsoup.parse("<h1>Pronájem bytu 2+kk Praha</h1><p>15000 Kč</p>",
                        "https://reality.idnes.cz/s/pronajem/byty/praha/");
            }
        };
        assertThat(parser.parsePage(Jsoup.parse(card("/detail/pronajem/byt/praha/removed",
                "Pronájem bytu 2+kk", "Praha", "Dohodou"), URL), region())).isEmpty();
    }

    @Test
    void recognizesObservedDetailPathAndLegacyPrefixButRejectsCatalogsAndForeignHosts() {
        assertThat(IdnesParser.isRentalDetailUrl("https://reality.idnes.cz/detail/pronajem/byt/mlada-boleslav-jicinska/6aa3db54965ffa7aba00f98c/"))
                .isTrue();
        assertThat(IdnesParser.isRentalDetailUrl("https://reality.idnes.cz/s/detail/pronajem/byt/praha/123/"))
                .isTrue();
        assertThat(IdnesParser.isRentalDetailUrl("https://reality.idnes.cz/detail/pronajem/pokoj/brno/123/"))
                .isTrue();
        assertThat(IdnesParser.isRentalDetailUrl("https://reality.idnes.cz/s/pronajem/byty/praha/?page=2")).isFalse();
        assertThat(IdnesParser.isRentalDetailUrl("https://external.invalid/detail/pronajem/byt/praha/123/")).isFalse();
        assertThat(IdnesParser.isRentalDetailUrl("https://reality.idnes.cz/detail/")).isFalse();
    }

    private static Region region() {
        Region region = new Region(); region.setCode("PRAHA"); region.setTitle("Praha"); return region;
    }

    private static String card(String href, String title, String locality, String price) {
        return "<article><a href='" + href + "'><h2>" + title + "</h2></a><p>" + locality
                + "</p><div>" + price + "</div><img src='' data-src='/photo.jpg'></article>";
    }
}
