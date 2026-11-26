package com.yourapp.rentbot.service;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

class SourceRequestGateTest {
    @Test void cooldownSurvivesCycleChangesAndHonorsRetryAfter() throws Exception {
        AtomicLong now = new AtomicLong(1000);
        SourceRequestGate gate = new SourceRequestGate("test", now::get);
        gate.observe(429, "7200");
        assertThatThrownBy(gate::check).isInstanceOf(IOException.class);
        now.addAndGet(3_600_000);
        assertThatThrownBy(gate::check).isInstanceOf(IOException.class);
        now.addAndGet(3_600_000);
        gate.check();
        gate.observe(403, null);
        now.addAndGet(900_000);
        assertThatThrownBy(gate::check).isInstanceOf(IOException.class);
        now.addAndGet(2_700_000);
        gate.check();
    }

    @Test void rejectsErrorResponsesAndStopsFurtherNetworkCalls() throws Exception {
        for (int status : new int[]{403, 429}) {
            var request = org.mockito.Mockito.mock(org.jsoup.Connection.class);
            var response = org.mockito.Mockito.mock(org.jsoup.Connection.Response.class);
            org.mockito.Mockito.when(request.execute()).thenReturn(response);
            org.mockito.Mockito.when(response.statusCode()).thenReturn(status);
            org.mockito.Mockito.when(response.url()).thenReturn(java.net.URI.create("https://example.test/").toURL());
            org.mockito.Mockito.when(response.bodyStream()).thenReturn(new java.io.BufferedInputStream(new java.io.ByteArrayInputStream(new byte[0])));
            SourceRequestGate gate = new SourceRequestGate("test");
            assertThatThrownBy(() -> gate.execute(request)).isInstanceOf(org.jsoup.HttpStatusException.class);
            assertThatThrownBy(() -> gate.execute(request)).isInstanceOf(IOException.class);
            org.mockito.Mockito.verify(request, org.mockito.Mockito.times(1)).execute();
        }
    }

    @Test void preservesSourceSpecificRateLimitPause() throws Exception {
        AtomicLong now = new AtomicLong(1);
        SourceRequestGate gate = new SourceRequestGate("rss", now::get);
        var request = org.mockito.Mockito.mock(org.jsoup.Connection.class);
        var response = org.mockito.Mockito.mock(org.jsoup.Connection.Response.class);
        org.mockito.Mockito.when(request.execute()).thenReturn(response);
        org.mockito.Mockito.when(response.statusCode()).thenReturn(200);
        gate.execute(request, 21_600_000L);
        gate.observe(429, null);
        now.addAndGet(3_600_000);
        assertThatThrownBy(gate::check).isInstanceOf(IOException.class);
        now.addAndGet(18_000_000);
        gate.check();
    }

    @Test void acceptsHttpDatesAndRejectsMalformedRetryAfter() {
        long now = Instant.parse("2026-11-26T16:00:00Z").toEpochMilli();
        assertThat(SourceRequestGate.retryAfterMillis("Thu, 26 Nov 2026 18:00:00 GMT", now)).isEqualTo(7_200_000);
        assertThat(SourceRequestGate.retryAfterMillis("nonsense", now)).isZero();
        assertThat(SourceRequestGate.retryAfterMillis("-10", now)).isZero();
        assertThat(SourceRequestGate.retryAfterMillis("999999999999", now)).isEqualTo(604_800_000);
    }
}
