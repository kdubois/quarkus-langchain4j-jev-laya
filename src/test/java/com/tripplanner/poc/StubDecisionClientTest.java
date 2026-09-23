package com.tripplanner.poc;

import com.tripplanner.poc.jev.JevQuestion;
import com.tripplanner.poc.jev.JevRequest;
import com.tripplanner.poc.jev.JevResponse;
import com.tripplanner.poc.jev.StubDecisionClient;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the deterministic offline Jev stub.
 */
class StubDecisionClientTest {

    private static final Map<String, String> ROUTE_CRITERIA = new LinkedHashMap<>(Map.of(
            "reservation", "Booking, modifying, cancelling, or questions about a rental reservation",
            "weather", "Weather, forecast, rain, snow, or climate",
            "cost", "Pricing, total cost, budget, fees, or cost comparison",
            "general", "Anything else, or a greeting or general question"));

    private final StubDecisionClient client = new StubDecisionClient("stub");

    @Test
    void reportsBackendAndModel() {
        assertEquals("stub", client.backend());
        assertEquals("stub", client.defaultModel());
    }

    @Test
    void routesWeatherQuestionToWeather() {
        String route = client.choose("What's the weather in Lisbon next week?", "route",
                JevQuestion.choice("Which specialist?", ROUTE_CRITERIA));
        assertEquals("weather", route);
    }

    @Test
    void routesBookingQuestionToReservation() {
        String route = client.choose("I need to book a car for next month", "route",
                JevQuestion.choice("Which specialist?", ROUTE_CRITERIA));
        assertEquals("reservation", route);
    }

    @Test
    void routesPricingQuestionToCost() {
        String route = client.choose("How much does it cost to rent an SUV?", "route",
                JevQuestion.choice("Which specialist?", ROUTE_CRITERIA));
        assertEquals("cost", route);
    }

    @Test
    void routesGreetingToGeneral() {
        String route = client.choose("Hello, who are you?", "route",
                JevQuestion.choice("Which specialist?", ROUTE_CRITERIA));
        assertEquals("general", route);
    }

    @Test
    void scoresComplexityByLength() {
        Double simple = client.score("Hi", "complexity",
                JevQuestion.score("How complex?", List.of("Simple", "Moderate", "Complex")));
        Double complex = client.score(
                "Compare the total cost, weather and best car for a 10 day trip around Portugal and Spain with a family and budget",
                "complexity", JevQuestion.score("How complex?", List.of("Simple", "Moderate", "Complex")));
        assertTrue(simple < complex, "longer/multi-part request should score more complex");
    }

    @Test
    void noulDetectsProblematicReplies() {
        Double good = client.noul("The rain starts Tuesday, expect wet roads.", "ok",
                JevQuestion.noul("Does the reply address the request?"));
        Double bad = client.noul("TODO: implement this later", "ok",
                JevQuestion.noul("Does the reply address the request?"));
        assertTrue(good > 0.5, "a normal reply should look like it addresses the request");
        assertTrue(bad < 0.5, "a placeholder reply should not");
    }

    @Test
    void evaluateReturnsAnAnswerPerQuestion() {
        JevResponse response = client.evaluate(new JevRequest(
                "weather forecast please",
                "jev-latest",
                Map.of("route", JevQuestion.choice("Which specialist?", ROUTE_CRITERIA))));
        assertEquals("weather", response.answers().get("route").choice());
    }
}
