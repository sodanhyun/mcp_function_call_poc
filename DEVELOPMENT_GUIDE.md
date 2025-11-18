# 개발 가이드 (Development Guide) - LangChain4j Refactoring

## 1. 서론 (Introduction)

### 1.1. 프로젝트 개요 (Project Overview)
이 프로젝트는 **LangChain4j** 프레임워크를 기반으로 리팩토링된 Spring Boot 애플리케이션입니다. Google의 Gemini 모델을 활용하여 다음과 같은 고급 기능을 제공합니다.

-   **멀티턴 대화 (Multi-turn Conversation)**: 대화 기록을 관리하여 문맥을 이해하는 연속적인 대화가 가능합니다.
-   **스트리밍 응답 (Streaming Responses)**: 모델의 응답을 토큰 단위로 실시간 전송하여 사용자 경험을 향상시킵니다.
-   **어노테이션 기반 도구 사용 (Annotation-based Tools)**: `@Tool` 어노테이션만으로 Gemini가 호출할 수 있는 외부 함수(도구)를 손쉽게 추가하고 관리할 수 있습니다.
-   **RAG (Retrieval-Augmented Generation)**: **Milvus** 벡터 데이터베이스와 연동하여, 저장된 문서나 데이터를 기반으로 더 정확하고 풍부한 답변을 생성합니다.

### 1.2. 개발 가이드 목적 (Purpose of this Guide)
이 문서는 LangChain4j 기반으로 재설계된 프로젝트의 아키텍처, 주요 컴포넌트, 개발 환경 설정 및 확장 방법을 안내합니다. 개발자는 이 가이드를 통해 새로운 구조를 빠르게 파악하고 기능을 효율적으로 추가 및 유지보수할 수 있습니다.

## 2. 개발 환경 설정 (Development Environment Setup)

### 2.1. 선수 지식 (Prerequisites)
-   Java & Spring Boot
-   LangChain4j 프레임워크 기본 개념
-   Docker 및 Docker Compose
-   Google Gemini API Key (GCP Vertex AI 대신 `com.google.genai` 클라이언트 사용)

### 2.2. 인프라 실행 (Running Infrastructure)
본 프로젝트는 벡터 데이터베이스로 Milvus를 사용합니다. 프로젝트 루트 디렉토리에 있는 `docker-compose.yml` 파일을 사용하여 Milvus와 관련 의존성(etcd, MinIO)을 한 번에 실행할 수 있습니다.

터미널에서 다음 명령어를 실행하세요.
```bash
docker-compose up -d
```
이 명령은 백그라운드에서 필요한 모든 서비스를 시작합니다.

### 2.3. 애플리케이션 설정 (Application Configuration)
`src/main/resources/application.properties` 파일을 열고 본인의 Gemini API Key 및 Milvus 환경에 맞게 아래 항목들을 수정해야 합니다.

```properties
# Gemini (Google GenAI Client)
gemini.api.key=YOUR_GEMINI_API_KEY # 여기에 실제 Gemini API 키를 입력하세요.
gemini.model.chat=gemini-1.5-flash-001
gemini.model.embedding=text-embedding-004

# Milvus
milvus.host=localhost
milvus.port=19530
milvus.collection-name=gemini_report_embeddings
```
**중요**: `gemini.api.key`를 자신의 Gemini API 키로 반드시 변경해야 합니다.

## 3. 시스템 아키텍처 (System Architecture)

### 3.1. 전체 시스템 개요 (Overall System Overview)
LangChain4j 프레임워크가 전체 오케스트레이션을 담당합니다. 사용자의 요청은 `AgentController`를 통해 접수되고, `Assistant` AI 서비스로 전달됩니다. `Assistant`는 대화 기록(`ChatMemory`), 도구(`CustomTools`), RAG(`ContentRetriever`)를 활용하여 Gemini 모델과 상호작용하고, 그 결과를 스트림으로 반환합니다.

```
[사용자] --(HTTP Streaming 요청)--> [AgentController]
                                       | (message, conversationId)
                                       V
[Assistant (AI Service)] <------> [Gemini 모델 (StreamingChatLanguageModel)]
       |         |         |
       |         |         +------> [CustomTools (@Tool)]
       |         |                    |
       |         |                    V
       |         |                  [...Service (e.g., CleaningDataService)]
       |         |
       |         +----------------> [ChatMemoryProvider (대화 기록 관리)]
       |
       +--------------------------> [ContentRetriever (RAG)]
                                      |
                                      V
                                    [Milvus (EmbeddingStore)]
```

### 3.2. 주요 컴포넌트 설명 (Description of Key Components)

*   **`Assistant` (`service/Assistant.java`)**
    *   **역할**: LangChain4j의 `@AiService`를 통해 선언된 인터페이스. AI와의 상호작용 명세를 정의합니다. 실제 구현은 LangChain4j가 동적으로 생성합니다.
    *   **특징**: `@SystemMessage`, `@UserMessage`, `@MemoryId` 등의 어노테이션을 사용하여 AI의 행동과 대화 관리 방식을 손쉽게 정의합니다. `TokenStream`을 반환하여 스트리밍 응답을 처리합니다.

*   **`AgentController` (`controller/AgentController.java`)**
    *   **역할**: `/api/agent/chat` 엔드포인트를 통해 사용자의 요청을 받습니다.
    *   **특징**: `SseEmitter` (Server-Sent Events)를 사용하여 `Assistant`로부터 받은 `TokenStream`을 클라이언트에게 실시간으로 스트리밍합니다. 대화 ID를 관리하여 멀티턴 대화를 지원합니다.

*   **`CustomTools` (`tools/CustomTools.java`)**
    *   **역할**: AI가 사용할 수 있는 도구(함수)들을 모아놓은 클래스입니다.
    *   **특징**: `@Component`로 등록된 클래스 내의 메서드에 `@Tool` 어노테이션만 붙이면 LangChain4j가 자동으로 인식하여 Gemini 모델에 함수 목록으로 제공합니다. 기존의 복잡했던 `ToolExecutor`, `ToolRegistry`가 이 클래스 하나로 대체되었습니다.

*   **`GeminiConfig` (`config/GeminiConfig.java`)**
    *   **역할**: LangChain4j의 모든 핵심 컴포넌트를 설정하고 조합하는 중앙 설정 클래스입니다.
    *   **특징**:
        *   `com.google.genai.Client`를 사용하여 `GeminiStreamingChatModel` (채팅 모델) 및 `GeminiEmbeddingModel` (임베딩 모델)을 생성합니다.
        *   `MilvusEmbeddingStore` (벡터 DB 저장소)를 설정합니다.
        *   `ContentRetriever` (RAG 검색기)를 설정합니다.
        *   `ChatMemoryProvider` (대화 기록 관리자)를 설정합니다.
        *   위 모든 요소와 `CustomTools`를 엮어 최종적으로 `Assistant` AI 서비스를 생성하고 Spring Bean으로 등록합니다.

*   **`GeminiEmbeddingModel` (`langchain/GeminiEmbeddingModel.java`)**
    *   **역할**: LangChain4j의 `EmbeddingModel` 인터페이스를 구현하여 `com.google.genai.Client`를 통해 Gemini 임베딩 모델을 사용합니다.
    *   **특징**: `com.google.genai.Client`의 `embedContent` 메서드를 호출하여 텍스트를 임베딩 벡터로 변환합니다.

*   **`GeminiStreamingChatModel` (`langchain/GeminiStreamingChatModel.java`)**
    *   **역할**: LangChain4j의 `StreamingChatLanguageModel` 인터페이스를 구현하여 `com.google.genai.Client`를 통해 Gemini 채팅 모델을 사용합니다.
    *   **특징**: `com.google.genai.Client`의 `generateContentStream` 메서드를 호출하여 스트리밍 응답을 처리하고, 함수 호출을 감지하여 `ToolExecutionRequest`를 생성합니다.

### 3.3. 미해결 컴파일 문제 (Unresolved Compilation Issue)
현재 `GeminiStreamingChatModel.java` 파일에서 `StreamingChatLanguageModel` 인터페이스의 `generate` 메서드를 오버라이드하는 과정에서 `method does not override or implement a method from a supertype` 컴파일 오류가 발생하고 있습니다. 이 문제는 `langchain4j` 라이브러리의 특정 버전(`0.31.0`)과 관련된 미묘한 클래스패스 또는 타입 불일치 문제로 추정되며, 현재까지 해결되지 않았습니다. 이로 인해 프로젝트는 현재 컴파일되지 않습니다.

## 4. API 사용법 및 테스트 (API Usage and Testing)

새로운 스트리밍 API는 `curl`과 같은 도구를 사용하여 테스트할 수 있습니다.

### 4.1. 첫 번째 요청 (새로운 대화 시작)
```bash
curl -N "http://localhost:8080/api/agent/chat?message=우리 회사 이름이 뭐야?"
```
- **결과**:
  - `conversationId` 이벤트와 함께 고유한 대화 ID가 먼저 전송됩니다.
  - 이어서 AI의 답변이 토큰 단위로 스트리밍됩니다.
```
event:conversationId
data:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx

data:우리
data: 회사는
data: '
data:Awesome
data: Cleaning
data: '
data: 입니다
data:.
```

### 4.2. 후속 요청 (대화 이어가기)
첫 번째 요청에서 받은 `conversationId`를 사용하여 대화를 이어갈 수 있습니다.

```bash
curl -N "http://localhost:8080/api/agent/chat?message=그럼 2024년 5월 청소 보고서 보여줘&conversationId=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
```
- **결과**: AI가 이전 대화("우리 회사 이름이 뭐야?")를 기억하고, `getCleaningReport` 도구를 사용하여 요청된 보고서를 생성하여 스트리밍으로 답변합니다.

## 5. 확장 가이드 (Extension Guide)

### 5.1. 새로운 도구(Tool) 추가 방법 (How to Add a New Tool)
새로운 도구를 추가하는 과정은 매우 간단해졌습니다.

1.  **비즈니스 로직 구현**:
    *   필요하다면, 도구가 사용할 비즈니스 로직을 별도의 서비스 클래스(예: `WeatherService`)에 구현합니다.

2.  **`@Tool` 메서드 추가**:
    *   `CustomTools.java` 클래스(또는 `@Component`로 등록된 다른 클래스)에 새로운 public 메서드를 추가합니다.
    *   메서드에 `@Tool` 어노테이션을 붙입니다.
    *   `@Tool` 어노테이션 안에 Gemini 모델이 이해할 수 있도록 **함수의 설명**을 명확하게 작성합니다.
    *   메서드 파라미터에는 `@P` 어노테이션으로 설명을 추가할 수 있습니다.

**예시: 날씨 정보 조회 도구 추가**

```java
// CustomTools.java에 아래 메서드 추가

import dev.langchain4j.agent.tool.P; // @P 어노테이션 import 필요

// ...

/**
 * 지정된 도시의 현재 날씨 정보를 가져옵니다.
 * @param city 날씨를 조회할 도시 이름 (예: "Seoul", "London")
 * @return 해당 도시의 날씨 정보 문자열
 */
@Tool("Get the current weather for a given city")
public String getWeather(@P("The city for which to get the weather") String city) {
    // 1. WeatherService를 주입받았다고 가정
    // return weatherService.getCurrentWeather(city);

    // 2. 또는 여기서 직접 로직 구현
    if ("paris".equalsIgnoreCase(city)) {
        return "파리의 날씨는 맑고 25도입니다.";
    } else {
        return city + "의 날씨 정보는 현재 제공되지 않습니다.";
    }
}
```
이것으로 끝입니다. 애플리케이션을 재시작하면 LangChain4j가 자동으로 `getWeather` 메서드를 도구로 등록하고, 사용자가 "파리 날씨 어때?"라고 질문하면 AI가 이 도구를 호출하여 답변합니다.
