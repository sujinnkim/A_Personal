package com.example.frontapi.integration.meetingmanager;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AS-09: Meeting Manager 연동 어댑터 (Feign + Resilience4j)
 * - 외부 호출: MeetingManagerClient(@FeignClient, 동기)
 * - Circuit Breaker: failureRate=50%, wait=10s
 * - Fallback: fail-fast 오류 반환 (null / false)
 */
@Slf4j
@Component
public class MeetingManagerAdapter implements MeetingManagerGateway {

    private final MeetingManagerClient client;

    public MeetingManagerAdapter(MeetingManagerClient client) {
        this.client = client;
    }

    @Override
    @CircuitBreaker(name = "meetingManager", fallbackMethod = "notifyFallback")
    public boolean notifyMeetingStarted(Long meetingId) {
        client.notifyMeetingStarted(meetingId, Map.of("meetingId", meetingId));
        log.debug("[MeetingManager] 회의 시작 알림 성공 meetingId={}", meetingId);
        return true;
    }

    @Override
    @CircuitBreaker(name = "meetingManager", fallbackMethod = "tokenFallback")
    public String issueConferenceToken(Long meetingId, String email) {
        Map<String, String> response = client.issueConferenceToken(meetingId, email);
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
