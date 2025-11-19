package com.example.gemini_report.langchain.models;

import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.types.*;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LangChain4j의 `StreamingChatLanguageModel` 인터페이스를 `com.google.genai` 클라이언트를 사용하여 구현한 커스텀 클래스입니다.
 * 이 클래스는 LangChain4j 프레임워크가 Google의 제네릭 Gemini 클라이언트를 통해 스트리밍 채팅 및 함수 호출을 수행할 수 있도록 하는 어댑터 역할을 합니다.
 * `@RequiredArgsConstructor`는 Lombok 어노테이션으로, final 필드에 대한 생성자를 자동으로 생성하여 의존성 주입을 용이하게 합니다.
 * `@Builder`는 Lombok 어노테이션으로, 빌더 패턴을 사용하여 객체를 생성할 수 있도록 합니다.
 */
@RequiredArgsConstructor
@Builder
public class GeminiStreamingChatModel implements StreamingChatLanguageModel {

    // Google Gemini API 클라이언트 주입
    private final Client geminiClient;
    // 사용할 Gemini 채팅 모델의 이름
    private final String modelName;
    // JSON 직렬화/역직렬화를 위한 GSON 인스턴스
    private final Gson gson = new Gson();

    /**
     * LangChain4j 메시지 리스트와 도구 스펙을 받아 스트리밍 방식으로 응답을 생성합니다.
     * 이 메서드는 Gemini API의 `generateContentStream`을 호출하여 실시간으로 응답을 처리합니다.
     *
     * @param messages LangChain4j의 `ChatMessage` 리스트 (대화 기록)
     * @param toolSpecifications LangChain4j의 `ToolSpecification` 리스트 (AI가 사용할 수 있는 도구 명세)
     * @param handler 스트리밍 응답을 처리할 `StreamingResponseHandler<AiMessage>`
     */
    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        // LangChain4j ChatMessage 리스트를 Gemini API의 Content 리스트로 변환합니다.
        List<Content> googleAiMessages = MessageConverter.toGoogleAiMessages(messages);
        // LangChain4j ToolSpecification 리스트를 Gemini API의 Tool 리스트로 변환합니다.
        List<Tool> googleAiTools = ToolConverter.toGoogleAiTools(toolSpecifications);

        // `GenerateContentConfig`를 빌드하여 모델 생성 설정을 정의합니다.
        // 여기서는 도구들을 설정에 포함시킵니다.
        GenerateContentConfig config = GenerateContentConfig.builder()
                // TODO: 필요에 따라 ThinkingConfig, candidateCount, maxOutputTokens, safetySettings 등을 설정할 수 있습니다.
                .tools(googleAiTools) // 도구들을 요청에 포함시킵니다.
                .build();

        // 스트리밍 응답을 처리하기 위한 로직
        // `contentBuilder`는 스트리밍되는 텍스트 토큰을 누적합니다.
        AtomicReference<String> contentBuilder = new AtomicReference<>("");
        // `toolExecutionRequestRef`는 함수 호출 요청이 있을 경우 이를 저장합니다.
        AtomicReference<ToolExecutionRequest> toolExecutionRequestRef = new AtomicReference<>(null);

        // Gemini API 클라이언트를 호출하여 스트리밍 응답을 받습니다.
        geminiClient.models.generateContentStream(modelName, googleAiMessages, config)
                .forEach(response -> {
                    // 응답에 후보(candidate)가 존재하는지 확인합니다.
                    if (response.candidates().isPresent()) {
                        Candidate candidate = response.candidates().orElse(Collections.emptyList()).getFirst();
                        // 후보의 콘텐츠 파트들을 순회합니다.
                        candidate.content().flatMap(Content::parts).orElse(Collections.emptyList()).forEach(part -> {
                            // 텍스트 파트가 존재하는 경우
                            if (part.text().isPresent()) {
                                String token = part.text().get();
                                contentBuilder.updateAndGet(current -> current + token); // 토큰을 누적합니다.
                                handler.onNext(token); // 핸들러에 다음 토큰을 전달합니다.
                            }
                            // 함수 호출 파트가 존재하는 경우
                            else if (part.functionCall().isPresent()) {
                                // `FunctionCall` 정보를 추출하여 LangChain4j의 `ToolExecutionRequest`로 변환합니다.
                                FunctionCall functionCall = part.functionCall().get();
                                ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                                        .name(functionCall.name().orElse("")) // 도구 이름 설정
                                        .arguments(gson.toJson(functionCall.args().orElse(Collections.emptyMap()))) // 인자를 JSON 문자열로 변환
                                        .build();
                                toolExecutionRequestRef.set(toolExecutionRequest); // 함수 호출 요청을 저장합니다.
                            }
                        });
                    }
                });

        // 스트리밍 완료 후 최종 응답을 처리합니다.
        if (toolExecutionRequestRef.get() != null) {
            // 함수 호출 요청이 있었다면, 해당 요청을 포함하는 `AiMessage`를 완료 핸들러에 전달합니다.
            handler.onComplete(Response.from(AiMessage.from(toolExecutionRequestRef.get())));
        } else {
            // 텍스트 응답만 있었다면, 누적된 텍스트를 포함하는 `AiMessage`를 완료 핸들러에 전달합니다.
            handler.onComplete(Response.from(AiMessage.from(contentBuilder.get())));
        }
    }

    /**
     * LangChain4j 메시지 리스트를 받아 스트리밍 방식으로 응답을 생성합니다.
     * 이 메서드는 도구 사용이 없는 경우에 호출됩니다.
     * 내부적으로 `toolSpecifications`가 빈 리스트인 `generate` 메서드를 호출합니다.
     *
     * @param messages LangChain4j의 `ChatMessage` 리스트
     * @param handler 스트리밍 응답을 처리할 핸들러
     */
    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        // 도구 명세가 없는 경우, 빈 리스트를 전달하여 기존 `generate` 메서드를 호출합니다.
        generate(messages, Collections.emptyList(), handler);
    }
}