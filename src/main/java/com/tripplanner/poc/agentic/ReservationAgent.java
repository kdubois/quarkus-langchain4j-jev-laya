package com.tripplanner.poc.agentic;

import com.tripplanner.poc.guardrails.JevReplyGuardrail;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;

/**
 * Handles reservation, booking, pick-up and cancellation questions.
 */
public interface ReservationAgent {

    @UserMessage("""
            You are the reservations specialist for a car rental company.
            Answer the following customer reservation question helpfully and concisely. Cover
            bookings, modifications, cancellations and pick-up details. If a detail is not given,
            ask a short clarifying question rather than inventing specifics.

            Customer request: {request}
            """)
    @Agent(description = "Handles booking, modifying, cancelling, or questions about a reservation",
           outputKey = "reply")
    @OutputGuardrails(value = JevReplyGuardrail.class, maxRetries = 2)
    String answer(String request);
}
