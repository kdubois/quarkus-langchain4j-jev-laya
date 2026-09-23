package com.tripplanner.poc.agentic;

import com.tripplanner.poc.guardrails.RouteAudit;
import com.tripplanner.poc.jev.DecisionClient;
import com.tripplanner.poc.jev.JevQuestion;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates a customer request into a Jev routing decision. The routing logic is a plain,
 * dependency-free function over the Jev answer so it can be unit-tested; the Jev call itself is
 * isolated behind {@link DecisionClient} (hosted Jev, self-hosted Laya, or a deterministic stub).
 */
@ApplicationScoped
public class JevRouter {

    public static final String ROUTE_RESERVATION = "reservation";
    public static final String ROUTE_WEATHER = "weather";
    public static final String ROUTE_COST = "cost";
    public static final String ROUTE_GENERAL = "general";

    private static final Logger LOG = Logger.getLogger(JevRouter.class);

    private final DecisionClient decision;
    private final RouteAudit routeAudit;

    public JevRouter(DecisionClient decision, RouteAudit routeAudit) {
        this.decision = decision;
        this.routeAudit = routeAudit;
    }

    public JevRouter(DecisionClient decision) {
        this(decision, null);
    }

    /**
     * Asks Jev which specialist should handle the request. Returns the route id.
     */
    public RouteDecision route(String request) {
        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put(ROUTE_RESERVATION, "Booking, modifying, cancelling, or questions about a rental reservation or pick-up");
        criteria.put(ROUTE_WEATHER, "Weather, forecast, rain, snow, or climate for the trip or destination");
        criteria.put(ROUTE_COST, "Pricing, total cost, budget, fees, or cost comparison");
        criteria.put(ROUTE_GENERAL, "Anything else, or a greeting or general question about the service");

        String chosen = decision.choose(request, "route",
                JevQuestion.choice("Which specialist should handle this customer request?", criteria));
        RouteDecision decision = decide(chosen);
        routeAudit.log(request, decision.route(), decision.rawChoice());
        return decision;
    }

    /**
     * Pure routing decision. Maps a Jev choice (or null) to a normalized route, logging the result.
     */
    public static RouteDecision decide(String chosen) {
        String route = switch (chosen) {
            case ROUTE_WEATHER -> ROUTE_WEATHER;
            case ROUTE_COST -> ROUTE_COST;
            case ROUTE_RESERVATION -> ROUTE_RESERVATION;
            case null, default -> ROUTE_GENERAL;
        };
        LOG.infof("Jev routed to '%s' (choice=%s)", route, chosen);
        return new RouteDecision(route, chosen);
    }

    public record RouteDecision(String route, String rawChoice) {}
}
