package com.example.gemini_report.langchain;

import com.google.genai.Client;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LangChain4j의 EmbeddingModel 인터페이스를 `com.google.genai` 클라이언트를 사용하여 구현한 커스텀 클래스입니다.
 * LangChain4j 프레임워크가 Google의 제네릭 Gemini 클라이언트를 통해 임베딩을 생성할 수 있도록 하는 어댑터 역할을 합니다.
 */
@RequiredArgsConstructor
public class GeminiEmbeddingModel implements EmbeddingModel {

    private final Client geminiClient;
    private final String modelName;

    /**
     * 단일 텍스트 세그먼트를 임베딩합니다.
     * @param textSegment 임베딩할 텍스트 세그먼트
     * @return 임베딩 결과를 포함하는 Response 객체
     */
    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        // embedAll을 사용하여 단일 세그먼트를 임베딩
        return Response.from(embedAll(Collections.singletonList(textSegment)).content().get(0));
    }

    /**
     * 여러 텍스트 세그먼트를 한 번에 임베딩합니다.
     * @param textSegments 임베딩할 텍스트 세그먼트 리스트
     * @return 임베딩 결과 리스트를 포함하는 Response 객체
     */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<String> texts = textSegments.stream()
                .map(TextSegment::text)
                .collect(Collectors.toList());

        // com.google.genai 라이브러리는 현재 배치 임베딩을 직접 지원하지 않으므로,
        // 각 텍스트를 순차적으로 임베딩합니다.
        List<Embedding> embeddings = texts.stream()
                .map(text -> {
                    try {
                        EmbedContentResponse response = geminiClient.models.embedContent(modelName, text, EmbedContentConfig.builder().build());
                        return toLangChainEmbedding(response);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to embed text: " + text, e);
                    }
                })
                .collect(Collectors.toList());

        return Response.from(embeddings);
    }

    /**
     * `com.google.genai`의 EmbedContentResponse를 LangChain4j의 Embedding 객체로 변환합니다.
     * @param response `com.google.genai` 클라이언트로부터 받은 임베딩 응답
     * @return LangChain4j의 Embedding 객체
     */
    private Embedding toLangChainEmbedding(EmbedContentResponse response) {
        List<Float> embeddingValues = response.embeddings().orElse(Collections.emptyList()).stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No embedding found in response"))
                .values().orElse(Collections.emptyList());
        
        float[] floatArray = new float[embeddingValues.size()];
        for (int i = 0; i < embeddingValues.size(); i++) {
            floatArray[i] = embeddingValues.get(i);
        }
        return Embedding.from(floatArray);
    }
}
