package com.example.frontapi.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * AS-02 설계원칙 4(컨텍스트 전파): @Async 외부 호출 위임 시 호출(서블릿) 스레드의
 * 컨텍스트를 워커 스레드로 전파한다. PoC 범위에서는 로깅 컨텍스트(MDC)를 복사한다.
 *  - 트랜잭션 컨텍스트는 비동기 메서드에 부여된 @Transactional이 워커 스레드에서 새로 시작한다.
 *  - 보안 컨텍스트는 본 프로토타입에 인증/인가가 없어 전파 대상이 아니다.
 */
public class ContextPropagatingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> callerMdc = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (callerMdc != null) {
                    MDC.setContextMap(callerMdc);
                } else {
                    MDC.clear();
                }
                runnable.run();
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };
    }
}
