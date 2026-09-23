package com.tripplanner.poc.jev;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * One typed question sent to Jev. Mirrors the three TypeSafe primitives:
 * Choice (pick an option), Score (rate on a rubric) and Noul (yes/no probability).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JevQuestion(String type, Object instructions, Object criteria) {

    public static JevQuestion choice(String instructions, Map<String, String> criteria) {
        return new JevQuestion("choice", instructions, criteria);
    }

    public static JevQuestion score(String instructions, List<String> criteria) {
        return new JevQuestion("score", instructions, criteria);
    }

    public static JevQuestion noul(String instructions) {
        return new JevQuestion("noul", instructions, null);
    }
}
