package com.example.gemini_report.controller;

import com.example.gemini_report.dto.EmbeddingRequest;
import com.example.gemini_report.service.Assistant;
import com.example.gemini_report.service.EmbeddingService;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 에이전트와의 대화 및 임베딩 관리를 처리하는 API 컨트롤러입니다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {

    private final Assistant assistant;
    private final EmbeddingService embeddingService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 사용자의 메시지를 받아 AI와 대화하고, 응답을 스트리밍으로 반환합니다.
     *
     * @param message        사용자 메시지
     * @param conversationId 대화 ID (지정하지 않으면 새로운 대화 시작)
     * @return Server-Sent Events (SSE)를 통해 AI의 응답을 스트리밍하는 SseEmitter
     */
    @PostMapping(value = "/agent/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestParam("message") String message,
                           @RequestParam(value = "conversationId", required = false) String conversationId) {

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        String convId = (conversationId == null || conversationId.isBlank()) ? UUID.randomUUID().toString() : conversationId;

        // AI의 응답(TokenStream)을 비동기적으로 처리하여 클라이언트에 전송
        executor.execute(() -> {
            try {
                TokenStream tokenStream = assistant.chat(message, convId);

                // 첫 응답으로 대화 ID를 전송
                emitter.send(SseEmitter.event().name("conversationId").data(convId));

                tokenStream.onNext(token -> {
                            try {
                                emitter.send(SseEmitter.event().data(token));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .onComplete(response -> emitter.complete())
                        .onError(emitter::completeWithError)
                        .start();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 주어진 텍스트를 임베딩하여 벡터 저장소에 저장합니다.
     * RAG를 위한 지식 기반을 동적으로 추가하는 데 사용됩니다.
     *
     * @param request 임베딩할 텍스트를 포함하는 요청 DTO
     * @return 작업 성공 여부를 나타내는 ResponseEntity
     */
    @PostMapping("/embeddings")
    public ResponseEntity<String> embed(@RequestBody EmbeddingRequest request) {
        try {
            embeddingService.embedAndStore(request.getText());
            return ResponseEntity.ok("Text embedded and stored successfully.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to embed and store text: " + e.getMessage());
        }
    }
}
