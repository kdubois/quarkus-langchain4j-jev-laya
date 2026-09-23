package com.tripplanner.poc.agentic;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;

/**
 * Handles pricing, budget and cost-comparison questions.
 */
public interface CostAgent {

    @UserMessage("""
            You are the pricing specialist for a car rental company.
            Answer the following customer cost or pricing question helpfully and concisely.
            Distinguish base rates, optional extras and fees. If the trip details are incomplete,
            state your assumptions and keep figures clearly indicative.

            Customer request: {request}
            """)
    @Agent(description = "Answers pricing, total cost, budget, or fee questions",
           outputKey = "reply")
    String answer(String request);
}
