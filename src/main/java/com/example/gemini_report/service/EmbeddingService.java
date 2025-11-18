package com.example.gemini_report.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 텍스트 임베딩 및 벡터 저장소 관리를 담당하는 서비스입니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 주어진 텍스트를 임베딩하여 벡터 저장소에 추가합니다.
     * 이 메서드는 RAG(Retrieval-Augmented Generation)를 위한 지식 기반을 구축하는 데 사용됩니다.
     *
     * @param text 임베딩하고 저장할 텍스트
     */
    public void embedAndStore(String text) {
        log.info("Embedding and storing text: '{}'", text);

        // 1. 텍스트를 TextSegment로 변환합니다.
        TextSegment segment = TextSegment.from(text);

        // 2. EmbeddingModel을 사용하여 TextSegment를 임베딩합니다.
        Embedding embedding = embeddingModel.embed(segment).content();

        // 3. 임베딩된 TextSegment를 EmbeddingStore에 추가합니다.
        embeddingStore.add(embedding, segment);

        log.info("Successfully embedded and stored the text.");
    }
}
