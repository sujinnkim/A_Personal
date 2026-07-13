package com.example.frontapi.domain.meeting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Port 8080 — 참석자 상태 피드백/조회 API (ISSUE-07)
 * cPaaS 콜백을 시뮬레이션하며, 상태 변경(write)과 상태 조회(read, Replica)를 분리한다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/meetings")
public class ParticipantFeedbackController {

    private final ParticipantFeedbackService feedbackService;

    /**
     * POST /meetings/{id}/feedback/entrance — cPaaS 입장 성공 피드백 (write)
     */
    @PostMapping("/{id}/feedback/entrance")
    public ResponseEntity<Map<String, Object>> entranceFeedback(
        @PathVariable Long id,
        @RequestBody Map<String, String> body
    ) {
        String email = body.getOrDefault("email", "guest@example.com");
        try {
            return ResponseEntity.ok(feedbackService.applyEntranceFeedback(id, email));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /meetings/{id}/feedback/leave — cPaaS 퇴장·연결 끊김 피드백 (write)
     * body: { "email": "...", "disconnected": "true|false" }
     */
    @PostMapping("/{id}/feedback/leave")
    public ResponseEntity<Map<String, Object>> leaveFeedback(
        @PathVariable Long id,
        @RequestBody Map<String, String> body
    ) {
        String email = body.getOrDefault("email", "guest@example.com");
        boolean disconnected = Boolean.parseBoolean(body.getOrDefault("disconnected", "false"));
        try {
            return ResponseEntity.ok(feedbackService.applyLeaveFeedback(id, email, disconnected));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /meetings/{id}/participants/status — 참석자 상태 조회 (read → Replica, AS-07)
     */
    @GetMapping("/{id}/participants/status")
    public ResponseEntity<Map<String, Object>> participantStatuses(@PathVariable Long id) {
        return ResponseEntity.ok(feedbackService.getParticipantStatuses(id));
    }
}
