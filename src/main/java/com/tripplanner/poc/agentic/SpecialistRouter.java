package com.tripplanner.poc.agentic;

import com.tripplanner.poc.jev.ActiveDecisionClient;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.decisionrouter.DecisionRouterPlanner;
import dev.langchain4j.agentic.planner.Planner;
import io.quarkus.arc.Arc;
import dev.langchain4j.service.V;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Map;

/** Routes a request through LangChain4j's decision-model router and returns all specialist replies. */
public interface SpecialistRouter {

    @PlannerAgent(
            name = "specialistRouter",
            description = "Selects the specialists needed to answer a car-rental customer request",
            outputKey = "specialistReplies",
            subAgents = { ReservationAgent.class, WeatherAgent.class, CostAgent.class, GeneralAgent.class })
    Map<String, String> route(@V("request") String request);

    @PlannerSupplier
    static Planner planner() {
        ActiveDecisionClient decisionModel = Arc.container().select(ActiveDecisionClient.class).get();
        double threshold = ConfigProvider.getConfig()
                .getOptionalValue("routing.fan-out-threshold", Double.class)
                .orElse(0.5);
        return new DecisionRouterPlanner(decisionModel, threshold);
    }
}
