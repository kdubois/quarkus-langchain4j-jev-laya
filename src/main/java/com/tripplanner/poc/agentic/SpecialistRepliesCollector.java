package com.tripplanner.poc.agentic;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.scope.AgenticScope;

/**
 * A non-AI agent that appends the latest specialist reply to {@code specialistReplies} during a
 * fan-out, labelled with the route that produced it. The {@link JevRoutingPlanner} calls it after
 * each specialist, and the {@link MergeAgent} reads the result.
 *
 * The agentic module runs a static {@code @Agent} method that takes only the {@link AgenticScope}
 * as a scope action and ignores its return value, so the method writes the state itself. The
 * {@code outputKey} and the {@code String} return type are still declared, because Quarkus checks
 * at build time that some agent provides the {@code specialistReplies} parameter of
 * {@link MergeAgent}, with a matching type.
 */
public class SpecialistRepliesCollector {

    static final String CURRENT_ROUTE = "currentRoute";
    static final String SPECIALIST_REPLIES = "specialistReplies";

    @Agent(description = "Collects the specialist replies of a fan-out", outputKey = SPECIALIST_REPLIES)
    public static String collect(AgenticScope scope) {
        String collected = scope.readState(SPECIALIST_REPLIES, "");
        String route = scope.readState(CURRENT_ROUTE, JevRouter.ROUTE_GENERAL);
        Object reply = scope.readState("reply");
        String updated = collected + "[" + route + "]\n" + reply + "\n\n";
        scope.writeState(SPECIALIST_REPLIES, updated);
        return updated;
    }
}
