package com.example.frontapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * AS-06: {@link ThrottlingInterceptor}를 전체 요청 경로에 등록한다.
 * 실제 제어는 인터셉터 내부 판정으로 피크 구간 + 비핵심(@ThrottleExempt 미부여) 요청에만 한정된다.
 */
@Configuration
public class ThrottlingConfig implements WebMvcConfigurer {

    private final ThrottlingInterceptor throttlingInterceptor;

    public ThrottlingConfig(ThrottlingInterceptor throttlingInterceptor) {
        this.throttlingInterceptor = throttlingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(throttlingInterceptor).addPathPatterns("/**");
    }
}
