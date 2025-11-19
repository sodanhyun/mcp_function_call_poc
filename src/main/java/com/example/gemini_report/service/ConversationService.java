package com.example.gemini_report.service;

import com.example.gemini_report.config.UserContextHolder;
import com.example.gemini_report.dto.ChatPromptRequest;
import com.example.gemini_report.langchain.Agent;
import dev.langchain4j.service.TokenStream;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

/**
 * AI 에이전트와의 대화 로직을 캡슐화하는 서비스 클래스입니다.
 * `AgentController`의 복잡성을 줄이고, 대화 처리와 관련된 모든 로직을 이곳에서 관리합니다.
 * `@Service` 어노테이션은 이 클래스가 비즈니스 계층의 컴포넌트임을 나타냅니다.
 * `@RequiredArgsConstructor`는 Lombok 어노테이션으로, final 필드에 대한 생성자를 자동으로 생성하여 의존성 주입을 용이하게 합니다.
 * `@Slf4j`는 Lombok 어노테이션으로, 로깅을 위한 `log` 객체를 자동으로 생성합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    // LangChain4j Agent 인터페이스의 구현체를 주입받습니다.
    private final Agent agent;

    // 비동기 작업을 위한 스레드 풀을 주입받습니다.
    @Qualifier("taskExecutor")
    private final TaskExecutor taskExecutor;

    /**
     * 사용자의 메시지를 받아 AI와 대화하고, 응답을 스트리밍으로 클라이언트에게 전송합니다.
     * 이 메서드는 `SseEmitter`를 사용하여 Server-Sent Events (SSE) 방식으로 실시간 응답을 처리합니다.
     *
     * @param chatPromptRequest 사용자 메시지와 대화 ID를 포함하는 요청 DTO
     * @param request 현재 HTTP 요청 객체 (URI 확인용)
     * @return `SseEmitter` 객체. 클라이언트에게 이벤트를 스트리밍하는 데 사용됩니다.
     */
    public SseEmitter startChat(ChatPromptRequest chatPromptRequest, HttpServletRequest request, String username) {
        // 요청 URI가 "/agent/report"로 끝나는 경우, 보고서 생성을 위한 특정 프롬프트 형식을 적용합니다.
        if(request.getRequestURI().endsWith("/agent/report")) { // 설정 값 사용
            chatPromptRequest.setMessage(String.format("""
                      제공받은 데이터셋을 분석하여, 전체 요약과 상세 보고서를 모두 포함하는 마크다운 형식의 리포트를 생성하세요.\\n\\
                      리포트는 다음 항목을 포함해야 합니다:\\n\\
                      \\n\\
                      # 총괄 요약\\n\\
                      - 데이터의 핵심 인사이트와 결론 요약\\n\\
                      \\n\\
                      # 상세 분석\\n\\
                      - 섹션별 상세 분석\\n\\
                      - 표와 리스트, 필요시 그래프 링크 포함 가능\\n\\
                      \\n\\
                      # 결론 및 제언\\n\\
                      - 데이터 기반의 결론과 향후 조치/추천 사항\\n\\
                      \\n\\
                      **참고**:\\n\\
                      - 항상 Markdown 형식 사용 (헤더, 리스트, 표, 코드블록 등)\\n\\
                      - 요약은 주요 포인트를 간결하게\\n\\
                      - 상세 분석은 항목별로 구체적 내용을 포함\\n\\
                      원본 요청:\\n\\
                      %s
                    """, chatPromptRequest.getMessage())); // 설정 값 사용
        }

        // 새로운 SseEmitter를 생성합니다. 타임아웃은 Long.MAX_VALUE로 설정하여 사실상 무한대입니다.
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        // 대화 ID가 요청에 포함되어 있지 않다면 새로운 ID를 생성합니다.
        String convId = (chatPromptRequest.getConversationId() == null || chatPromptRequest.getConversationId().isBlank())
                ? UUID.randomUUID().toString()
                : chatPromptRequest.getConversationId();

        // AI의 응답(TokenStream)을 비동기적으로 처리하여 클라이언트에 전송합니다.
        // `taskExecutor`를 사용하여 별도의 스레드에서 실행됩니다.
        taskExecutor.execute(() -> {
            try {
                // 현재 스레드에 사용자 이름을 설정하여, 도구 호출 등에서 사용자 컨텍스트를 활용할 수 있도록 합니다.
                UserContextHolder.setUserName(username);
                // Agent의 chat 메서드를 호출하여 Gemini 모델과 상호작용합니다.
                // 스트리밍 방식으로 응답을 받으며, 각 토큰을 클라이언트에게 전송합니다.
                TokenStream tokenStream = agent.chat(chatPromptRequest.getMessage(), convId);

                // 첫 응답으로 대화 ID를 전송합니다.
                emitter.send(SseEmitter.event().name("conversationId").data(convId));

                // 스트리밍 응답의 각 토큰을 처리합니다.
                tokenStream.onNext(token -> {
                            try {
                                // 각 토큰을 SSE 이벤트로 클라이언트에게 전송합니다.
                                emitter.send(SseEmitter.event().data(token));
                            } catch (IOException e) {
                                // 토큰 전송 중 오류 발생 시 emitter를 오류와 함께 완료합니다.
                                log.error("SSE 토큰 전송 중 오류 발생: {}", e.getMessage());
                                emitter.completeWithError(e);
                            }
                        })
                        // 스트리밍 완료 시 emitter를 완료합니다.
                        .onComplete(response -> emitter.complete())
                        // 스트리밍 중 오류 발생 시 emitter를 오류와 함께 완료합니다.
                        .onError(emitter::completeWithError)
                        // 스트리밍을 시작합니다.
                        .start();
            } catch (Exception e) {
                // 예외 발생 시 emitter를 오류와 함께 완료합니다.
                log.error("채팅 처리 중 오류 발생: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }finally {
                // 요청 처리 완료 후 스레드 로컬에 저장된 사용자 정보를 제거합니다.
                UserContextHolder.clear();
                // 최종적으로 emitter를 완료합니다. (onComplete/onError에서 이미 호출될 수 있으나, 안전을 위해)
                // emitter.complete(); // 이중 호출 방지를 위해 주석 처리
            }
        });

        return emitter;
    }
}
