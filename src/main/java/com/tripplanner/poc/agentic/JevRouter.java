package com.tripplanner.poc.agentic;

import com.tripplanner.poc.guardrails.RouteAudit;
import com.tripplanner.poc.jev.DecisionClient;
import com.tripplanner.poc.jev.JevAnswer;
import com.tripplanner.poc.jev.JevQuestion;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates a customer request into a routing decision. One decision call asks two kinds of
 * question about the request:
 *
 * <ul>
 *   <li>a Choice ("which specialist?"), which is reliable when the request has one clear intent;</li>
 *   <li>one Noul per specialist ("does this request need weather / cost / reservation input?"),
 *       which can say yes to several specialists when the request mixes intents.</li>
 * </ul>
 *
 * The policy in {@link #decide(JevAnswer, Map, RoutingPolicy)} routes to every specialist whose Noul
 * is above the fan-out threshold, and uses the Choice only when none is. The Choice is not used as a
 * fast path: on a labelled eval set it was confident (0.90 to 0.99) about a single specialist for
 * requests that clearly needed two, and since both kinds of question come back in the same call,
 * skipping the Nouls saves nothing. The policy is a plain function over the answers so it can be
 * unit-tested; the decision call itself is isolated behind {@link DecisionClient}.
 */
public class JevRouter {

    public static final String ROUTE_RESERVATION = "reservation";
    public static final String ROUTE_WEATHER = "weather";
    public static final String ROUTE_COST = "cost";
    public static final String ROUTE_GENERAL = "general";

    /** The specialists that can be combined in a fan-out. */
    public static final List<String> SPECIALISTS = List.of(ROUTE_RESERVATION, ROUTE_WEATHER, ROUTE_COST);

    /** One specialist's Noul was above the threshold. */
    public static final String MODE_NEEDS = "needs";
    /** Several specialists' Nouls were above the threshold. */
    public static final String MODE_FAN_OUT = "fan-out";
    /** No Noul was above the threshold; the Choice decided. */
    public static final String MODE_CHOICE = "choice";
    /** No usable answer at all; the general agent. */
    public static final String MODE_FALLBACK = "fallback";

    static final String ROUTE_QUESTION = "route";
    static final String NEEDS_PREFIX = "needs_";

    private static final Map<String, String> ROUTE_CRITERIA = new LinkedHashMap<>();
    private static final Map<String, String> NEEDS_TOPICS = new LinkedHashMap<>();

    static {
        ROUTE_CRITERIA.put(ROUTE_RESERVATION, "Booking, modifying, cancelling, or questions about a rental reservation or pick-up");
        ROUTE_CRITERIA.put(ROUTE_WEATHER, "Weather, forecast, rain, snow, or climate for the trip or destination");
        ROUTE_CRITERIA.put(ROUTE_COST, "Pricing, total cost, budget, fees, or cost comparison");
        ROUTE_CRITERIA.put(ROUTE_GENERAL, "Anything else, or a greeting or general question about the service");

        NEEDS_TOPICS.put(ROUTE_RESERVATION, "booking, modifying, cancelling, upgrading, or other questions about a rental reservation or pick-up");
        NEEDS_TOPICS.put(ROUTE_WEATHER, "weather, forecast, rain, snow, or climate for the trip or destination");
        NEEDS_TOPICS.put(ROUTE_COST, "pricing, total cost, budget, fees, value for money, or cost comparison");
    }

    private static final Logger LOG = Logger.getLogger(JevRouter.class);

    private final DecisionClient decision;
    private final RouteAudit routeAudit;
    private final RoutingPolicy policy;

    public JevRouter(DecisionClient decision, RouteAudit routeAudit, RoutingPolicy policy) {
        this.decision = decision;
        this.routeAudit = routeAudit;
        this.policy = policy;
    }

    public JevRouter(DecisionClient decision, RoutingPolicy policy) {
        this(decision, null, policy);
    }

    /**
     * Threshold for the routing policy.
     *
     * @param fanOutThreshold Noul probability above which a specialist is called
     */
    public record RoutingPolicy(double fanOutThreshold) {

        public static final RoutingPolicy DEFAULT = new RoutingPolicy(0.5);

        /** Reads {@code routing.fan-out-threshold}. */
        public static RoutingPolicy fromConfig() {
            return new RoutingPolicy(ConfigProvider.getConfig()
                    .getOptionalValue("routing.fan-out-threshold", Double.class)
                    .orElse(DEFAULT.fanOutThreshold()));
        }
    }

    /**
     * The routing outcome plus the raw signals it was based on, so callers can report or audit them.
     *
     * @param routes        the specialists to call, in order (one unless {@code mode} is fan-out)
     * @param rawChoice     the model's Choice answer, before normalization
     * @param confidence    the Choice confidence (null if the backend returned none)
     * @param probabilities the Choice probability per option
     * @param needs         the Noul probability per specialist
     * @param mode          which part of the policy decided: needs, fan-out, choice, or fallback
     */
    public record RouteDecision(List<String> routes, String rawChoice, Double confidence,
                                Map<String, Double> probabilities, Map<String, Double> needs, String mode) {

        /** The primary route (the first specialist called). */
        public String route() {
            return routes.get(0);
        }

        public boolean isFanOut() {
            return routes.size() > 1;
        }
    }

    /** The questions sent for every request: the routing Choice plus one Noul per specialist. */
    public static Map<String, JevQuestion> questions() {
        Map<String, JevQuestion> questions = new LinkedHashMap<>();
        questions.put(ROUTE_QUESTION, JevQuestion.choice("Which specialist should handle this customer request?", ROUTE_CRITERIA));
        NEEDS_TOPICS.forEach((route, topic) -> questions.put(NEEDS_PREFIX + route, JevQuestion.noul(
                "Does answering this customer request fully require input about " + topic + "?")));
        return questions;
    }

    /**
     * Asks the decision model about the request and applies the routing policy.
     */
    public RouteDecision route(String request) {
        Map<String, JevAnswer> answers = decision.ask(request, questions());
        RouteDecision result = decide(answers.get(ROUTE_QUESTION), needs(answers), policy);
        if (routeAudit != null) {
            routeAudit.log(request, result, decision.lastEffectiveBackend());
        }
        return result;
    }

    /** Extracts the per-specialist Noul probabilities from a set of answers. */
    public static Map<String, Double> needs(Map<String, JevAnswer> answers) {
        Map<String, Double> needs = new LinkedHashMap<>();
        for (String route : SPECIALISTS) {
            JevAnswer answer = answers.get(NEEDS_PREFIX + route);
            if (answer != null && answer.noul() != null) {
                needs.put(route, answer.noul());
            }
        }
        return needs;
    }

    /**
     * The routing policy, as a pure function over the decision answers:
     *
     * <ol>
     *   <li>Every specialist whose Noul is above the fan-out threshold is called, most likely
     *       first. One specialist is a plain route; several is a fan-out.</li>
     *   <li>No specialist above the threshold: the Choice decides (often the general agent).</li>
     *   <li>No Choice either: the general agent.</li>
     * </ol>
     */
    public static RouteDecision decide(JevAnswer choice, Map<String, Double> needs, RoutingPolicy policy) {
        String rawChoice = choice == null ? null : choice.choice();
        Double confidence = choice == null ? null : choice.confidence();
        Map<String, Double> probabilities = choice == null || choice.probabilities() == null
                ? Map.of() : choice.probabilities();

        List<String> routes = needs.entrySet().stream()
                .filter(e -> e.getValue() > policy.fanOutThreshold())
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .map(Map.Entry::getKey)
                .toList();
        String mode = switch (routes.size()) {
            case 0 -> rawChoice == null ? MODE_FALLBACK : MODE_CHOICE;
            case 1 -> MODE_NEEDS;
            default -> MODE_FAN_OUT;
        };
        if (routes.isEmpty()) {
            routes = List.of(normalize(rawChoice));
        }
        RouteDecision result = new RouteDecision(routes, rawChoice, confidence, probabilities, needs, mode);
        LOG.infof("Jev routed to %s (mode=%s, choice=%s, confidence=%s, needs=%s)",
                result.routes(), result.mode(), rawChoice, confidence, needs);
        return result;
    }

    /** Maps a Choice option (or null) to a known route; anything unrecognized is general. */
    public static String normalize(String chosen) {
        return switch (chosen) {
            case ROUTE_WEATHER -> ROUTE_WEATHER;
            case ROUTE_COST -> ROUTE_COST;
            case ROUTE_RESERVATION -> ROUTE_RESERVATION;
            case null, default -> ROUTE_GENERAL;
        };
    }
}
