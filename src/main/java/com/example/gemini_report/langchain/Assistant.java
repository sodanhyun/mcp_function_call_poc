package com.example.gemini_report.langchain;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * LangChain4j의 AI 서비스를 정의하는 인터페이스입니다.
 * 이 인터페이스를 통해 AI 모델과 상호작용하며, 대화 기록 관리, 시스템 메시지 설정, 스트리밍 응답 등을 처리합니다.
 *
 * @AiService 어노테이션을 통해 구현체가 자동으로 생성됩니다.
 */

@AiService
public interface Assistant {

    /**
     * 사용자의 메시지를 받아 스트리밍 방식으로 AI의 응답을 반환합니다.
     *
     * @param message    사용자의 메시지
     * @param memoryId   각 대화를 고유하게 식별하는 ID. 이를 통해 멀티턴 대화가 가능해집니다.
     * @return AI의 응답을 담은 토큰 스트림 (TokenStream)
     */
    @SystemMessage("당신은 대화할 수 있고 도구를 사용하여 질문에 답할 수 있는 유용한 어시스턴트입니다.")
    TokenStream chat(@UserMessage String message, @MemoryId Object memoryId);
}
