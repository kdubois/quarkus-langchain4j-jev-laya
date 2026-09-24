package com.tripplanner.poc.jev;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the effective-backend tracking of the hosted Jev client: the client must report
 * {@code stub} (not {@code jev}) whenever it actually answers with the stub fallback.
 */
class JevApiClientFallbackTest {

    private JevApiClient client() {
        JevApiClient client = new JevApiClient();
        client.model = "jev-latest";
        return client;
    }

    private JevRequest request() {
        return new JevRequest("book a car for next week", "jev-latest", Map.of(
                "route", JevQuestion.choice("Which specialist?",
                        Map.of("reservation", "Booking a rental", "weather", "Forecast",
                                "cost", "Pricing", "general", "Anything else"))));
    }

    @Test
    void reportsStubWhenNoKeyIsConfigured() {
        JevApiClient client = client();
        client.apiKey = Optional.empty();

        JevResponse response = client.evaluate(request());

        assertEquals("stub", client.lastEffectiveBackend());
        assertEquals("stub", response.model());
    }

    @Test
    void reportsJevWhenTheCallSucceeds() {
        JevApiClient client = client();
        client.apiKey = Optional.of("ts-test");
        client.restClient = Mockito.mock(JevRestClient.class);
        when(client.restClient.evaluate(anyString(), any()))
                .thenReturn(new JevResponse("jev-latest", Map.of(), null));

        client.evaluate(request());

        assertEquals("jev", client.lastEffectiveBackend());
    }

    @Test
    void reportsStubWhenTheCallFails() {
        JevApiClient client = client();
        client.apiKey = Optional.of("ts-test");
        client.restClient = Mockito.mock(JevRestClient.class);
        when(client.restClient.evaluate(anyString(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        JevResponse response = client.evaluate(request());

        assertEquals("stub", client.lastEffectiveBackend());
        assertEquals("stub", response.model());
    }
}
