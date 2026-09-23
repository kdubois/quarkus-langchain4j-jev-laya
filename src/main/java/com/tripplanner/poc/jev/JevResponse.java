package com.tripplanner.poc.jev;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JevResponse(String model, Map<String, JevAnswer> answers, JevUsage usage) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JevUsage(Integer inputTokens, Integer outputTokens) {}
}
