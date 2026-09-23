package com.tripplanner.poc.agentic;

import com.tripplanner.poc.guardrails.JevReplyGuardrail;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;

/**
 * Handles weather and forecast questions. The reply is validated by the Jev output guardrail,
 * which uses a System One decision to confirm the answer actually addresses the request.
 */
public interface WeatherAgent {

    @UserMessage("""
            You are the weather specialist for a car rental company.
            Answer the following customer weather question helpfully and concisely, mentioning
            practical driving or trip implications where relevant. Answer directly, do not ask the
            customer to restate the question.

            Customer request: {request}
            """)
    @Agent(description = "Answers weather and forecast questions for the trip or destination",
           outputKey = "reply")
    @OutputGuardrails(value = JevReplyGuardrail.class, maxRetries = 2)
    String answer(String request);
}
