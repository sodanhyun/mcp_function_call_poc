package com.example.gemini_report.controller;

import com.example.gemini_report.dto.ChatPromptRequest;
import com.example.gemini_report.dto.EmbeddingRequest;
import com.example.gemini_report.langchain.Assistant;
import com.example.gemini_report.service.embadding.EmbeddingService;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI 에이전트와의 대화 및 임베딩 관리를 처리하는 API 컨트롤러입니다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class AgentController {

    private final Assistant assistant;
    private final EmbeddingService embeddingService;

    @Qualifier("taskExecutor")
    private final TaskExecutor taskExecutor;

    /**
     * 사용자의 메시지를 받아 AI와 대화하고, 응답을 스트리밍으로 반환합니다.
     *
     * @param chatPromptRequest 사용자 메시지와 대화 ID를 포함하는 요청 DTO
     * @return Server-Sent Events (SSE)를 통해 AI의 응답을 스트리밍하는 SseEmitter
     */
    @PostMapping(value = "/agent/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatPromptRequest chatPromptRequest) {

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        String convId = (chatPromptRequest.getConversationId() == null || chatPromptRequest.getConversationId().isBlank()) ? UUID.randomUUID().toString() : chatPromptRequest.getConversationId();

        // AI의 응답(TokenStream)을 비동기적으로 처리하여 클라이언트에 전송
        taskExecutor.execute(() -> {
            try {
                TokenStream tokenStream = assistant.chat(chatPromptRequest.getMessage(), convId);

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
     * 주어진 텍스트를 비동기적으로 임베딩하여 벡터 저장소에 저장합니다.
     * 이 메서드는 요청을 즉시 수락하고 백그라운드에서 작업을 처리합니다.
     *
     * @param request 임베딩할 텍스트를 포함하는 요청 DTO
     * @return 작업의 비동기 실행 결과를 담은 CompletableFuture<ResponseEntity>
     */
    @PostMapping("/embeddings")
    public CompletableFuture<ResponseEntity<String>> embed(@RequestBody EmbeddingRequest request) {
        return embeddingService.embedAndStore(request.getText())
                .thenApply(v -> ResponseEntity.ok("Text embedding and storing process started successfully."))
                .exceptionally(ex -> {
                    log.error("Failed to execute embedAndStore task", ex);
                    Throwable cause = ex.getCause();
                    String errorMessage = (cause != null) ? cause.getMessage() : ex.getMessage();
                    return ResponseEntity.internalServerError().body("Failed to start embedding process: " + errorMessage);
                });
    }

    /**
     * 기존 임베딩을 모두 삭제하고 새로운 텍스트로 벡터 저장소를 비동기적으로 재설정합니다.
     * 이 메서드는 요청을 즉시 수락하고 백그라운드에서 작업을 처리합니다.
     *
     * @param request 재설정에 사용할 새로운 텍스트를 포함하는 요청 DTO
     * @return 작업의 비동기 실행 결과를 담은 CompletableFuture<ResponseEntity>
     */
    @PostMapping("/embeddings/reset")
    public CompletableFuture<ResponseEntity<String>> resetAndEmbed(@RequestBody EmbeddingRequest request) {
        return embeddingService.resetAndEmbed(request.getText())
                .thenApply(v -> ResponseEntity.ok("Embedding store reset and new text embedding process started successfully."))
                .exceptionally(ex -> {
                    log.error("Failed to execute resetAndEmbed task", ex);
                    Throwable cause = ex.getCause();
                    String errorMessage = (cause != null) ? cause.getMessage() : ex.getMessage();
                    return ResponseEntity.internalServerError().body("Failed to start reset and embedding process: " + errorMessage);
                });
    }
}
