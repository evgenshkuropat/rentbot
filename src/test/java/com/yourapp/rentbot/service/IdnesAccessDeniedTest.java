package com.yourapp.rentbot.service;

import org.jsoup.Connection;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdnesAccessDeniedTest {
    private static final String URL = "https://reality.idnes.cz/s/pronajem/byty/praha/";

    @Test
    void escalatesDenialsCapsAtOneDayAndResetsAfterSuccess() throws Exception {
        Connection.Response response = mock(Connection.Response.class);
        when(response.url()).thenReturn(URI.create(URL).toURL());
        when(response.statusCode()).thenReturn(403);
        when(response.bodyStream()).thenAnswer(inv -> new java.io.BufferedInputStream(new ByteArrayInputStream("<title>Access denied</title>".getBytes())));
        IdnesParser parser = new IdnesParser() {
            @Override Connection.Response executeRequest(String url, int timeout) { return response; }
        };
        var untilField = IdnesParser.class.getDeclaredField("rateLimitedUntil");
        untilField.setAccessible(true);
        AtomicLong until = (AtomicLong) untilField.get(parser);
        for (long hours : new long[]{1, 3, 24, 24}) {
            until.set(0);
            long before = System.currentTimeMillis();
            assertThatThrownBy(() -> parser.loadDocument(URL, 1000)).isInstanceOf(IdnesParser.RateLimitedException.class);
            assertThat(until.get() - before).isBetween(hours * 3_600_000L, hours * 3_600_000L + 5000);
            assertThatThrownBy(() -> parser.loadDocument(URL, 1000)).isInstanceOf(IdnesParser.RateLimitedException.class);
        }
        verify(response, times(4)).statusCode();
        until.set(0);
        when(response.statusCode()).thenReturn(200);
        parser.loadDocument(URL, 1000);
        assertThat(until.get()).isZero();
        when(response.statusCode()).thenReturn(403);
        long before = System.currentTimeMillis();
        assertThatThrownBy(() -> parser.loadDocument(URL, 1000)).isInstanceOf(IdnesParser.RateLimitedException.class);
        assertThat(until.get() - before).isBetween(3_600_000L, 3_605_000L);
    }

    @Test
    void diagnosticFailureStillActivatesCooldown() throws Exception {
        Connection.Response response = mock(Connection.Response.class);
        when(response.url()).thenReturn(URI.create(URL).toURL());
        when(response.statusCode()).thenReturn(403);
        when(response.bodyStream()).thenThrow(new IllegalStateException("unavailable"));
        IdnesParser parser = new IdnesParser() {
            @Override Connection.Response executeRequest(String url, int timeout) { return response; }
        };
        assertThatThrownBy(() -> parser.loadDocument(URL, 1000)).isInstanceOf(IdnesParser.RateLimitedException.class);
        assertThatThrownBy(() -> parser.loadDocument(URL, 1000)).isInstanceOf(IdnesParser.RateLimitedException.class);
        verify(response, times(1)).statusCode();
    }

    @Test
    void redactsSensitiveDiagnosticsAndBoundsSingleLineOutput() {
        String result = IdnesParser.safeDiagnosticText("Blocked 192.0.2.1 user@example.com https://example.com/?token=secret token=secret\n" + "x".repeat(200));
        assertThat(result).doesNotContain("192.0.2.1", "user@example.com", "secret", "\n");
        assertThat(result.length()).isLessThanOrEqualTo(160);
    }
}
