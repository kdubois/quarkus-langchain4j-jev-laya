package com.tripplanner.poc.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TripResponse(
        String request,
        String reply,
        String route,
        List<String> routes,
        String routingMode,
        String backend,
        String model,
        boolean live
) {
}
