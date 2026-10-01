package com.tripplanner.poc.guardrails;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Checks that a specialist's (or the general agent's) reply is relevant: that it answers at least
 * one thing the customer asked or said. It does not require the whole request to be covered,
 * because in a fan-out each specialist answers only its own part, and a greeting has no request to
 * address. Measured on live Jev: a partial fan-out answer scores 0.98 and a friendly reply to a
 * greeting 0.55, while an off-topic reply scores 0.02 and an evasive one 0.08.
 */
@ApplicationScoped
public class JevReplyGuardrail extends AbstractJevReplyGuardrail {

    @Override
    protected String question() {
        return "Does the drafted reply respond to the customer's message, answering at least one thing the customer asked or said?";
    }
}
