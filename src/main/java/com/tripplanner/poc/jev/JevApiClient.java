package com.tripplanner.poc.jev;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The hosted TypeSafe Jev System One backend: POSTs to {@code /v1/systemone}. Falls back to
 * {@link StubDecisionClient} when the API key is missing, the request fails to serialize, or the
 * call errors, so the system degrades gracefully instead of failing the request.
 */
@Singleton
public class JevApiClient implements DecisionClient {

    private static final Logger LOG = Logger.getLogger(JevApiClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final StubDecisionClient stub;

    @Inject
    public JevApiClient(ObjectMapper objectMapper, Config config) {
        this.objectMapper = objectMapper;
        this.apiKey = config.getOptionalValue("jev.api-key", String.class).orElse("");
        this.endpoint = config.getOptionalValue("jev.endpoint", String.class)
                .orElse("https://api.typesafe.ai/v1/systemone");
        this.model = config.getOptionalValue("jev.model", String.class).orElse("jev-latest");
        this.stub = new StubDecisionClient();
    }

    @Override
    public String defaultModel() {
        return model;
    }

    @Override
    public String backend() {
        return "jev";
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public JevResponse evaluate(JevRequest request) {
        if (!isConfigured()) {
            LOG.debug("Jev API key not configured; falling back to the deterministic stub");
            return stub.evaluate(request);
        }
        try {
            String body = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warnf("Jev returned HTTP %d; falling back to stub. Body: %s",
                        response.statusCode(), truncate(response.body()));
                return stub.evaluate(request);
            }
            return objectMapper.readValue(response.body(), JevResponse.class);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warnf("Jev call failed (%s); falling back to stub", e.getMessage());
            return stub.evaluate(request);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 300 ? value.substring(0, 300) + "..." : value;
    }
}
