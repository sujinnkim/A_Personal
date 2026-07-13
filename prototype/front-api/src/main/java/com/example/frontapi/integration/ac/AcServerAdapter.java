package com.example.frontapi.integration.ac;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * AS-09: AC서버 연동 어댑터
 * - Circuit Breaker: failureRate=60%, wait=30s
 * - Fallback: DB 저장 권한값 반환 (null 반환 → AuthService에서 DB 조회로 폴백)
 */
@Slf4j
@Component
public class AcServerAdapter implements AcServerGateway {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AcServerAdapter(
        @Qualifier("acServerRestTemplate") RestTemplate restTemplate,
        @Value("${integration.ac-server.url}") String baseUrl
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    @CircuitBreaker(name = "acServer", fallbackMethod = "fetchAcRoleFallback")
    public String fetchAcRole(String email) {
        String url = baseUrl + "/stub/ac/role?email=" + email;
        @SuppressWarnings("unchecked")
        Map<String, String> response = restTemplate.getForObject(url, Map.class);
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
