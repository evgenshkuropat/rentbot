package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.assertThat;

class RealFreeParserTest {
    @Test
    void parsesPricesLayoutsAndLazyImagesAndDeduplicatesLinks() {
        String card = card("/inzerat/flat", "Praha – Vinohrady", "Pronájem bytu 2 + kk", "18&#8239;500 Kč / měsíc");
        var rows = new RealFreeParser().parsePage(Jsoup.parse(card + card, "https://realfree.cz"), "praha", new HashSet<>());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().priceCzk()).isEqualTo(18500);
        assertThat(rows.getFirst().layout()).isEqualTo("2+kk");
        assertThat(rows.getFirst().photoUrl()).isEqualTo("https://realfree.cz/photo.jpg");
    }

    @Test
    void rejectsOtherCitiesDistrictsUnknownPricesAndExternalLinks() {
        String html = card("/inzerat/1", "Brno", "Byt 2+kk", "15000 Kč")
                + card("/inzerat/2", "Praha-východ", "Byt 2+kk", "15000 Kč")
                + card("/inzerat/3", "Praha", "Byt 2+kk", "Dohodou")
                + card("https://external.invalid/inzerat/4", "Praha", "Byt 2+kk", "15000 Kč");
        assertThat(new RealFreeParser().parsePage(Jsoup.parse(html, "https://realfree.cz"), "praha", new HashSet<>())).isEmpty();
    }

    @Test
    void supportsAdditionalCitiesAndCachesWholePaginatedSearch() {
        class Stub extends RealFreeParser {
            int calls;
            @Override Document loadDocument(String url) {
                calls++;
                String html = card("/inzerat/" + calls, "Kolín", "Garsonka", "12.000 CZK");
                if (calls == 1) html += "<a href='?page=2'>Next</a>";
                return Jsoup.parse(html, url);
            }
        }
        Stub parser = new Stub();
        Region region = new Region(); region.setCode("KOLIN"); region.setTitle("Kolín");
        var first = parser.fetchListings(region);
        assertThat(first).hasSize(2).allSatisfy(row -> assertThat(row.layout()).isEqualTo("1+kk"));
        assertThat(parser.fetchListings(region)).isEqualTo(first);
        assertThat(parser.calls).isEqualTo(2);
    }

    @Test
    void retainsEarlierPagesWhenNextPageFailsAndPausesRetries() {
        class Stub extends RealFreeParser {
            int calls;
            @Override Document loadDocument(String url) throws IOException {
                if (++calls > 1) throw new IOException("HTTP 429");
                return Jsoup.parse(card("/inzerat/1", "Praha", "Pokoj k pronájmu", "9000 Kč")
                        + "<a href='?page=2'>Next</a>", url);
            }
        }
        Stub parser = new Stub();
        Region region = new Region(); region.setCode("PRAHA");
        var partial = parser.fetchListings(region);
        assertThat(partial).hasSize(1);
        assertThat(parser.fetchListings(region)).isEqualTo(partial);
        assertThat(parser.calls).isEqualTo(2);
    }

    @Test
    void missingCityCatalogDoesNotPauseOtherCities() {
        class Stub extends RealFreeParser {
            int calls;
            @Override Document loadDocument(String url) throws IOException {
                calls++;
                if (url.contains("kolin")) throw new org.jsoup.HttpStatusException("Not found", 404, url);
                return Jsoup.parse(card("/inzerat/1", "Praha", "Byt 2+kk", "15000 Kč"), url);
            }
        }
        Stub parser = new Stub();
        Region kolin = new Region(); kolin.setCode("KOLIN");
        Region praha = new Region(); praha.setCode("PRAHA");
        assertThat(parser.fetchListings(kolin)).isEmpty();
        assertThat(parser.fetchListings(praha)).hasSize(1);
        assertThat(parser.calls).isEqualTo(2);
    }

    private static String card(String href, String locality, String title, String price) {
        return "<a class='rf-card' href='" + href + "'><h2>" + title + "</h2>"
                + "<p><i class='fa-map-marker-alt'></i>" + locality + "</p>"
                + "<div class='font-bold'>" + price + "</div><img data-src='/photo.jpg'></a>";
    }
}
