package com.tripplanner.poc;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.mockito.Mockito;

import java.util.Map;

/** Builds an {@link OutputGuardrailRequest} for unit tests. */
public final class GuardrailRequestBuilder {

    private GuardrailRequestBuilder() {}

    public static OutputGuardrailRequest of(String text, Map<String, Object> variables) {
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from(text))
                .build();
        GuardrailRequestParams params = GuardrailRequestParams.builder()
                .userMessageTemplate("You are a helpful assistant.")
                .variables(variables)
                .build();
        return OutputGuardrailRequest.builder()
                .responseFromLLM(chatResponse)
                .requestParams(params)
                .chatExecutor(Mockito.mock(ChatExecutor.class))
                .build();
    }
}
