package com.tripplanner.poc.agentic;

import com.tripplanner.poc.guardrails.JevCompleteReplyGuardrail;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;

/**
 * Combines the replies of several specialists into one answer when the router fans a request out.
 * The combined reply is validated by {@link JevCompleteReplyGuardrail}, which checks that it covers
 * the whole request.
 */
public interface MergeAgent {

    @UserMessage("""
            You are the customer-facing assistant for a car rental company.
            Several specialists each answered part of the customer's request. Combine their answers
            into one concise reply that covers every part of the request. Use only facts from the
            specialist answers, and drop repetition between them.

            Customer request: {request}

            Specialist answers:
            {specialistReplies}
            """)
    @Agent(description = "Combines several specialist answers into one reply",
           outputKey = "reply")
    @OutputGuardrails(value = JevCompleteReplyGuardrail.class, maxRetries = 2)
    String merge(String request, String specialistReplies);
}
