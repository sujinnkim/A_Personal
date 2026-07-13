package com.example.frontapi.domain.meeting;

import com.example.frontapi.config.ThrottleExempt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Port 8080 — 회의 관리 API
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/meetings")
public class MeetingController {

    private final MeetingService meetingService;

    /**
     * POST /meetings — 회의 예약
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createMeeting(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "무제 회의");
        String organizerEmail = body.getOrDefault("organizerEmail", "unknown@example.com");
        String scheduledAtStr = body.get("scheduledAt");
        LocalDateTime scheduledAt = scheduledAtStr != null
            ? LocalDateTime.parse(scheduledAtStr) : LocalDateTime.now().plusHours(1);

        Map<String, Object> result = meetingService.createMeeting(title, organizerEmail, scheduledAt);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * GET /meetings/{id} — 회의 조회 (QueryPool, readOnly)
     * IV-02 검증 대상: join-pool 고갈 시에도 100% 성공
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getMeeting(@PathVariable Long id) {
        Map<String, Object> result = meetingService.getMeeting(id);
        if (result.containsKey("error")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * POST /meetings/{id}/start — 회의 시작
     * AS-02: 서비스가 externalCallExecutor로 비동기 처리 → 컨트롤러는 CompletableFuture 반환
     * AS-06: 핵심 회의 시작 API — 피크 스로틀링에서 면제(@ThrottleExempt)
     */
    @ThrottleExempt
    @PostMapping("/{id}/start")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> startMeeting(@PathVariable Long id) {
        return meetingService.startMeeting(id)
            .thenApply(ResponseEntity::ok)
            .exceptionally(ex -> {
                Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", cause.getMessage()));
            });
    }

    /**
     * POST /meetings/{id}/participants — 참석자 초대
     */
    @PostMapping("/{id}/participants")
    public ResponseEntity<Map<String, Object>> inviteParticipant(
        @PathVariable Long id,
        @RequestBody Map<String, String> body
    ) {
        String email = body.getOrDefault("email", "guest@example.com");
        try {
            Map<String, Object> result = meetingService.inviteParticipant(id, email);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
