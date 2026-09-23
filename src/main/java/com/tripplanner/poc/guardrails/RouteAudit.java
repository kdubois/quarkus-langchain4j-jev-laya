package com.tripplanner.poc.guardrails;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Keeps a short, in-memory history of the last Jev routing decisions so the API can report which
 * specialist handled the request. Mirrors the workshop's guardrail audit-log pattern.
 */
@ApplicationScoped
public class RouteAudit {

    public record AuditEntry(Instant timestamp, String request, String route, String rawChoice) {}

    private static final int MAX_ENTRIES = 100;

    private final Deque<AuditEntry> entries = new ConcurrentLinkedDeque<>();

    public void log(String request, String route, String rawChoice) {
        entries.addLast(new AuditEntry(Instant.now(), request, route, rawChoice));
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
