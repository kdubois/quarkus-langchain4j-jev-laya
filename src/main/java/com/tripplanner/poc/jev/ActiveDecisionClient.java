package com.tripplanner.poc.jev;

import com.tripplanner.poc.agentic.RoutingAuditListener;
import com.tripplanner.poc.guardrails.RouteAudit;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.listener.DecisionModelListener;
import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.request.Question;
import dev.langchain4j.model.decision.request.ScaleQuestion;
import dev.langchain4j.model.decision.request.YesNoQuestion;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import dev.langchain4j.model.decision.response.DecisionResponse;
import dev.langchain4j.model.decision.response.ScaleAnswer;
import dev.langchain4j.model.decision.response.YesNoAnswer;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Selects the configured LangChain4j decision model and adapts the older PoC client API. */
@ApplicationScoped
public class ActiveDecisionClient implements DecisionClient, DecisionModel {

    private final DecisionModel delegate;
    private final StubDecisionClient stub = new StubDecisionClient();
    private final String backend;
    private final String configuredModel;
    private final boolean configured;
    private final ThreadLocal<String> effectiveBackend = new ThreadLocal<>();
    private final List<DecisionModelListener> listeners;

    @Inject
    public ActiveDecisionClient(@Any Instance<DecisionModel> models, Config config, RouteAudit routeAudit) {
        String requested = config.getOptionalValue("decision.backend", String.class).orElse("jev").toLowerCase();
        this.backend = knownBackend(requested);
        this.configured = !"jev".equals(backend)
                || config.getOptionalValue("jev.api-key", String.class).filter(key -> !key.isBlank()).isPresent();
        this.delegate = "stub".equals(backend)
                ? null
                : models.select(ModelName.Literal.of(backend)).get();
        this.configuredModel = delegate == null ? "stub" : delegate.modelName();
        double routingThreshold = config.getOptionalValue("routing.fan-out-threshold", Double.class).orElse(0.5);
        this.listeners = List.of(new RoutingAuditListener(routeAudit, routingThreshold, this::lastEffectiveBackend));
        Log.infof("Decision backend configured to '%s' (model=%s)", backend(), defaultModel());
    }

    @Override
    public List<DecisionModelListener> listeners() {
        return listeners;
    }

    private static String knownBackend(String backend) {
        return switch (backend) {
            case "jev", "kev", "laya", "stub" -> backend;
            default -> {
                Log.warnf("Unknown decision.backend '%s'; using 'jev'", backend);
                yield "jev";
            }
        };
    }

    @Override
    public DecisionResponse doDecide(DecisionRequest request) {
        if (delegate == null || !configured) {
            effectiveBackend.set("stub");
            return stubResponse(request);
        }
        try {
            DecisionResponse response = delegate.decide(request);
            effectiveBackend.set(backend);
            return response;
        } catch (RuntimeException e) {
            Log.warnf("%s decision call failed (%s); falling back to stub", backend, e.getMessage());
            effectiveBackend.set("stub");
            return stubResponse(request);
        }
    }

    @Override
    public JevResponse evaluate(JevRequest request) {
        DecisionResponse response = decide(toDecisionRequest(request));
        Map<String, JevAnswer> answers = new LinkedHashMap<>();
        response.answers().forEach((id, answer) -> answers.put(id, toLegacyAnswer(answer)));
        return new JevResponse(response.modelName(), answers, null);
    }

    @Override
    public String defaultModel() {
        return configuredModel == null || configuredModel.isBlank() ? backend + "-latest" : configuredModel;
    }

    @Override
    public String backend() {
        return "stub".equals(backend) || !configured ? "stub" : backend;
    }

    @Override
    public String lastEffectiveBackend() {
        return effectiveBackend.get() == null ? backend() : effectiveBackend.get();
    }

    @Override
    public String modelName() {
        return defaultModel();
    }

    private DecisionResponse stubResponse(DecisionRequest request) {
        JevResponse response = stub.evaluate(toLegacyRequest(request));
        Map<String, DecisionAnswer> answers = new LinkedHashMap<>();
        response.answers().forEach((id, answer) -> answers.put(id, toDecisionAnswer(answer)));
        return DecisionResponse.builder().answers(answers).modelName("stub").build();
    }

    private JevRequest toLegacyRequest(DecisionRequest request) {
        Map<String, JevQuestion> questions = new LinkedHashMap<>();
        request.questions().forEach((id, question) -> questions.put(id, toLegacyQuestion(question)));
        return new JevRequest(request.input(), "stub", questions);
    }

    private DecisionRequest toDecisionRequest(JevRequest request) {
        Map<String, Question> questions = new LinkedHashMap<>();
        request.questions().forEach((id, question) -> questions.put(id, toDecisionQuestion(question)));
        DecisionRequest.Builder builder = DecisionRequest.builder().questions(questions);
        if (request.state() instanceof Map<?, ?> map) {
            Map<String, Object> input = new LinkedHashMap<>();
            map.forEach((key, value) -> input.put(String.valueOf(key), value));
            builder.input(input);
        } else {
            builder.input(String.valueOf(request.state()));
        }
        return builder.build();
    }

    private static JevQuestion toLegacyQuestion(Question question) {
        return switch (question) {
            case ChoiceQuestion choice -> JevQuestion.choice(choice.text(), choice.options());
            case YesNoQuestion yesNo -> JevQuestion.noul(yesNo.text());
            case ScaleQuestion scale -> JevQuestion.score(scale.text(), scale.levels());
            default -> throw new IllegalArgumentException("Unsupported decision question: " + question.getClass());
        };
    }

    private static Question toDecisionQuestion(JevQuestion question) {
        return switch (question.type()) {
            case "choice" -> ChoiceQuestion.of(String.valueOf(question.instructions()), stringMap(question.criteria()));
            case "noul" -> YesNoQuestion.of(String.valueOf(question.instructions()));
            case "score" -> ScaleQuestion.of(String.valueOf(question.instructions()), stringList(question.criteria()));
            default -> throw new IllegalArgumentException("Unsupported decision question type: " + question.type());
        };
    }

    private static DecisionAnswer toDecisionAnswer(JevAnswer answer) {
        return switch (answer.type()) {
            case "choice" -> ChoiceAnswer.builder()
                    .value(answer.choice())
                    .probabilities(answer.probabilities())
                    .confidence(answer.confidence())
                    .build();
            case "noul" -> YesNoAnswer.of(answer.noul());
            case "score" -> ScaleAnswer.builder()
                    .mean(answer.score())
                    .probabilities(indexedProbabilities(answer.probabilities()))
                    .confidence(answer.confidence())
                    .build();
            default -> throw new IllegalArgumentException("Unsupported decision answer type: " + answer.type());
        };
    }

    private static JevAnswer toLegacyAnswer(DecisionAnswer answer) {
        return switch (answer) {
            case ChoiceAnswer choice -> new JevAnswer("choice", choice.value(), null, null, null,
                    choice.probabilities(), choice.confidence());
            case YesNoAnswer yesNo -> new JevAnswer("noul", null, yesNo.probability(), null, null, null, null);
            case ScaleAnswer scale -> new JevAnswer("score", null, null, scale.mean(), null,
                    keyedProbabilities(scale.probabilities()), scale.confidence());
            default -> throw new IllegalArgumentException("Unsupported decision answer: " + answer.getClass());
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Object value) {
        return (Map<String, String>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        return (List<String>) value;
    }

    private static List<Double> indexedProbabilities(Map<String, Double> probabilities) {
        if (probabilities == null || probabilities.isEmpty()) {
            return List.of();
        }
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < probabilities.size(); i++) {
            result.add(probabilities.getOrDefault(String.valueOf(i), 0.0));
        }
        return result;
    }

    private static Map<String, Double> keyedProbabilities(List<Double> probabilities) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (int i = 0; i < probabilities.size(); i++) {
            result.put(String.valueOf(i), probabilities.get(i));
        }
        return result;
    }
}
