package com.example.gemini_report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GeminiReportApplication은 이 Spring Boot 애플리케이션의 메인 클래스입니다.
 * `@SpringBootApplication` 어노테이션은 다음 세 가지 어노테이션을 포함합니다:
 * 1. `@Configuration`: 애플리케이션 컨텍스트의 빈 정의 소스로 클래스를 태그합니다.
 * 2. `@EnableAutoConfiguration`: Spring Boot의 자동 구성을 활성화하여 클래스패스 설정 및 기타 빈을 기반으로 Spring이 자동으로 빈을 추가하도록 합니다.
 * 3. `@ComponentScan`: Spring이 `com.example.gemini_report` 패키지 및 그 하위 패키지에서 컴포넌트, 서비스, 리포지토리 등을 스캔하여 빈으로 등록하도록 합니다.
 *
 * 이 클래스는 애플리케이션을 시작하는 `main` 메서드를 포함합니다.
 */
@SpringBootApplication
public class GeminiReportApplication {

	public static void main(String[] args) {
		// SpringApplication.run() 메서드를 호출하여 Spring 애플리케이션을 부트스트랩하고 실행합니다.
		// 이 메서드는 Spring 컨테이너를 초기화하고, 모든 빈을 로드하며, 내장 웹 서버(예: Tomcat)를 시작합니다.
		SpringApplication.run(GeminiReportApplication.class, args);
	}

}

