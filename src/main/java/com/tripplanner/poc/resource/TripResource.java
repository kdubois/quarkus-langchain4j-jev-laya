package com.tripplanner.poc.resource;

import com.tripplanner.poc.agentic.TripAdvisorSystem;
import com.tripplanner.poc.guardrails.RouteAudit;
import com.tripplanner.poc.jev.ActiveDecisionClient;
import com.tripplanner.poc.model.TripResponse;
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
        String reply = tripAdvisorSystem.planTrip(request);
        RouteAudit.AuditEntry decisionEntry = routeAudit.latest();
        TripResponse response = new TripResponse(
                request,
                reply,
                decisionEntry == null ? null : decisionEntry.route(),
                decisionEntry == null ? null : decisionEntry.rawChoice(),
                decision.backend(),
                decision.defaultModel(),
                !decision.backend().equals("stub"));
        return Response.ok(response).build();
    }

    @GET
    @Path("/backend")
    public Map<String, Object> backendStatus() {
        return Map.of(
                "backend", decision.backend(),
                "model", decision.defaultModel());
    }
}
