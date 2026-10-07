package com.tripplanner.poc.agentic;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.service.V;

import java.util.Map;

/** Invokes the general agent when the decision router did not activate any specialist. */
public interface GeneralFallback {

    @ConditionalAgent(
            name = "generalFallback",
            description = "Uses the general assistant when no specialist reaches the routing threshold",
            outputKey = "routedReplies",
            subAgents = GeneralAgent.class)
    Map<String, String> apply(
            @V("request") String request,
            @V("specialistReplies") Map<String, String> specialistReplies);

    @ActivationCondition(GeneralAgent.class)
    static boolean needsGeneral(@V("specialistReplies") Map<String, String> specialistReplies) {
        return specialistReplies == null || specialistReplies.isEmpty();
    }

    @Output
    static Map<String, String> replies(
            @V("specialistReplies") Map<String, String> specialistReplies,
            @V("reply") String generalReply) {
        if (specialistReplies != null && !specialistReplies.isEmpty()) {
            return specialistReplies;
        }
        return generalReply == null ? Map.of() : Map.of("general", generalReply);
    }
}
