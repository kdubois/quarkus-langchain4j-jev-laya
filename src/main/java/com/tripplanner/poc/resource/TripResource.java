package com.tripplanner.poc.resource;

import com.tripplanner.poc.agentic.TripAdvisorSystem;
import com.tripplanner.poc.guardrails.RouteAudit;
import com.tripplanner.poc.jev.ActiveDecisionClient;
import com.tripplanner.poc.model.TripResponse;
import dev.langchain4j.guardrail.OutputGuardrailException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/trip")
@Produces(MediaType.APPLICATION_JSON)
public class TripResource {

    @Inject
    TripAdvisorSystem tripAdvisorSystem;

    @Inject
    ActiveDecisionClient decision;

    @Inject
    RouteAudit routeAudit;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response planTrip(Map<String, String> body) {
        String request = body == null ? null : body.get("request");
        if (request == null || request.isBlank()) {
            return Response.status(400)
                    .entity(Map.of("error", "missing_request", "message", "'request' is required."))
                    .build();
        }
        routeAudit.beginRequest();
        String reply;
        try {
            reply = tripAdvisorSystem.planTrip(request);
        } catch (RuntimeException e) {
            routeAudit.clearRequest();
            if (!isGuardrailRejection(e)) {
                throw e;
            }
            // Every retry was rejected by the reply guardrail: report it instead of a bare 500.
            return Response.status(502)
                    .entity(Map.of("error", "reply_rejected",
                            "message", "The drafted reply was rejected by the reply guardrail after all retries."))
                    .build();
        }
        RouteAudit.AuditEntry decisionEntry = routeAudit.current();
        routeAudit.clearRequest();
        // The backend/model/live fields report the decision backend that actually answered the
        // request (from the audit entry), which may be the stub even when a real model is
        // configured. Falls back to the configured backend if no audit entry exists.
        String effectiveBackend = decisionEntry != null && decisionEntry.effectiveBackend() != null
                ? decisionEntry.effectiveBackend()
                : decision.backend();
        String model = "stub".equals(effectiveBackend) ? "stub" : decision.defaultModel();
        TripResponse response = new TripResponse(
                request,
                reply,
                decisionEntry == null ? null : decisionEntry.route(),
                decisionEntry == null ? null : decisionEntry.routes(),
                decisionEntry == null ? null : decisionEntry.mode(),
                effectiveBackend,
                model,
                !"stub".equals(effectiveBackend));
        return Response.ok(response).build();
    }

    private static boolean isGuardrailRejection(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof OutputGuardrailException) {
                return true;
            }
        }
        return false;
    }

    @GET
    @Path("/backend")
    public Map<String, Object> backendStatus() {
        return Map.of(
                "backend", decision.backend(),
                "model", decision.defaultModel());
    }
}
