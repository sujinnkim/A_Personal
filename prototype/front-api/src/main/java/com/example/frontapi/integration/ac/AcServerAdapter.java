package com.example.frontapi.integration.ac;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AS-09: AC서버 연동 어댑터 (Feign + Resilience4j)
 * - 외부 호출: AcServerClient(@FeignClient, 동기)
 * - Circuit Breaker: failureRate=60%, wait=30s
 * - Fallback: null 반환 → AuthService에서 DB 저장값으로 폴백
 */
@Slf4j
@Component
public class AcServerAdapter implements AcServerGateway {

    private final AcServerClient client;

    public AcServerAdapter(AcServerClient client) {
        this.client = client;
    }

    @Override
    @CircuitBreaker(name = "acServer", fallbackMethod = "fetchAcRoleFallback")
    public String fetchAcRole(String email) {
        Map<String, String> response = client.fetchRole(email);
        if (response != null) {
            String role = response.get("role");
            log.debug("[AcServer] 역할 조회 성공 email={} role={}", email, role);
            return role;
        }
        return "VIEWER";
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    private String fetchAcRoleFallback(String email, Throwable t) {
        log.warn("[AcServer CB] 역할 조회 실패 email={} cause={} → DB 저장값 사용", email, t.getMessage());
        return null; // AuthService에서 DB 저장값으로 폴백
    }
}
