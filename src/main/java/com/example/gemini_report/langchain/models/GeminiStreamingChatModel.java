package com.example.gemini_report.langchain.models;

import com.google.genai.Client;
import com.google.genai.types.*;
import com.google.gson.Gson;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.StreamingResponseHandler;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import com.example.gemini_report.langchain.converters.MessageConverter;
import com.example.gemini_report.langchain.converters.ToolConverter;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LangChain4j의 StreamingChatLanguageModel 인터페이스를 `com.google.genai` 클라이언트를 사용하여 구현한 커스텀 클래스입니다.
 * LangChain4j 프레임워크가 Google의 제네릭 Gemini 클라이언트를 통해 스트리밍 채팅 및 함수 호출을 수행할 수 있도록 하는 어댑터 역할을 합니다.
 */
@RequiredArgsConstructor
@Builder
public class GeminiStreamingChatModel implements StreamingChatLanguageModel {

    private final Client geminiClient;
    private final String modelName;
    private final Gson gson = new Gson();

    /**
     * LangChain4j 메시지 리스트와 도구 스펙을 받아 스트리밍 방식으로 응답을 생성합니다.
     * @param messages LangChain4j의 ChatMessage 리스트
     * @param toolSpecifications LangChain4j의 ToolSpecification 리스트
     * @param handler 스트리밍 응답을 처리할 핸들러
     */
    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        List<Content> googleAiMessages = MessageConverter.toGoogleAiMessages(messages);
        List<Tool> googleAiTools = ToolConverter.toGoogleAiTools(toolSpecifications);

        // 스트리밍 응답을 처리하기 위한 로직
        AtomicReference<String> contentBuilder = new AtomicReference<>("");
        AtomicReference<ToolExecutionRequest> toolExecutionRequestRef = new AtomicReference<>(null);

        geminiClient.models.generateContentStream(modelName, googleAiMessages, GenerateContentConfig.builder().tools(googleAiTools).build())
                .forEach(response -> {
                    if (response.candidates().isPresent()) {
                        Candidate candidate = response.candidates().orElse(Collections.emptyList()).getFirst();
                        candidate.content().flatMap(Content::parts).orElse(Collections.emptyList()).forEach(part -> {
                            if (part.text().isPresent()) {
                                String token = part.text().get();
                                contentBuilder.updateAndGet(current -> current + token);
                                handler.onNext(token);
                            } else if (part.functionCall().isPresent()) {
                                // FunctionCall 처리
                                FunctionCall functionCall = part.functionCall().get();
                                ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                                        .name(functionCall.name().orElse(""))
                                        .arguments(gson.toJson(functionCall.args().orElse(Collections.emptyMap())))
                                        .build();
                                toolExecutionRequestRef.set(toolExecutionRequest);
                            }
                        });
                    }
                });

        // 스트리밍 완료 후 최종 응답 처리
        if (toolExecutionRequestRef.get() != null) {
            handler.onComplete(Response.from(AiMessage.from(toolExecutionRequestRef.get())));
        } else {
            handler.onComplete(Response.from(AiMessage.from(contentBuilder.get())));
        }
    }

    // Implement the abstract method from StreamingChatLanguageModel
    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generate(messages, Collections.emptyList(), handler); // Call the existing method with empty list for toolSpecifications
    }
}