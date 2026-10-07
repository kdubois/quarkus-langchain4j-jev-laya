package com.tripplanner.poc.agentic;

import com.tripplanner.poc.guardrails.RouteAudit;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RoutingAuditListenerTest {

    @Test
    void recordsTheRoutesSelectedByTheCorePlannerThreshold() {
        RouteAudit audit = new RouteAudit();
        RoutingAuditListener listener = new RoutingAuditListener(audit, 0.5, () -> "kev");

        listener.record(request(), response(0.81, 0.12, 0.50, 0.09));

        assertEquals(List.of("reservation", "cost"), audit.latest().routes());
        assertEquals("fan-out", audit.latest().mode());
        assertEquals("Book a car and tell me the price", audit.latest().request());
        assertEquals("kev", audit.latest().effectiveBackend());
        assertEquals(audit.latest(), audit.current());

        audit.clearRequest();
        assertNull(audit.current());
    }

    @Test
    void recordsTheGeneralFallbackWhenNoRouteReachesTheThreshold() {
        RouteAudit audit = new RouteAudit();
        RoutingAuditListener listener = new RoutingAuditListener(audit, 0.5, () -> "jev");

        listener.record(request(), response(0.2, 0.1, 0.3, 0.4));

        assertEquals(List.of("general"), audit.latest().routes());
        assertEquals("fallback", audit.latest().mode());
    }

    private static DecisionRequest request() {
        Map<String, dev.langchain4j.model.decision.request.Question> questions = new LinkedHashMap<>();
        RoutingAuditListener.ROUTES.forEach(route ->
                questions.put(route, YesNoQuestion.of("Should " + route + " handle this request?")));
        return DecisionRequest.builder()
                .input(Map.of("request", "Book a car and tell me the price"))
                .questions(questions)
                .build();
    }

    private static DecisionResponse response(double reservation, double weather, double cost, double general) {
        Map<String, DecisionAnswer> answers = new LinkedHashMap<>();
        answers.put("reservation", YesNoAnswer.of(reservation));
        answers.put("weather", YesNoAnswer.of(weather));
        answers.put("cost", YesNoAnswer.of(cost));
        answers.put("general", YesNoAnswer.of(general));
        return DecisionResponse.builder().answers(answers).modelName("test").build();
    }
}
