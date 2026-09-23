package com.tripplanner.poc.jev;

import java.util.Map;

/**
 * Abstraction over a System One *decision* model. The same three primitives (Choice, Score, Noul)
 * and the same request/answer shape are shared by all backends, so the agentic system is written
 * once against this interface and can be pointed at any of:
 *
 * <ul>
 *   <li>{@link JevApiClient} — the hosted TypeSafe Jev endpoint over HTTP.</li>
 *   <li>{@link LayaDecisionClient} — a self-hosted Laya sidecar over HTTP (open source, Apache 2.0).</li>
 *   <li>{@link StubDecisionClient} — a deterministic offline stand-in.</li>
 * </ul>
 *
 * The active backend is selected by configuration ({@code decision.backend} = {@code jev} |
 * {@code laya} | {@code stub}); see {@link ActiveDecisionClient}.
 */
public interface DecisionClient {

    JevResponse evaluate(JevRequest request);

    /** The configured model name for this backend. */
    String defaultModel();

    /**
     * A short identifier for this backend, e.g. {@code jev}, {@code laya}, {@code stub}. Used for
     * logging and surfaced by the API so you can tell which model actually made the decision.
     */
    String backend();

    /**
     * Convenience for a single Choice question. Returns the chosen option.
     */
    default String choose(String state, String questionId, JevQuestion question) {
        JevResponse response = evaluate(new JevRequest(state, defaultModel(), Map.of(questionId, question)));
        JevAnswer answer = response.answers().get(questionId);
        if (answer == null || !answer.isChoice() || answer.choice() == null) {
            throw new IllegalStateException(backend() + " returned no choice for question '" + questionId + "'");
        }
        return answer.choice();
    }

    /**
     * Convenience for a single Score question. Returns the score (or null if absent).
     */
    default Double score(String state, String questionId, JevQuestion question) {
        JevResponse response = evaluate(new JevRequest(state, defaultModel(), Map.of(questionId, question)));
        JevAnswer answer = response.answers().get(questionId);
        return answer == null ? null : answer.score();
    }

    /**
     * Convenience for a single Noul question. Returns the yes-probability in [0, 1] (or null).
     */
    default Double noul(String state, String questionId, JevQuestion question) {
        JevResponse response = evaluate(new JevRequest(state, defaultModel(), Map.of(questionId, question)));
        JevAnswer answer = response.answers().get(questionId);
        return answer == null ? null : answer.noul();
    }
}
