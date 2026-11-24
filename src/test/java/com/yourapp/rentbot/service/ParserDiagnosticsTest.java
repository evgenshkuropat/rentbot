package com.yourapp.rentbot.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yourapp.rentbot.domain.Region;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParserDiagnosticsTest {
    @Test
    void logsMissingRealFreeCatalogInsteadOfOnlyZeroCount() throws Exception {
        var response = response(404, "https://realfree.cz/pronajem-bytu-kolin");
        RealFreeParser parser = new RealFreeParser() {
            @Override Connection.Response executeRequest(String url) { return response; }
        };
        try (var logs = new Logs(RealFreeParser.class)) {
            assertThat(parser.fetchListings(region("KOLIN"))).isEmpty();
            assertThat(logs.text()).contains("status=404", "reason=catalog_or_listing_not_found", "reason=catalog_not_found");
        }
    }

    @Test
    void logsRealFreeCardRejectionReasonsAndEmptySelector() {
        RealFreeParser parser = new RealFreeParser();
        String html = card(1, "Brno", "Byt 2+kk", "15000 Kč")
                + card(2, "Praha", "Byt 2+kk", "Dohodou")
                + card(3, "Praha", "Byt bez dispozice", "15000 Kč");
        try (var logs = new Logs(RealFreeParser.class)) {
            parser.parsePage(Jsoup.parse(html, "https://realfree.cz"), "praha", new HashSet<>());
            assertThat(logs.text()).contains("cards=3 accepted=0 rejectedCity=1", "rejectedLayout=1 rejectedPrice=1",
                    "priceText=Dohodou", "locality=Brno");
            parser.parsePage(Jsoup.parse("<title>Verification required</title>", "https://realfree.cz"), "praha", new HashSet<>());
            assertThat(logs.text()).contains("reason=no_matching_cards", "title=Verification required", "listingLinks=0");
        }
    }

    @Test
    void logsIdnesAccessDeniedWithHttpStatusAndFinalUrl() throws Exception {
        var response = response(403, "https://reality.idnes.cz/blocked");
        IdnesParser parser = new IdnesParser() {
            @Override Connection.Response executeRequest(String url, int timeout) { return response; }
        };
        try (var logs = new Logs(IdnesParser.class)) {
            assertThatThrownBy(() -> parser.fetchListings(region("PRAHA"), null))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 403");
            assertThat(logs.text()).contains("status=403 reason=access_denied", "finalUrl=https://reality.idnes.cz/blocked");
        }
    }

    @Test
    void logsIdnesCooldownAndDoesNotMakeAnotherHttpRequest() throws Exception {
        var response = response(429, "https://reality.idnes.cz/s/pronajem/byty/praha/");
        class Stub extends IdnesParser {
            int requests;
            @Override Connection.Response executeRequest(String url, int timeout) { requests++; return response; }
        }
        Stub parser = new Stub();
        try (var logs = new Logs(IdnesParser.class)) {
            assertThatThrownBy(() -> parser.fetchListings(region("PRAHA"), null)).isInstanceOf(IdnesParser.RateLimitedException.class);
            assertThatThrownBy(() -> parser.fetchListings(region("BRNO"), null)).isInstanceOf(IdnesParser.RateLimitedException.class);
            assertThat(parser.requests).isEqualTo(1);
            assertThat(logs.text()).contains("status=429 reason=rate_limited", "cooldown activated", "skipped reason=cooldown remainingSeconds=");
        }
    }

    @Test
    void accessDeniedPausesAllIdnesRegionsWithoutRetrying() throws Exception {
        var response = response(403, "https://reality.idnes.cz/s/pronajem/byty/praha/");
        class Stub extends IdnesParser {
            int requests;
            @Override Connection.Response executeRequest(String url, int timeout) { requests++; return response; }
        }
        Stub parser = new Stub();
        try (var logs = new Logs(IdnesParser.class)) {
            assertThatThrownBy(() -> parser.fetchListings(region("PRAHA"), null))
                    .isInstanceOf(IdnesParser.RateLimitedException.class).hasMessageContaining("HTTP 403");
            assertThatThrownBy(() -> parser.fetchListings(region("BRNO"), null))
                    .isInstanceOf(IdnesParser.RateLimitedException.class);
            assertThat(parser.requests).isEqualTo(1);
            assertThat(logs.text()).contains("reason=access_denied status=403 durationSeconds=3600", "cause=access_denied");
        }
    }

    @Test
    void boundsSamplesAndRemovesUrlCredentialsAndQueryParameters() {
        assertThat(ParserDiagnostics.url("https://user:password@realfree.cz/path?token=secret#fragment"))
                .isEqualTo("https://realfree.cz/path");
        assertThat(ParserDiagnostics.text("line\r\n" + "x".repeat(300))).hasSize(160).doesNotContain("\r", "\n");
        assertThat(ParserDiagnostics.url("invalid url")).isEqualTo("invalid_url");
    }

    private static Connection.Response response(int status, String url) throws Exception {
        Connection.Response response = mock(Connection.Response.class);
        when(response.statusCode()).thenReturn(status);
        when(response.url()).thenReturn(URI.create(url).toURL());
        when(response.contentType()).thenReturn("text/html");
        return response;
    }

    private static Region region(String code) {
        Region region = new Region(); region.setCode(code); region.setTitle(code); return region;
    }

    private static String card(int id, String city, String title, String price) {
        return "<a class='rf-card' href='/inzerat/" + id + "'><h2>" + title + "</h2><p><i class='fa-map-marker-alt'></i>"
                + city + "</p><div class='font-bold'>" + price + "</div></a>";
    }

    private static class Logs implements AutoCloseable {
        private final Logger logger;
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Logs(Class<?> type) {
            logger = (Logger) LoggerFactory.getLogger(type);
            appender.start();
            logger.addAppender(appender);
        }
        String text() {
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
        }
        @Override public void close() { logger.detachAppender(appender); appender.stop(); }
    }
}
