package com.example.frontapi.integration.copilot;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AS-09: Copilot Admin 연동 어댑터 (Feign + Resilience4j)
 * - 외부 호출: CopilotClient(@FeignClient, 동기)
 * - Circuit Breaker: failureRate=70%, wait=60s
 * - Fallback: null 반환 → AuthService에서 L2 Redis → DB 계층적 폴백 처리
 */
@Slf4j
@Component
public class CopilotAdapter implements CopilotAdminGateway {

    private final CopilotClient client;

    public CopilotAdapter(CopilotClient client) {
        this.client = client;
    }

    @Override
    @CircuitBreaker(name = "copilotAdmin", fallbackMethod = "fetchCopilotEnabledFallback")
    public Boolean fetchCopilotEnabled(String email) {
        Map<String, Object> response = client.fetchEnabled(email);
        if (response != null) {
            Boolean enabled = (Boolean) response.get("enabled");
            log.debug("[CopilotAdmin] 조회 성공 email={} enabled={}", email, enabled);
            return enabled;
        }
        return false;
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    private Boolean fetchCopilotEnabledFallback(String email, Throwable t) {
        log.warn("[CopilotAdmin CB] 조회 실패 email={} cause={} → Redis/DB 폴백", email, t.getMessage());
        return null; // AuthService에서 계층적 폴백 처리
    }
}
