package com.tripplanner.poc.jev;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

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

    @RestClient
    LayaRestClient restClient;

    @Inject
    @ConfigProperty(name = "laya.model", defaultValue = "laya")
    String model;

    private final StubDecisionClient stub = new StubDecisionClient();

    /** Backend that answered the last {@link #evaluate(JevRequest)} call ({@code laya} or {@code stub}). */
    private volatile String lastEffectiveBackend = "laya";

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
            lastEffectiveBackend = "laya";
            return restClient.evaluate(request);
        } catch (Exception e) {
            Log.warnf("Laya sidecar call failed (%s); falling back to stub", e.getMessage());
            lastEffectiveBackend = "stub";
            return stub.evaluate(request);
        }
    }

    @Override
    public String lastEffectiveBackend() {
        return lastEffectiveBackend;
    }
}
