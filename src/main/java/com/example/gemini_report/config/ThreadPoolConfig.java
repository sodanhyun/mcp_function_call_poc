package com.example.gemini_report.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 애플리케이션의 비동기 작업을 위한 중앙 스레드 풀을 설정하는 클래스입니다.
 */
@Configuration
public class ThreadPoolConfig {

    /**
     * 비동기 작업을 처리하기 위한 TaskExecutor Bean을 생성합니다.
     * - CorePoolSize: 기본적으로 유지할 스레드 수
     * - MaxPoolSize: 최대 스레드 수
     * - QueueCapacity: MaxPoolSize를 초과하는 요청이 들어왔을 때 대기하는 큐의 크기
     * - RejectedExecutionHandler: 큐까지 가득 찼을 때의 거부 정책 (호출자 스레드가 직접 실행)
     * - ThreadNamePrefix: 스레드 이름을 지정하여 로그 분석 및 디버깅을 용이하게 합니다.
     *
     * @return 설정이 완료된 ThreadPoolTaskExecutor 인스턴스
     */
    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("async-task-");
        executor.initialize();
        return executor;
    }
}
