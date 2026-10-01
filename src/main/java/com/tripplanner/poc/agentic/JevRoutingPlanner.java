package com.tripplanner.poc.agentic;

import com.tripplanner.poc.guardrails.RouteAudit;
import com.tripplanner.poc.jev.ActiveDecisionClient;
import com.tripplanner.poc.jev.DecisionClient;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgentInvocation;
import dev.langchain4j.agentic.scope.AgenticScope;
import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The agentic heart of the PoC: a {@link Planner} whose routing decision comes from Jev (a System
 * One model) instead of an LLM. In {@link #firstAction} it reads the customer request and asks the
 * {@link JevRouter} which specialists to use. One specialist is called directly. When the request
 * mixes intents, the specialists are called one after the other, the non-AI
 * {@link SpecialistRepliesCollector} appends each reply to the agentic scope, and the
 * {@link MergeAgent} combines them into one answer.
 *
 * Specialists run sequentially rather than through a parallel {@code call(...)}: in this version of
 * the agentic module, a planner that needs a follow-up step after parallel agents cannot express it
 * safely (the next actions of the parallel branches are combined without synchronization).
 *
 * All per-request state lives in the {@link AgenticScope}, not on the planner, so concurrent
 * requests do not interfere.
 *
 * This mirrors the framework's built-in {@code SupervisorPlanner}, except the routing intelligence
 * is a cheap, calibrated decision model rather than a chat model.
 *
 * The decision client and route audit are resolved from the CDI container lazily (at request time),
 * because the framework instantiates a {@code @PlannerSupplier} before the container is usable.
 */
public class JevRoutingPlanner implements Planner {

    static final String PENDING_ROUTES = "pendingRoutes";

    /** The agent that answers each route; anything else goes to the general agent. */
    private static final Map<String, Class<?>> ROUTE_AGENTS = Map.of(
            JevRouter.ROUTE_RESERVATION, ReservationAgent.class,
            JevRouter.ROUTE_WEATHER, WeatherAgent.class,
            JevRouter.ROUTE_COST, CostAgent.class,
            JevRouter.ROUTE_GENERAL, GeneralAgent.class);

    /** The sub-agents, by agent type. */
    private final Map<Class<?>, AgentInstance> agents = new HashMap<>();

    public JevRoutingPlanner() {
    }

    @Override
    public void init(InitPlanningContext context) {
        for (AgentInstance subagent : context.subagents()) {
            agents.put(isCollector(subagent.type()) ? SpecialistRepliesCollector.class : subagent.type(), subagent);
        }
    }

    @Override
    public Action firstAction(PlanningContext context) {
        AgenticScope scope = context.agenticScope();
        String request = readRequest(scope);
        if (request == null || request.isBlank()) {
            request = "general question";
        }

        JevRouter router = new JevRouter(decisionClient(), routeAudit(), JevRouter.RoutingPolicy.fromConfig());
        JevRouter.RouteDecision decision = router.route(request);
        scope.writeState("route", decision.route());
        scope.writeState("routes", decision.routes());
        scope.writeState("rawChoice", decision.rawChoice());
        scope.writeState(PENDING_ROUTES, new ArrayList<>(decision.routes().subList(1, decision.routes().size())));
        scope.writeState(SpecialistRepliesCollector.CURRENT_ROUTE, decision.route());
        Log.infof("Jev routing decision: routes=%s, mode=%s", decision.routes(), decision.mode());

        return call(pickSubagent(decision.route()));
    }

    /**
     * Maps a route to the matching sub-agent. Exposed separately so the routing choice can be
     * unit-tested without invoking the (final, non-mockable) agent executor.
     */
    public AgentInstance pickSubagent(String route) {
        return agents.get(ROUTE_AGENTS.getOrDefault(route, GeneralAgent.class));
    }

    @Override
    public Action nextAction(PlanningContext context) {
        AgentInvocation previous = context.previousAgentInvocation();
        AgenticScope scope = context.agenticScope();
        List<String> routes = scope.readState("routes", List.of());
        if (routes.size() <= 1 || MergeAgent.class.equals(previous.agentType())) {
            return done(previous.output());
        }

        // Fan-out: after each specialist, collect its reply; then call the next specialist, or merge.
        if (!isCollector(previous.agentType())) {
            return call(agents.get(SpecialistRepliesCollector.class));
        }
        List<String> pending = new ArrayList<>(scope.readState(PENDING_ROUTES, List.<String>of()));
        if (pending.isEmpty()) {
            return call(agents.get(MergeAgent.class));
        }
        String next = pending.remove(0);
        scope.writeState(PENDING_ROUTES, pending);
        scope.writeState(SpecialistRepliesCollector.CURRENT_ROUTE, next);
        return call(pickSubagent(next));
    }

    /**
     * The collector is a static scope action, which the agentic module wraps in
     * {@link AgenticServices.AgenticScopeAction}; it is the only such sub-agent here.
     */
    private static boolean isCollector(Class<?> type) {
        return SpecialistRepliesCollector.class.equals(type) || AgenticServices.AgenticScopeAction.class.equals(type);
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
