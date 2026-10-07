package com.tripplanner.poc.agentic;

import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.service.V;

/** Runs the core decision router first, then merges its parallel specialist replies. */
public interface TripAdvisorSystem extends MonitoredAgent {

    @SequenceAgent(
            name = "tripAdvisor",
            description = "Routes a customer request to specialists and combines their replies",
            outputKey = "reply",
            subAgents = { SpecialistRouter.class, GeneralFallback.class, MergeAgent.class })
    String planTrip(@V("request") String request);
}
