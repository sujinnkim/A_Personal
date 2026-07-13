package com.example.frontapi.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * AS-06 설계원칙 2·3·4:
 * 피크 구간({@link PeakDetector#isPeakActive()})에서 {@link ThrottleExempt}가 없는 비핵심 API에만
 * Bucket4j 기반 유입 상한(기본 초당 1,000 req)을 적용한다. 상한 초과 시 429 + Retry-After 반환.
 * 핵심 API(입장·conference-token·회의 시작)는 @ThrottleExempt로 면제된다.
 */
@Slf4j
@Component
public class ThrottlingInterceptor implements HandlerInterceptor {

    private final PeakDetector peakDetector;
    private final Bucket nonCoreBucket;
    private final int retryAfterSeconds;

    public ThrottlingInterceptor(
        PeakDetector peakDetector,
        @Value("${throttling.non-core.rps:1000}") long rps,
        @Value("${throttling.non-core.retry-after-seconds:1}") int retryAfterSeconds
    ) {
        this.peakDetector = peakDetector;
        this.retryAfterSeconds = retryAfterSeconds;
        Bandwidth limit = Bandwidth.builder()
            .capacity(rps)
            .refillGreedy(rps, Duration.ofSeconds(1))
            .build();
        this.nonCoreBucket = Bucket.builder().addLimit(limit).build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 피크 구간이 아니면 유입 제어를 하지 않는다
        if (!peakDetector.isPeakActive()) {
            return true;
        }
        // 정적 리소스 등 컨트롤러 핸들러가 아니면 통과
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        // 핵심 API 면제
        if (isExempt(handlerMethod)) {
            return true;
        }
        // 비핵심 API: 상한 소비 시도
        if (nonCoreBucket.tryConsume(1)) {
            return true;
        }
        // 상한 초과 → 429 + Retry-After
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
            "{\"error\":\"THROTTLED\",\"reason\":\"peak non-core rate limit exceeded\",\"retryAfterSeconds\":"
                + retryAfterSeconds + "}");
        log.debug("[Throttling] 비핵심 요청 429 uri={}", request.getRequestURI());
        return false;
    }

    private boolean isExempt(HandlerMethod handlerMethod) {
        return handlerMethod.getMethodAnnotation(ThrottleExempt.class) != null
            || handlerMethod.getBeanType().isAnnotationPresent(ThrottleExempt.class);
    }
}
