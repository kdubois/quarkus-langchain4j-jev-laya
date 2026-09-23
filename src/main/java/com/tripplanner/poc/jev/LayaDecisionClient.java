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
 * The self-hosted Laya backend. Laya is an open source (Apache 2.0) Python decision model that is
 * not callable from Java, so it runs as a small FastAPI sidecar (see {@code laya-sidecar/}) and this
 * client simply POSTs the same {@link JevRequest} shape to it. Laya's response already uses the
 * same answer schema as Jev, so it deserializes straight into {@link JevResponse}.
 *
 * When the sidecar is not running or errors, it falls back to {@link StubDecisionClient}.
 */
@Singleton
public class LayaDecisionClient implements DecisionClient {

    private static final Logger LOG = Logger.getLogger(LayaDecisionClient.class);

    // HTTP/1.1 only: the sidecar is uvicorn, which does not support the h2c upgrade that
    // HttpClient attempts by default over plain http, causing the request body to be lost.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String model;
    private final StubDecisionClient stub;

    @Inject
    public LayaDecisionClient(ObjectMapper objectMapper, Config config) {
        this.objectMapper = objectMapper;
        this.endpoint = config.getOptionalValue("laya.endpoint", String.class)
                .orElse("http://localhost:8100/v1/decision");
        this.model = config.getOptionalValue("laya.model", String.class).orElse("laya");
        this.stub = new StubDecisionClient();
    }

    @Override
    public String defaultModel() {
        return model;
    }

    @Override
    public String backend() {
        return "laya";
    }

    @Override
    public JevResponse evaluate(JevRequest request) {
        try {
            String body = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.warnf("Laya sidecar returned HTTP %d; falling back to stub. Body: %s",
                        response.statusCode(), truncate(response.body()));
                return stub.evaluate(request);
            }
            return objectMapper.readValue(response.body(), JevResponse.class);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOG.warnf("Laya sidecar call failed (%s); falling back to stub", e.getMessage());
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
