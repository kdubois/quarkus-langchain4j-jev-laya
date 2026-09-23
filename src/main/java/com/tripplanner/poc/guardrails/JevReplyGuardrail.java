package com.tripplanner.poc.guardrails;

import com.tripplanner.poc.jev.ActiveDecisionClient;
import com.tripplanner.poc.jev.DecisionClient;
import com.tripplanner.poc.jev.JevQuestion;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * An output guardrail driven by a System One decision (Jev or Laya) instead of an LLM. It asks the
 * model, as a yes/no (Noul) question, whether the drafted reply actually addresses the customer's
 * request, and uses the returned confidence to decide:
 *
 * <ul>
 *   <li>high confidence the answer is good  -> pass</li>
 *   <li>high confidence the answer is not good -> retry with guidance</li>
 *   <li>low confidence (uncertain) -> pass and log the uncertainty for review</li>
 * </ul>
 *
 * This demonstrates using a decision model as a cheap, reliable guardrail layer. The active model
 * is whatever {@code decision.backend} selects.
 */
@ApplicationScoped
public class JevReplyGuardrail implements OutputGuardrail {

    private static final Logger LOG = Logger.getLogger(JevReplyGuardrail.class);

    /** Act autonomously when the model is at least this confident. Below it, pass and flag for review. */
    private static final double CONFIDENCE_THRESHOLD = 0.75;

    @Inject
    ActiveDecisionClient activeClient;

    /** Overrides the injected client for unit tests. */
    private DecisionClient override;

    public void setDecisionClient(DecisionClient decision) {
        this.override = decision;
    }

    private DecisionClient client() {
        DecisionClient chosen = override != null ? override : activeClient;
        if (chosen == null) {
            throw new IllegalStateException("No DecisionClient available for the reply guardrail");
        }
        return chosen;
    }

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        String text = request.responseFromLLM().aiMessage().text();
        if (text == null || text.isBlank()) {
            return success();
        }
        String customerRequest = String.valueOf(request.requestParams().variables().get("request"));

        String state = "Customer request: " + customerRequest + "\n\nDrafted reply: " + text;
        Double addressIt = client().noul(state, "addresses_request", JevQuestion.noul(
                "Does the drafted reply directly address the customer's request?"));
        Double confidence = addressIt == null ? 0.0 : Math.abs(addressIt - 0.5) * 2;

        LOG.infof("Reply guardrail [%s]: addresses_request=%.2f, confidence=%.2f",
                client().backend(), addressIt, confidence);

        if (confidence < CONFIDENCE_THRESHOLD) {
            LOG.warnf("Model was not confident (%.2f); passing the reply and flagging it for review", confidence);
            return success();
        }
        if (addressIt >= 0.5) {
            return success();
        }
        return retry("The reply does not directly address the customer's request. "
                + "Re-answer using only the customer's actual question and keep it concise.");
    }
}
