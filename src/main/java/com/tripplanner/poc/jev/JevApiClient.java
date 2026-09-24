package com.tripplanner.poc.jev;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;

/**
 * The hosted TypeSafe Jev System One backend: POSTs to {@code /v1/systemone}. Falls back to
 * {@link StubDecisionClient} when the API key is missing or the call errors, so the system degrades
 * gracefully instead of failing the request.
 */
@Singleton
public class JevApiClient implements DecisionClient {

    @RestClient
    JevRestClient restClient;

    @Inject
    @ConfigProperty(name = "jev.api-key")
    Optional<String> apiKey;

    @Inject
    @ConfigProperty(name = "jev.model", defaultValue = "jev-latest")
    String model;

    private final StubDecisionClient stub = new StubDecisionClient();

    /** Backend that answered the last {@link #evaluate(JevRequest)} call ({@code jev} or {@code stub}). */
    private volatile String lastEffectiveBackend = "jev";

    @Override
    public String defaultModel() {
        return model;
    }

    @Override
    public String backend() {
        return "jev";
    }

    public boolean isConfigured() {
        return apiKey.filter(k -> !k.isBlank()).isPresent();
    }

    @Override
    public JevResponse evaluate(JevRequest request) {
        if (!isConfigured()) {
            Log.debug("Jev API key not configured; falling back to the deterministic stub");
            lastEffectiveBackend = "stub";
            return stub.evaluate(request);
        }
        try {
            lastEffectiveBackend = "jev";
            return restClient.evaluate("Bearer " + apiKey.get(), request);
        } catch (Exception e) {
            Log.warnf("Jev call failed (%s); falling back to stub", e.getMessage());
            lastEffectiveBackend = "stub";
            return stub.evaluate(request);
        }
    }

    @Override
    public String lastEffectiveBackend() {
        return lastEffectiveBackend;
    }
}
