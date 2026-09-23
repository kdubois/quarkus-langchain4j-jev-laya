package com.tripplanner.poc.jev;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * MicroProfile REST Client for the self-hosted Laya sidecar.
 * Base URL is configured via {@code quarkus.rest-client.laya.url}.
 */
@RegisterRestClient(configKey = "laya")
@Path("/v1/decision")
public interface LayaRestClient {

    @POST
    JevResponse evaluate(JevRequest request);
}
