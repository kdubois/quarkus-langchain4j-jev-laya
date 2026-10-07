package com.tripplanner.poc.agentic;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneralFallbackTest {

    @Test
    void invokesGeneralOnlyWhenTheRouterReturnedNoReplies() {
        assertTrue(GeneralFallback.needsGeneral(Map.of()));
        assertTrue(GeneralFallback.needsGeneral(null));
        assertFalse(GeneralFallback.needsGeneral(Map.of("weather", "Sunny")));
    }

    @Test
    void wrapsTheGeneralReplyInTheSameMapShapeAsTheRouter() {
        assertEquals(Map.of("general", "How can I help?"),
                GeneralFallback.replies(Map.of(), "How can I help?"));
    }

    @Test
    void keepsTheRouterRepliesWhenSpecialistsWereActivated() {
        Map<String, String> replies = Map.of("reservation", "Please provide a pickup time");

        assertEquals(replies, GeneralFallback.replies(replies, "unused"));
    }
}
