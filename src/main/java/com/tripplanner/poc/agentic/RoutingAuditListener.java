package com.tripplanner.poc.agentic;

import com.tripplanner.poc.guardrails.RouteAudit;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.listener.DecisionModelResponseContext;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Records the core decision router's answers without changing them. */
public final class RoutingAuditListener implements DecisionModelListener {

    static final List<String> ROUTES = List.of("reservation", "weather", "cost", "general");

    private static final Logger LOG = Logger.getLogger(RoutingAuditListener.class);

    private final RouteAudit audit;
    private final double threshold;
    private final Supplier<String> effectiveBackend;

    public RoutingAuditListener(RouteAudit audit, double threshold, Supplier<String> effectiveBackend) {
        this.audit = audit;
        this.threshold = threshold;
        this.effectiveBackend = effectiveBackend;
    }

    @Override
    public void onResponse(DecisionModelResponseContext context) {
        record(context.decisionRequest(), context.decisionResponse());
    }

    void record(DecisionRequest request, DecisionResponse response) {
        if (!request.questions().keySet().containsAll(ROUTES)) {
            return;
        }

        Map<String, Double> probabilities = new LinkedHashMap<>();
        for (String route : ROUTES) {
            if (response.answers().get(route) instanceof YesNoAnswer answer) {
                probabilities.put(route, answer.probability());
            }
        }
        if (probabilities.size() != ROUTES.size()) {
            return;
        }

        List<String> activated = ROUTES.stream()
                .filter(route -> probabilities.get(route) >= threshold)
                .toList();
        String mode = activated.size() > 1 ? "fan-out" : activated.isEmpty() ? "fallback" : "needs";
        List<String> auditedRoutes = activated.isEmpty() ? List.of("general") : activated;
        String customerRequest = inputText(request.input());

        audit.log(customerRequest, auditedRoutes, mode, effectiveBackend.get());
        LOG.infof("DecisionRouterPlanner activated %s; effective routes are %s (mode=%s, probabilities=%s)",
                activated, auditedRoutes, mode, probabilities);
    }

    private static String inputText(Object input) {
        if (input instanceof Map<?, ?> values && values.get("request") != null) {
            return String.valueOf(values.get("request"));
        }
        return String.valueOf(input);
    }
}
