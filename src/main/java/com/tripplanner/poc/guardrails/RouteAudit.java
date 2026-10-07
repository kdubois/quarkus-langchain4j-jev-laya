package com.tripplanner.poc.guardrails;

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

    public record AuditEntry(Instant timestamp, String request, List<String> routes,
                             String mode, String effectiveBackend) {

        /** The primary route (the first specialist called). */
        public String route() {
            return routes.get(0);
        }
    }

    private static final int MAX_ENTRIES = 100;

    private final Deque<AuditEntry> entries = new ConcurrentLinkedDeque<>();
    private final ThreadLocal<AuditEntry> current = new ThreadLocal<>();

    public void beginRequest() {
        current.remove();
    }

    public void log(String request, List<String> routes, String mode, String effectiveBackend) {
        AuditEntry entry = new AuditEntry(Instant.now(), request, List.copyOf(routes), mode, effectiveBackend);
        current.set(entry);
        entries.addLast(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.pollFirst();
        }
    }

    public AuditEntry current() {
        return current.get();
    }

    public void clearRequest() {
        current.remove();
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
