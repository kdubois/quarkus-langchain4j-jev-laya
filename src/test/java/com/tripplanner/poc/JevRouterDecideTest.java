package com.tripplanner.poc;

import com.tripplanner.poc.agentic.JevRouter;
import com.tripplanner.poc.agentic.JevRouter.RouteDecision;
import com.tripplanner.poc.agentic.JevRouter.RoutingPolicy;
import com.tripplanner.poc.jev.JevAnswer;
import com.tripplanner.poc.jev.StubDecisionClient;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure routing policy (no decision backend or LLM involved). The answer values
 * in the multi-intent cases are the ones live Jev returned for those requests.
 */
class JevRouterDecideTest {

    private static final RoutingPolicy POLICY = new RoutingPolicy(0.5);

    private static JevAnswer choice(String option, double confidence) {
        return new JevAnswer("choice", option, null, null, null, Map.of(option, confidence), confidence);
    }

    private static Map<String, Double> needs(double reservation, double weather, double cost) {
        Map<String, Double> needs = new LinkedHashMap<>();
        needs.put(JevRouter.ROUTE_RESERVATION, reservation);
        needs.put(JevRouter.ROUTE_WEATHER, weather);
        needs.put(JevRouter.ROUTE_COST, cost);
        return needs;
    }

    @Test
    void singleNeededSpecialistIsAPlainRoute() {
        // "Will it rain in Lisbon next Tuesday?"
        RouteDecision decision = JevRouter.decide(choice("weather", 1.0), needs(0.02, 0.97, 0.02), POLICY);

        assertEquals(List.of(JevRouter.ROUTE_WEATHER), decision.routes());
        assertEquals(JevRouter.MODE_NEEDS, decision.mode());
        assertFalse(decision.isFanOut());
    }

    @Test
    void confidentChoiceDoesNotHideASecondIntent() {
        // "Book me a car for Saturday and tell me what it'll cost with full insurance."
        // The Choice is sure it is a reservation, but the Nouls show the cost question too.
        RouteDecision decision = JevRouter.decide(choice("reservation", 0.97), needs(0.84, 0.05, 0.95), POLICY);

        assertEquals(List.of(JevRouter.ROUTE_COST, JevRouter.ROUTE_RESERVATION), decision.routes());
        assertEquals(JevRouter.MODE_FAN_OUT, decision.mode());
    }

    @Test
    void fansOutToEverySpecialistThatIsNeededMostLikelyFirst() {
        // "How much extra is a convertible, and will the weather in Nice be good enough next weekend?"
        RouteDecision decision = JevRouter.decide(choice("general", 0.26), needs(0.69, 0.91, 0.85), POLICY);

        assertEquals(List.of(JevRouter.ROUTE_WEATHER, JevRouter.ROUTE_COST, JevRouter.ROUTE_RESERVATION),
                decision.routes());
        assertEquals(JevRouter.MODE_FAN_OUT, decision.mode());
        assertTrue(decision.isFanOut());
        assertEquals("general", decision.rawChoice());
    }

    @Test
    void noNeededSpecialistLetsTheChoiceDecide() {
        // "Can I bring my dog in the rental car?"
        RouteDecision decision = JevRouter.decide(choice("reservation", 0.60), needs(0.46, 0.04, 0.19), POLICY);

        assertEquals(List.of(JevRouter.ROUTE_RESERVATION), decision.routes());
        assertEquals(JevRouter.MODE_CHOICE, decision.mode());
    }

    @Test
    void greetingGoesToGeneralThroughTheChoice() {
        // "Hi there!"
        RouteDecision decision = JevRouter.decide(choice("general", 1.0), needs(0.16, 0.08, 0.07), POLICY);

        assertEquals(List.of(JevRouter.ROUTE_GENERAL), decision.routes());
        assertEquals(JevRouter.MODE_CHOICE, decision.mode());
    }

    @Test
    void thresholdIsExclusive() {
        RouteDecision decision = JevRouter.decide(choice("general", 0.3), needs(0.5, 0.51, 0.1), POLICY);

        assertEquals(List.of(JevRouter.ROUTE_WEATHER), decision.routes());
    }

    @Test
    void missingChoiceUsesTheNouls() {
        RouteDecision decision = JevRouter.decide(null, needs(0.9, 0.1, 0.1), POLICY);

        assertEquals(List.of(JevRouter.ROUTE_RESERVATION), decision.routes());
        assertNull(decision.rawChoice());
        assertNull(decision.confidence());
    }

    @Test
    void missingEverythingFallsBackToGeneral() {
        RouteDecision decision = JevRouter.decide(null, Map.of(), POLICY);

        assertEquals(List.of(JevRouter.ROUTE_GENERAL), decision.routes());
        assertEquals(JevRouter.MODE_FALLBACK, decision.mode());
    }

    @Test
    void unknownChoiceIsGeneral() {
        RouteDecision decision = JevRouter.decide(choice("something-else", 0.95), Map.of(), POLICY);

        assertEquals(List.of(JevRouter.ROUTE_GENERAL), decision.routes());
    }

    @Test
    void normalizesChoices() {
        assertEquals(JevRouter.ROUTE_RESERVATION, JevRouter.normalize("reservation"));
        assertEquals(JevRouter.ROUTE_WEATHER, JevRouter.normalize("weather"));
        assertEquals(JevRouter.ROUTE_COST, JevRouter.normalize("cost"));
        assertEquals(JevRouter.ROUTE_GENERAL, JevRouter.normalize("something-else"));
        assertEquals(JevRouter.ROUTE_GENERAL, JevRouter.normalize(null));
        assertEquals(JevRouter.ROUTE_GENERAL, JevRouter.normalize(""));
    }

    @Test
    void routeAsksOneChoiceAndOneNoulPerSpecialistInOneCall() {
        int[] calls = {0};
        StubDecisionClient stub = new StubDecisionClient() {
            @Override
            public com.tripplanner.poc.jev.JevResponse evaluate(com.tripplanner.poc.jev.JevRequest request) {
                calls[0]++;
                assertEquals(1 + JevRouter.SPECIALISTS.size(), request.questions().size());
                return super.evaluate(request);
            }
        };

        RouteDecision decision = new JevRouter(stub, POLICY).route("Please reserve a car for next week");

        assertEquals(1, calls[0]);
        assertEquals(List.of(JevRouter.ROUTE_RESERVATION), decision.routes());
        assertEquals(JevRouter.SPECIALISTS.size(), decision.needs().size());
    }
}
