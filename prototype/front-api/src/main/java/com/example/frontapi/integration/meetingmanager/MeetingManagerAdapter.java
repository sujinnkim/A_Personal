package com.example.frontapi.integration.meetingmanager;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * AS-09: Meeting Manager 연동 어댑터
 * - Circuit Breaker: failureRate=50%, wait=10s
 * - Fallback: fail-fast 오류 반환 (null / false)
 */
@Slf4j
@Component
public class MeetingManagerAdapter implements MeetingManagerGateway {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MeetingManagerAdapter(
        @Qualifier("meetingManagerRestTemplate") RestTemplate restTemplate,
        @Value("${integration.meeting-manager.url}") String baseUrl
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    @CircuitBreaker(name = "meetingManager", fallbackMethod = "notifyFallback")
    public boolean notifyMeetingStarted(Long meetingId) {
        String url = baseUrl + "/stub/meeting-manager/meetings/" + meetingId + "/start";
        restTemplate.postForObject(url, Map.of("meetingId", meetingId), Void.class);
        log.debug("[MeetingManager] 회의 시작 알림 성공 meetingId={}", meetingId);
        return true;
    }

    @Override
    @CircuitBreaker(name = "meetingManager", fallbackMethod = "tokenFallback")
    public String issueConferenceToken(Long meetingId, String email) {
        String url = baseUrl + "/stub/meeting-manager/conference-token?meetingId=" + meetingId + "&email=" + email;
        @SuppressWarnings("unchecked")
        Map<String, String> response = restTemplate.getForObject(url, Map.class);
        if (response != null) {
            return response.get("token");
        }
        return null;
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    private boolean notifyFallback(Long meetingId, Throwable t) {
        log.warn("[MeetingManager CB] 회의 시작 알림 실패 meetingId={} cause={}", meetingId, t.getMessage());
        return false;
    }

    private String tokenFallback(Long meetingId, String email, Throwable t) {
        log.warn("[MeetingManager CB] 토큰 발급 실패 meetingId={} email={} cause={}", meetingId, email, t.getMessage());
        return null;
    }
}
