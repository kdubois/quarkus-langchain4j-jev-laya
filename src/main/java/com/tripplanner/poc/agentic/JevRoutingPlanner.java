package com.tripplanner.poc.agentic;

import com.tripplanner.poc.guardrails.RouteAudit;
import com.tripplanner.poc.jev.ActiveDecisionClient;
import com.tripplanner.poc.jev.DecisionClient;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkus.arc.Arc;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * The agentic heart of the PoC: a {@link Planner} whose routing decision comes from Jev (a System
 * One model) instead of an LLM. In {@link #firstAction} it reads the customer request, asks Jev
 * (a Choice question) which specialist to use, and dispatches exactly that sub-agent.
 *
 * This mirrors the framework's built-in {@code SupervisorPlanner}, except the routing intelligence
 * is a cheap, calibrated decision model rather than a chat model.
 *
 * The decision client and route audit are resolved from the CDI container lazily (at request time),
 * because the framework instantiates a {@code @PlannerSupplier} before the container is usable.
 */
public class JevRoutingPlanner implements Planner {

    private static final Logger LOG = Logger.getLogger(JevRoutingPlanner.class);

    private AgentInstance reservationAgent;
    private AgentInstance weatherAgent;
    private AgentInstance costAgent;
    private AgentInstance generalAgent;

    public JevRoutingPlanner() {
    }

    @Override
    public void init(InitPlanningContext context) {
        for (AgentInstance subagent : context.subagents()) {
            Class<?> type = subagent.type();
            if (ReservationAgent.class.equals(type)) {
                reservationAgent = subagent;
            } else if (WeatherAgent.class.equals(type)) {
                weatherAgent = subagent;
            } else if (CostAgent.class.equals(type)) {
                costAgent = subagent;
            } else if (GeneralAgent.class.equals(type)) {
                generalAgent = subagent;
            }
        }
    }

    @Override
    public Action firstAction(PlanningContext context) {
        AgenticScope scope = context.agenticScope();
        String request = readRequest(scope);
        if (request == null || request.isBlank()) {
            request = "general question";
        }

        JevRouter router = new JevRouter(decisionClient(), routeAudit());
        JevRouter.RouteDecision decision = router.route(request);
        scope.writeState("route", decision.route());
        scope.writeState("rawChoice", decision.rawChoice());
        LOG.infof("Jev routing decision: route=%s, rawChoice=%s", decision.route(), decision.rawChoice());

        return call(pickSubagent(decision));
    }

    /**
     * Maps a Jev routing decision to the matching sub-agent. Exposed separately so the routing
     * choice can be unit-tested without invoking the (final, non-mockable) agent executor.
     */
    public AgentInstance pickSubagent(JevRouter.RouteDecision decision) {
        return switch (decision.route()) {
            case JevRouter.ROUTE_RESERVATION -> reservationAgent;
            case JevRouter.ROUTE_WEATHER -> weatherAgent;
            case JevRouter.ROUTE_COST -> costAgent;
            default -> generalAgent;
        };
    }

    @Override
    public Action nextAction(PlanningContext context) {
        Object output = context.previousAgentInvocation().output();
        return done(output);
    }

    @Override
    public AgenticSystemTopology topology() {
        return AgenticSystemTopology.ROUTER;
    }

    private String readRequest(AgenticScope scope) {
        Object request = scope.readState("request");
        if (request instanceof String s && !s.isBlank()) {
            return s;
        }
        // Fallback: use the only string state value if present.
        Map<String, Object> state = scope.state();
        for (Object value : state.values()) {
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return request == null ? null : String.valueOf(request);
    }

    private DecisionClient decisionClient() {
        return Arc.container().select(ActiveDecisionClient.class).get();
    }

    private RouteAudit routeAudit() {
        return Arc.container().select(RouteAudit.class).get();
    }
}
