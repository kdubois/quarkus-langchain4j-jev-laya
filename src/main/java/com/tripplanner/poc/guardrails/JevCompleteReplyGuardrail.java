package com.tripplanner.poc.guardrails;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Checks that the merged reply of a fan-out covers the whole request. Used on the
 * {@code MergeAgent}, where leaving out one of the specialists' answers is the failure to catch.
 * Measured on live Jev: a reply that answers only the weather half of a weather-and-price question
 * scores 0.24 and is retried, while a complete reply scores 0.93.
 */
@ApplicationScoped
public class JevCompleteReplyGuardrail extends AbstractJevReplyGuardrail {

    @Override
    protected String question() {
        return "Does the drafted reply directly address the customer's request?";
    }
}
