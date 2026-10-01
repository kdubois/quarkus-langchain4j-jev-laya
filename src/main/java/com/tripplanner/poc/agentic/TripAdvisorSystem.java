package com.tripplanner.poc.agentic;

import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.observability.MonitoredAgent;
import dev.langchain4j.agentic.planner.Planner;

/**
 * Top-level entry point. It is a {@link PlannerAgent} whose planner is supplied by
 * {@link JevRoutingPlanner} (built with the {@link JevRouter}). The customer request is routed by
 * Jev to one specialist, or to several whose replies the {@link MergeAgent} combines.
 *
 * The router is resolved from the CDI container at planner-creation time, because the framework
 * invokes a {@code @PlannerSupplier} method without arguments.
 */
public interface TripAdvisorSystem extends MonitoredAgent {

    @PlannerAgent(
            name = "tripAdvisor",
            description = "Routes a customer request to the right specialist using a Jev decision",
            outputKey = "reply",
            subAgents = { ReservationAgent.class, WeatherAgent.class, CostAgent.class, GeneralAgent.class,
                    MergeAgent.class, SpecialistRepliesCollector.class })
    String planTrip(String request);

    @PlannerSupplier
    static Planner planner() {
        return new JevRoutingPlanner();
    }
}
