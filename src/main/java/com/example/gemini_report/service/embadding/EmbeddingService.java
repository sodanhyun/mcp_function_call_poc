package com.example.gemini_report.service.embadding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 텍스트 임베딩 및 벡터 저장소 관리를 담당하는 서비스입니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingStoreManager embeddingStoreManager;
    private static final int MAX_TOKENS_PER_SEGMENT = 250;

    @Qualifier("taskExecutor")
    private final TaskExecutor taskExecutor;
    /**
     * 주어진 텍스트를 임베딩하여 벡터 저장소에 추가합니다.
     * 이 메서드는 RAG(Retrieval-Augmented Generation)를 위한 지식 기반을 구축하는 데 사용됩니다.
     *
     * @param text 임베딩하고 저장할 텍스트
     */
    public CompletableFuture<Void> embedAndStore(String text) {
        return CompletableFuture.runAsync(() -> {
            log.info("Embedding and storing text: '{}'", text);

            // 1. 텍스트를 TextSegment로 변환합니다.
            List<TextSegment> segments = segmentText(text).stream().map(TextSegment::from).toList();

            // 2. EmbeddingModel을 사용하여 TextSegment를 임베딩합니다.
            Response<List<Embedding>> embedding = embeddingModel.embedAll(segments);

            // 3. 임베딩된 TextSegment를 EmbeddingStore에 추가합니다.
            embeddingStore.addAll(embedding.content(), segments);

            log.info("Successfully embedded and stored the text.");
        }, taskExecutor);
    }

    /**
     * 기존 벡터 저장소의 모든 데이터를 삭제하고, 주어진 텍스트로 새로 임베딩하여 저장합니다.
     * 지식 기반을 완전히 초기화하고 새로운 데이터로 교체할 때 사용됩니다.
     *
     * @param text 새로 임베딩하고 저장할 텍스트
     */
    public CompletableFuture<Void> resetAndEmbed(String text) {
        return CompletableFuture.runAsync(() -> {
            log.info("Resetting embedding store and embedding new text.");

            // 1. EmbeddingStoreManager를 사용하여 저장소를 초기화합니다.
            embeddingStoreManager.reset();

            // 2. 새로운 텍스트로 임베딩 및 저장을 수행합니다.
            embedAndStore(text);

            log.info("Successfully reset and embedded the new text.");
        }, taskExecutor);
    }

     // 원하는 최대 토큰(단어) 수

    /**
     * 긴 텍스트를 문장 단위로 나누고, 각 세그먼트가 최대 토큰 수를 넘지 않도록 분할합니다.
     *
     * @param text 원본 긴 텍스트
     * @return 텍스트 세그먼트 리스트
     */
    private List<String> segmentText(String text) {
        List<String> segments = new ArrayList<>();

        // 대략 문장 단위로 분할 ('.', '?' 등 기준), 간단하게 '.' 기준 예시
        String[] sentences = text.split("(?<=[.!?])\\s+");

        StringBuilder currentSegment = new StringBuilder();
        int currentTokenCount = 0;

        for (String sentence : sentences) {
            int sentenceTokenCount = countTokens(sentence);

            // 현재 세그먼트에 문장 더했을 때 최대 토큰 수 초과 시, 세그먼트 나누기
            if (currentTokenCount + sentenceTokenCount > MAX_TOKENS_PER_SEGMENT) {
                if (!currentSegment.isEmpty()) {
                    segments.add(currentSegment.toString().trim());
                    currentSegment.setLength(0);
                    currentTokenCount = 0;
                }
            }

            currentSegment.append(sentence).append(" ");
            currentTokenCount += sentenceTokenCount;
        }

        // 마지막 세그먼트가 남아있다면 추가
        if (!currentSegment.isEmpty()) {
            segments.add(currentSegment.toString().trim());
        }

        return segments;
    }

    private int countTokens(String text) {
        StringTokenizer tokenizer = new StringTokenizer(text);
        return tokenizer.countTokens();
    }
}

