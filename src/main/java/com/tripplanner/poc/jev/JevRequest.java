package com.tripplanner.poc.jev;

import java.util.Map;

/**
 * A single POST to the Jev System One endpoint: one state plus a map of typed questions.
 * Answers come back keyed by the same question ids.
 */
public record JevRequest(Object state, String model, Map<String, JevQuestion> questions) {
}
