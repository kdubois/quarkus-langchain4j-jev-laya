package com.tripplanner.poc.jev;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that a real Jev response body (captured from the live API) maps onto the records,
 * including the snake_case usage fields.
 */
class JevResponseDeserializationTest {

    private static final String LIVE_CHOICE_RESPONSE = """
            {"model":"jev-1.13.0","answers":{"route":{"type":"choice","choice":"weather","confidence":0.45,
            "probabilities":{"general":0.25,"reservation":0.14,"weather":0.59,"cost":0.02}}},
            "usage":{"input_tokens":400,"output_tokens":45}}
            """;

    private static final String LIVE_NOUL_RESPONSE = """
            {"model":"jev-1.13.0","answers":{"addresses_request":{"type":"noul","noul":0.01}},
            "usage":{"input_tokens":319,"output_tokens":21}}
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsChoiceAnswerAndUsage() throws Exception {
        JevResponse response = mapper.readValue(LIVE_CHOICE_RESPONSE, JevResponse.class);

        assertEquals("jev-1.13.0", response.model());
        JevAnswer route = response.answers().get("route");
        assertTrue(route.isChoice());
        assertEquals("weather", route.choice());
        assertEquals(0.45, route.confidence());
        assertEquals(0.59, route.probabilities().get("weather"));
        assertEquals(400, response.usage().inputTokens());
        assertEquals(45, response.usage().outputTokens());
    }

    @Test
    void mapsNoulAnswer() throws Exception {
        JevResponse response = mapper.readValue(LIVE_NOUL_RESPONSE, JevResponse.class);

        JevAnswer answer = response.answers().get("addresses_request");
        assertTrue(answer.isNoul());
        assertEquals(0.01, answer.noul());
        assertEquals(319, response.usage().inputTokens());
    }
}
