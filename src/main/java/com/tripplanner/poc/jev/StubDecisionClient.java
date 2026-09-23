package com.tripplanner.poc.jev;

import io.quarkus.logging.Log;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic, offline stand-in for a decision model. It applies simple keyword and length
 * heuristics so the agentic system can be demonstrated (and unit-tested) without an API key, a
 * network, or a downloaded model. Every answer it returns is a stub decision, not a real model
 * output.
 */
public class StubDecisionClient implements DecisionClient {

    private final String model;

    public StubDecisionClient() {
        this("stub");
    }

    public StubDecisionClient(String model) {
        this.model = model;
    }

    @Override
    public String defaultModel() {
        return model;
    }

    @Override
    public String backend() {
        return "stub";
    }

    @Override
    public JevResponse evaluate(JevRequest request) {
        Map<String, JevAnswer> answers = new LinkedHashMap<>();
        for (Map.Entry<String, JevQuestion> entry : request.questions().entrySet()) {
            String state = String.valueOf(request.state());
            answers.put(entry.getKey(), answer(state, entry.getKey(), entry.getValue()));
        }
        Log.debugf("Stub decision answered %d question(s): %s", answers.size(), answers.keySet());
        return new JevResponse(model, answers, new JevResponse.JevUsage(120, 8));
    }

    private JevAnswer answer(String state, String questionId, JevQuestion question) {
        return switch (question.type()) {
            case "choice" -> choice(state, question);
            case "score" -> scoreAnswer(state, questionId, question);
            case "noul" -> noulAnswer(state, questionId, question);
            default -> new JevAnswer("noul", null, 0.0, null, null, null, 0.0);
        };
    }

    private JevAnswer choice(String state, JevQuestion question) {
        Map<String, ?> criteria = asMap(question.criteria());
        if (criteria == null || criteria.isEmpty()) {
            return new JevAnswer("choice", null, null, null, null, null, 0.0);
        }
        List<String> options = List.copyOf(criteria.keySet());
        String best = options.get(0);
        int bestScore = -1;
        for (String option : options) {
            int score = keywordScore(state, option);
            if (score > bestScore) {
                bestScore = score;
                best = option;
            }
        }
        // No domain keyword matched: fall back to a general/greeting route, if one is offered.
        if (bestScore <= 0 && criteria.containsKey("general")) {
            best = "general";
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        for (String option : options) {
            probabilities.put(option, option.equals(best) ? 0.8 : 0.2 / (options.size() - 1));
        }
        return new JevAnswer("choice", best, null, null, null, probabilities, 0.8);
    }

    private int keywordScore(String state, String option) {
        String lower = state.toLowerCase(Locale.ROOT);
        return switch (option) {
            case "reservation" -> count(lower, "book", "booking", "reserve", "reservation", "cancel",
                    "modification", "modify", "pick-up", "pickup", "drop-off", "drop off");
            case "weather" -> count(lower, "weather", "forecast", "rain", "snow", "temperature",
                    "sunny", "storm", "climate");
            case "cost" -> count(lower, "cost", "price", "pricing", "how much", "total", "budget",
                    "fee", "charge", "rate", "quote");
            default -> count(lower, option);
        };
    }

    private int count(String text, String... words) {
        int total = 0;
        for (String word : words) {
            if (word.contains(" ") ? text.contains(word) : wordMatches(text, word)) {
                total++;
            }
        }
        return total;
    }

    /** Whole-word match so "you" does not count inside "booking". */
    private boolean wordMatches(String text, String word) {
        return text.matches("(?i).*\\b" + java.util.regex.Pattern.quote(word) + "\\b.*");
    }

    private JevAnswer scoreAnswer(String state, String questionId, JevQuestion question) {
        // The only Score question the system asks is about routing complexity (Simple/Moderate/Complex).
        double value = complexity(state);
        Map<String, Double> probabilities = new LinkedHashMap<>();
        probabilities.put("0", value < 1 ? 0.8 : 0.1);
        probabilities.put("1", value >= 1 && value < 2 ? 0.8 : 0.1);
        probabilities.put("2", value >= 2 ? 0.8 : 0.1);
        Map<String, String> legend = Map.of("0", "Simple", "1", "Moderate", "2", "Complex");
        return new JevAnswer("score", null, null, value, legend, probabilities, 0.85);
    }

    private double complexity(String state) {
        if (state == null) {
            return 0.0;
        }
        String lower = state.toLowerCase(Locale.ROOT);
        int length = state.length();
        int questionWords = count(lower, "how", "why", "which", "compare", "alternatives", "best",
                "recommend", "and", "versus", "vs");
        if (length > 140 || questionWords >= 2) {
            return 2.0;
        }
        if (length > 60 || questionWords == 1) {
            return 1.0;
        }
        return 0.0;
    }

    private JevAnswer noulAnswer(String state, String questionId, JevQuestion question) {
        if (state == null || state.isBlank()) {
            return new JevAnswer("noul", null, 0.0, null, null, null, 0.0);
        }
        String lower = state.toLowerCase(Locale.ROOT);
        boolean problematic = lower.contains("todo") || lower.contains("null")
                || lower.contains("error") || lower.contains("i don't know")
                || lower.contains("i'm sorry, i cannot");
        double value = problematic ? 0.2 : 0.9;
        return new JevAnswer("noul", null, value, null, null, null, 0.9);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> asMap(Object criteria) {
        return criteria instanceof Map ? (Map<String, ?>) criteria : null;
    }
}
