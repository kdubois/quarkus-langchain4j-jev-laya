package com.tripplanner.poc.agentic;

import com.tripplanner.poc.guardrails.JevReplyGuardrail;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;

/**
 * Handles greetings and general questions that do not belong to a specific specialist. The
 * reply is validated by the Jev output guardrail.
 */
public interface GeneralAgent {

    @UserMessage("""
            You are the general assistant for a car rental company.
            Answer the customer's question helpfully and concisely. Keep it to the point and offer
            to help with reservations, weather or pricing when it is relevant.

            Customer request: {{request}}
            """)
    @Agent(name = "general", description = "Handles only greetings and questions unrelated to reservations, weather, or pricing",
           outputKey = "reply")
    @OutputGuardrails(value = JevReplyGuardrail.class, maxRetries = 2)
    String answer(String request);
}
