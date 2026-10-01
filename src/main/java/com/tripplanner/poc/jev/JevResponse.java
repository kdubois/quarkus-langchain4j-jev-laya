package com.tripplanner.poc.jev;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JevResponse(String model, Map<String, JevAnswer> answers, JevUsage usage) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JevUsage(@JsonProperty("input_tokens") Integer inputTokens,
                           @JsonProperty("output_tokens") Integer outputTokens) {}
}
