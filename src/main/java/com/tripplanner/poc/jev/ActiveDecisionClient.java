package com.tripplanner.poc.jev;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

/**
 * The single bean the rest of the application depends on. It delegates to one of the backends,
 * chosen by the {@code decision.backend} property:
 *
 * <ul>
 *   <li>{@code jev} (default) — the hosted TypeSafe Jev API (falls back to the stub if no key).</li>
 *   <li>{@code laya} — the self-hosted Laya sidecar (falls back to the stub if it is not running).</li>
 *   <li>{@code stub} — the deterministic offline stand-in, always.</li>
 * </ul>
 *
 * Because a backend transparently degrades to the stub on error, the app stays up and answers
 * requests even when the chosen model is unavailable; the response's {@code backend} field tells
 * you which model actually decided.
 */
@ApplicationScoped
public class ActiveDecisionClient implements DecisionClient {

    private final DecisionClient delegate;

    @Inject
    public ActiveDecisionClient(JevApiClient jev, LayaDecisionClient laya, Config config) {
        String backend = config.getOptionalValue("decision.backend", String.class).orElse("jev");
        this.delegate = switch (backend.toLowerCase()) {
            case "laya" -> laya;
            case "stub" -> new StubDecisionClient();
            case "jev" -> jev;
            default -> {
                Log.warnf("Unknown decision.backend '%s'; using 'jev'", backend);
                yield jev;
            }
        };
        Log.infof("Decision backend configured to '%s' (model=%s)", delegate.backend(), delegate.defaultModel());
    }

    @Override
    public JevResponse evaluate(JevRequest request) {
        return delegate.evaluate(request);
    }

    @Override
    public String defaultModel() {
        return delegate.defaultModel();
    }

    @Override
    public String backend() {
        return delegate.backend();
    }
}
