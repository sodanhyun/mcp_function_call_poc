package com.example.gemini_report.config;

import com.example.gemini_report.langchain.models.GeminiEmbeddingModel;
import com.example.gemini_report.langchain.models.GeminiStreamingChatModel;
import com.example.gemini_report.langchain.Assistant;
import com.example.gemini_report.langchain.tools.CustomTools;
import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.types.HarmBlockThreshold;
import com.google.genai.types.HarmCategory;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.ThinkingConfig;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j와 관련된 모든 Bean 설정을 담당하는 클래스입니다.
 * AI 모델, 임베딩, 벡터 저장소, 대화 메모리 등 AI 서비스의 핵심 구성요소를 설정합니다.
 */
@Configuration
public class GeminiConfig {

    // Gemini 모델 설정을 위한 프로퍼티
    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model.chat}")
    private String chatModelName;

    @Value("${gemini.model.embedding}")
    private String embeddingModelName;

    // Milvus 벡터 저장소 설정을 위한 프로퍼티
    @Value("${milvus.host}")
    private String milvusHost;

    @Value("${milvus.port}")
    private Integer milvusPort;

    @Value("${milvus.collection-name}")
    private String milvusCollectionName;

    /**
     * `com.google.genai` 클라이언트를 Bean으로 등록합니다.
     * 이 클라이언트는 Gemini 모델과의 직접적인 통신을 담당합니다.
     *
     * @return `com.google.genai.Client` 인스턴스
     */
    @Bean
    public Client geminiClient() {
        return Client.builder()
                .apiKey(geminiApiKey)
                .build();
    }

    /**
     * 커스텀 Gemini 스트리밍 채팅 모델을 Bean으로 등록합니다.
     * 이 모델은 AI와의 실시간 대화를 가능하게 합니다.
     *
     * @param geminiClient `com.google.genai` 클라이언트
     * @return GeminiStreamingChatModel 인스턴스
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(Client geminiClient) {
        return GeminiStreamingChatModel.builder()
                .geminiClient(geminiClient)
                .modelName(chatModelName)
                .build();
    }

    /**
     * 커스텀 Gemini 임베딩 모델을 Bean으로 등록합니다.
     * 이 모델은 텍스트를 벡터로 변환하는 역할을 합니다.
     *
     * @param geminiClient `com.google.genai` 클라이언트
     * @return EmbeddingModel 인스턴스
     */
    @Bean
    public EmbeddingModel embeddingModel(Client geminiClient) {
        return new GeminiEmbeddingModel(geminiClient, embeddingModelName);
    }

    /**
     * Milvus 임베딩 저장소를 Bean으로 등록합니다.
     * 임베딩된 벡터 데이터를 저장하고 검색하는 데 사용됩니다.
     *
     * @return EmbeddingStore 인스턴스
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return MilvusEmbeddingStore.builder()
                .host(milvusHost)
                .port(milvusPort)
                .collectionName(milvusCollectionName)
                .dimension(3072) // Gemini 임베딩 모델의 차원 수
                .build();
    }

    /**
     * 임베딩 저장소를 활용하는 Content Retriever를 Bean으로 등록합니다.
     * RAG(Retrieval-Augmented Generation)의 핵심 요소로, 질문과 관련된 문서를 벡터 저장소에서 찾아옵니다.
     *
     * @param embeddingStore 임베딩 저장소
     * @param embeddingModel 임베딩 모델
     * @return ContentRetriever 인스턴스
     */
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .minScore(0.5)
                .build();
    }

    /**
     * 대화 메모리 제공자를 Bean으로 등록합니다.
     * 사용자별 대화 기록을 관리하여 멀티턴 대화를 가능하게 합니다.
     *
     * @return ChatMemoryProvider 인스턴스
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .chatMemoryStore(new InMemoryChatMemoryStore())
                .build();
    }

    /**
     * 최종적으로 AI 서비스를 생성하여 Bean으로 등록합니다.
     * 채팅 모델, 도구, 대화 메모리, RAG 등 모든 구성요소를 통합합니다.
     *
     * @param streamingChatLanguageModel 스트리밍 채팅 모델
     * @param customTools        사용자 정의 도구
     * @param chatMemoryProvider 대화 메모리 제공자
     * @param contentRetriever   RAG 컨텐츠 리트리버
     * @return Assistant 서비스 인스턴스
     */
    @Bean
    public Assistant assistant(StreamingChatLanguageModel streamingChatLanguageModel, CustomTools customTools, ChatMemoryProvider chatMemoryProvider, ContentRetriever contentRetriever) {
        return AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(customTools)
                .chatMemoryProvider(chatMemoryProvider)
                .contentRetriever(contentRetriever)
                .build();
    }
}
