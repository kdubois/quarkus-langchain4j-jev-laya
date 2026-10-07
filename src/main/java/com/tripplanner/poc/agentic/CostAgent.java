package com.tripplanner.poc.agentic;

import com.tripplanner.poc.guardrails.JevReplyGuardrail;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;

/**
 * Handles pricing, budget and cost-comparison questions. The reply is validated by the Jev
 * output guardrail.
 */
public interface CostAgent {

    @UserMessage("""
            You are the pricing specialist for a car rental company.
            Answer the following customer cost or pricing question helpfully and concisely.
            Distinguish base rates, optional extras and fees. If the trip details are incomplete,
            state your assumptions and keep figures clearly indicative.

            Customer request: {{request}}
            """)
    @Agent(name = "cost", description = "Answers pricing, total cost, budget, or fee questions",
           outputKey = "reply")
    @OutputGuardrails(value = JevReplyGuardrail.class, maxRetries = 2)
    String answer(String request);
}
