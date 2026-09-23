package com.tripplanner.poc.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripResponse(
        String request,
        String reply,
        String route,
        String rawChoice,
        String backend,
        String model,
        boolean live
) {
}
