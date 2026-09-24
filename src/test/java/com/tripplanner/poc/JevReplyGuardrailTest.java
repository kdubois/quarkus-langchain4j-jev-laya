package com.tripplanner.poc;

import com.tripplanner.poc.guardrails.JevReplyGuardrail;
import com.tripplanner.poc.jev.DecisionClient;
import com.tripplanner.poc.jev.JevQuestion;
import com.tripplanner.poc.jev.JevRequest;
import com.tripplanner.poc.jev.JevResponse;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the decision-model output guardrail, using a fake {@link DecisionClient} to
 * control the exact decision and confidence returned.
 */
class JevReplyGuardrailTest {

    /** A DecisionClient that returns a canned Noul value for any question. */
    static final class FakeDecisionClient implements DecisionClient {
        private final Double noulValue;

        FakeDecisionClient(Double noulValue) {
            this.noulValue = noulValue;
        }

        @Override
        public JevResponse evaluate(JevRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Double noul(String state, String questionId, JevQuestion question) {
            return noulValue;
        }

        @Override
        public String defaultModel() {
            return "test";
        }

        @Override
        public String backend() {
            return "test";
        }
    }

    private OutputGuardrailRequest request(String reply, String customerRequest) {
        return GuardrailRequestBuilder.of(reply, Map.of("request", customerRequest));
    }

    private JevReplyGuardrail guardrail(Double noulValue) {
        JevReplyGuardrail guardrail = new JevReplyGuardrail();
        guardrail.setDecisionClient(new FakeDecisionClient(noulValue));
        return guardrail;
    }

    @Test
    void passesWhenModelIsConfidentTheReplyIsGood() {
        OutputGuardrailResult result = guardrail(0.95).validate(
                request("The rain starts Tuesday, so expect wet roads.", "What's the weather?"));
        assertFalse(result.isRetry());
    }

    @Test
    void retriesWhenModelIsConfidentTheReplyIsWrong() {
        OutputGuardrailResult result = guardrail(0.1).validate(
                request("Here is a generic answer.", "How do I cancel my reservation?"));
        assertTrue(result.isRetry());
    }

    @Test
    void passesAndFlagsWhenModelIsUncertain() {
        // Noul of 0.52 is within DECISION_MARGIN (0.1) of the 0.5 boundary: pass and flag.
        OutputGuardrailResult result = guardrail(0.52).validate(
                request("Maybe, I think it is fine.", "Is the car insured?"));
        assertFalse(result.isRetry());
    }

    @Test
    void passesWhenModelIsJustConfidentEnough() {
        // Noul of 0.61 is more than DECISION_MARGIN above 0.5: decisive pass.
        OutputGuardrailResult result = guardrail(0.61).validate(
                request("Yes, the car is fully insured.", "Is the car insured?"));
        assertFalse(result.isRetry());
    }

    @Test
    void retriesWhenModelIsJustConfidentTheReplyIsWrong() {
        // Noul of 0.39 is more than DECISION_MARGIN below 0.5: decisive retry.
        OutputGuardrailResult result = guardrail(0.39).validate(
                request("Our office is closed on weekends.", "How do I cancel my reservation?"));
        assertTrue(result.isRetry());
    }

    @Test
    void passesBlankReplies() {
        OutputGuardrailResult result = guardrail(0.1).validate(request("   ", "Hi"));
        assertFalse(result.isRetry());
    }
}
