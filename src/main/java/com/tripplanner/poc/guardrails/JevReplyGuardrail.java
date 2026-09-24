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
 * request. The returned probability is read directly and the decision is based on the *margin*
 * from the 0.5 boundary:
 *
 * <ul>
 *   <li>probability clearly &gt; 0.5 -> pass</li>
 *   <li>probability clearly &lt; 0.5 -> retry with guidance</li>
 *   <li>probability within the margin of 0.5 (uncertain) -> pass and flag for review</li>
 * </ul>
 *
 * The margin keeps a near-50/50 answer out of driving an automatic retry, while still catching a
 * confident "no" (a reply the model is sure does not address the request). This demonstrates using
 * a decision model as a cheap guardrail layer. The active model is whatever {@code decision.backend}
 * selects.
 */
@ApplicationScoped
public class JevReplyGuardrail implements OutputGuardrail {

    private static final Logger LOG = Logger.getLogger(JevReplyGuardrail.class);

    /**
     * Decisive margin from the 0.5 boundary. A probability at least this far above 0.5 passes, at
     * least this far below 0.5 retries; closer than this either way is treated as uncertain and
     * passes with a flag for review.
     */
    private static final double DECISION_MARGIN = 0.1;

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

        LOG.infof("Reply guardrail [%s]: addresses_request=%.2f (margin from 0.5=%.2f)",
                client().backend(), addressIt == null ? -1 : addressIt,
                addressIt == null ? -1 : addressIt - 0.5);

        if (addressIt == null) {
            LOG.warnf("Model returned no Noul value; passing the reply and flagging it for review");
            return success();
        }
        if (addressIt < 0.5 - DECISION_MARGIN) {
            LOG.infof("Model is confident the reply does not address the request (%.2f); requesting a retry", addressIt);
            return retry("The reply does not directly address the customer's request. "
                    + "Re-answer using only the customer's actual question and keep it concise.");
        }
        if (addressIt > 0.5 + DECISION_MARGIN) {
            return success();
        }
        LOG.warnf("Model was uncertain (%.2f); passing the reply and flagging it for review", addressIt);
        return success();
    }
}
