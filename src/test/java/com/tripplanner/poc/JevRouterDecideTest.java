package com.tripplanner.poc;

import com.tripplanner.poc.agentic.JevRouter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the pure routing decision (no Jev client or LLM involved).
 */
class JevRouterDecideTest {

    @Test
    void mapsEachChoiceToItsRoute() {
        assertEquals(JevRouter.ROUTE_RESERVATION, JevRouter.decide("reservation").route());
        assertEquals(JevRouter.ROUTE_WEATHER, JevRouter.decide("weather").route());
        assertEquals(JevRouter.ROUTE_COST, JevRouter.decide("cost").route());
    }

    @Test
    void unknownOrNullChoiceFallsBackToGeneral() {
        assertEquals(JevRouter.ROUTE_GENERAL, JevRouter.decide("something-else").route());
        assertEquals(JevRouter.ROUTE_GENERAL, JevRouter.decide(null).route());
        assertEquals(JevRouter.ROUTE_GENERAL, JevRouter.decide("").route());
    }

    @Test
    void keepsTheRawChoice() {
        assertEquals("reservation", JevRouter.decide("reservation").rawChoice());
    }
}
