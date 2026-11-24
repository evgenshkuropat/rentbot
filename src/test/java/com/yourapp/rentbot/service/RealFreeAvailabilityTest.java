package com.yourapp.rentbot.service;

import com.yourapp.rentbot.service.dto.ListingDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;

class RealFreeAvailabilityTest {
    @Test
    void verifiesKnownLayoutAndRefreshesCachedActiveStatusBeforeFurtherDelivery() {
        AtomicLong now = new AtomicLong(100000);
        class Stub extends RealFreeParser {
            int requests;
            boolean inactive;
            Stub() { super(now::get); }
            @Override Document loadDocument(String url) {
                requests++;
                return Jsoup.parse("<h1>Byt 2+kk</h1><div class='param-item'>"
                        + "<div class='param-label'>Dispozice</div><div class='param-value'>2+kk</div></div>"
                        + (inactive ? "<h3>Inzerát již není aktuální</h3>" : ""), url);
            }
        }
        Stub parser = new Stub();
        ListingDto listing = listing("https://realfree.cz/inzerat/flat");
        assertThat(parser.availabilityFor(listing)).isEqualTo(RealFreeParser.Availability.ACTIVE);
        assertThat(parser.availabilityFor(listing)).isEqualTo(RealFreeParser.Availability.ACTIVE);
        assertThat(parser.requests).isEqualTo(1);

        parser.inactive = true;
        now.addAndGet(Duration.ofMinutes(10).toMillis());
        assertThat(parser.availabilityFor(listing)).isEqualTo(RealFreeParser.Availability.INACTIVE);
        assertThat(parser.availabilityFor(listing)).isEqualTo(RealFreeParser.Availability.INACTIVE);
        assertThat(parser.requests).isEqualTo(2);
    }

    @Test
    void unknownStatusIsCachedBrieflyAndRetriedWithoutUsingStaleActiveStatus() {
        AtomicLong now = new AtomicLong(100000);
        class Stub extends RealFreeParser {
            int requests;
            boolean fail;
            Stub() { super(now::get); }
            @Override Document loadDocument(String url) throws IOException {
                requests++;
                if (fail) throw new IOException("Timeout");
                return Jsoup.parse("<h1>Byt</h1><div class='param-item'>Dispozice</div>", url);
            }
        }
        Stub parser = new Stub();
        var listing = listing("https://realfree.cz/inzerat/flat");
        assertThat(parser.availabilityFor(listing)).isEqualTo(RealFreeParser.Availability.ACTIVE);
        parser.fail = true;
        now.addAndGet(Duration.ofMinutes(10).toMillis());
        assertThat(parser.availabilityFor(listing)).isEqualTo(RealFreeParser.Availability.UNKNOWN);
        assertThat(parser.availabilityFor(listing)).isEqualTo(RealFreeParser.Availability.UNKNOWN);
        assertThat(parser.requests).isEqualTo(2);
        now.addAndGet(Duration.ofMinutes(2).toMillis());
        assertThat(parser.availabilityFor(listing)).isEqualTo(RealFreeParser.Availability.UNKNOWN);
        assertThat(parser.requests).isEqualTo(3);
    }

    @Test
    void treatsMissingListingAsInactiveAndUnexpectedMarkupAsUnknown() {
        RealFreeParser missing = new RealFreeParser() {
            @Override Document loadDocument(String url) throws IOException {
                throw new org.jsoup.HttpStatusException("Gone", 410, url);
            }
        };
        RealFreeParser challenge = new RealFreeParser() {
            @Override Document loadDocument(String url) { return Jsoup.parse("<h1>Verification required</h1>", url); }
        };
        var listing = listing("https://realfree.cz/inzerat/flat");
        assertThat(missing.availabilityFor(listing)).isEqualTo(RealFreeParser.Availability.INACTIVE);
        assertThat(challenge.availabilityFor(listing)).isEqualTo(RealFreeParser.Availability.UNKNOWN);
    }

    @Test
    void rejectsInvalidSourceLinksWithoutNetworkCalls() {
        RealFreeParser parser = new RealFreeParser() {
            @Override Document loadDocument(String url) { throw new AssertionError("Unexpected HTTP request"); }
        };
        assertThat(parser.availabilityFor(listing("https://external.invalid/inzerat/flat")))
                .isEqualTo(RealFreeParser.Availability.UNKNOWN);
        assertThat(parser.availabilityFor(listing("https://realfree.cz/pronajem-bytu-praha")))
                .isEqualTo(RealFreeParser.Availability.UNKNOWN);
    }

    private static ListingDto listing(String url) {
        return new ListingDto("Byt 2+kk", 15000, url, "2+kk", "Praha", "", "RealFree|possible-owner", null);
    }
}
