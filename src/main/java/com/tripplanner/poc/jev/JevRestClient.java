package com.tripplanner.poc.jev;

import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * MicroProfile REST Client for the hosted TypeSafe Jev System One endpoint.
 * Base URL is configured via {@code quarkus.rest-client.jev.url}.
 */
@RegisterRestClient(configKey = "jev")
@Path("/v1/systemone")
public interface JevRestClient {

    @POST
    JevResponse evaluate(@HeaderParam("Authorization") String bearerToken, JevRequest request);
}
