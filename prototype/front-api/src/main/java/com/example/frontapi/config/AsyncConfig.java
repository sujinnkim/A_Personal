package com.example.frontapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 실행 설정
 * - externalCallExecutor : 외부 서버 연동 전용 (corePoolSize=100, max=500, queue=2000) — AS-02
 * - preWarmExecutor      : 사전 워밍 전용 (corePoolSize=10, max=50, queue=1000)        — AS-05
 * externalCallExecutor에는 컨텍스트 전파용 TaskDecorator(AS-02 설계원칙 4)를 부착한다.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "externalCallExecutor")
    public Executor externalCallExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(100);
        executor.setMaxPoolSize(500);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("ext-call-");
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean(name = "preWarmExecutor")
    public Executor preWarmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("pre-warm-");
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }
}
