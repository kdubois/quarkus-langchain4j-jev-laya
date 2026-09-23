package com.tripplanner.poc.agentic;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;

/**
 * Handles greetings and general questions that do not belong to a specific specialist.
 */
public interface GeneralAgent {

    @UserMessage("""
            You are the general assistant for a car rental company.
            Answer the customer's question helpfully and concisely. Keep it to the point and offer
            to help with reservations, weather or pricing when it is relevant.
            """)
    @Agent(description = "Handles greetings and general questions about the service",
           outputKey = "reply")
    String answer(String request);
}
