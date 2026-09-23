package com.tripplanner.poc.jev;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * One Jev answer, keyed by the question id it was asked with. The populated fields depend on the
 * answer type: Choice carries choice/probabilities, Score carries score/legend/probabilities,
 * Noul carries the noul probability. Choice and Score also carry confidence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JevAnswer(
        String type,
        String choice,
        Double noul,
        Double score,
        Map<String, String> legend,
        Map<String, Double> probabilities,
        Double confidence
) {

    public double confidenceOrZero() {
        return confidence == null ? 0.0 : confidence;
    }

    public boolean isChoice() {
        return "choice".equals(type);
    }

    public boolean isScore() {
        return "score".equals(type);
    }

    public boolean isNoul() {
        return "noul".equals(type);
    }
}
