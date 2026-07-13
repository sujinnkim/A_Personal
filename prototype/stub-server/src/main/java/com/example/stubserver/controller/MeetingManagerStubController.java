package com.example.stubserver.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Meeting Manager 외부 서버 Stub
 * - 정상 모드: 즉시 응답
 * - 장애 모드: 지연 또는 503 오류 (IV-04 검증용)
 */
@Slf4j
@RestController
@RequestMapping("/stub/meeting-manager")
public class MeetingManagerStubController {

    /**
     * 회의 시작 알림
     */
    @PostMapping("/meetings/{meetingId}/start")
    public ResponseEntity<Map<String, Object>> notifyMeetingStart(@PathVariable Long meetingId) throws InterruptedException {
        applyFault("meeting-manager");
        log.debug("[Stub/MeetingManager] 회의 시작 알림 수신 meetingId={}", meetingId);
        return ResponseEntity.ok(Map.of("meetingId", meetingId, "status", "STARTED"));
    }

    /**
     * conference-token 발급
     */
    @GetMapping("/conference-token")
    public ResponseEntity<Map<String, String>> issueToken(
        @RequestParam Long meetingId,
        @RequestParam String email
    ) throws InterruptedException {
        applyFault("meeting-manager");
        String token = "MM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        log.debug("[Stub/MeetingManager] 토큰 발급 meetingId={} email={} token={}", meetingId, email, token);
        return ResponseEntity.ok(Map.of("token", token));
    }

    private void applyFault(String server) throws InterruptedException {
        FaultState state = FaultController.meetingManagerFault;
        if (state.isFaultEnabled()) {
            if (state.getDelayMs() > 0) {
                Thread.sleep(state.getDelayMs());
            }
            if (state.isErrorMode()) {
                throw new StubFaultException("Meeting Manager 장애 주입");
            }
        }
    }

    @ExceptionHandler(StubFaultException.class)
    public ResponseEntity<Map<String, String>> handleFault(StubFaultException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", e.getMessage()));
    }
}
