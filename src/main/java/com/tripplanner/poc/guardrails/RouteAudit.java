package com.tripplanner.poc.guardrails;

import com.tripplanner.poc.agentic.JevRouter.RouteDecision;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Keeps a short, in-memory history of the last routing decisions so the API can report which
 * specialists handled the request and which decision backend actually answered (including a stub
 * fallback). Mirrors the workshop's guardrail audit-log pattern.
 */
@ApplicationScoped
public class RouteAudit {

    public record AuditEntry(Instant timestamp, String request, List<String> routes, String rawChoice,
                             Double confidence, String mode, String effectiveBackend) {

        /** The primary route (the first specialist called). */
        public String route() {
            return routes.get(0);
        }
    }

    private static final int MAX_ENTRIES = 100;

    private final Deque<AuditEntry> entries = new ConcurrentLinkedDeque<>();

    public void log(String request, RouteDecision decision, String effectiveBackend) {
        entries.addLast(new AuditEntry(Instant.now(), request, decision.routes(), decision.rawChoice(),
                decision.confidence(), decision.mode(), effectiveBackend));
        while (entries.size() > MAX_ENTRIES) {
            entries.pollFirst();
        }
    }

    public AuditEntry latest() {
        AuditEntry entry = null;
        for (AuditEntry candidate : entries) {
            entry = candidate;
        }
        return entry;
    }

    public List<AuditEntry> recent() {
        return List.copyOf(entries);
    }
}
