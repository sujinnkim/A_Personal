package com.example.frontapi.domain.entry;

import com.example.frontapi.config.ThrottleExempt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Port 8081 — 입장 전용 Connector (AS-04)
 * IV-01: 동시 입장 부하 → JoinPool(maximumPoolSize=100) 사용률 측정
 * IV-02: join-pool 포화 시 service-pool 격리 검증
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/meetings")
public class MeetingJoinController {

    private final MeetingJoinService meetingJoinService;

    /**
     * POST /meetings/{id}/join
     * AS-06: 핵심 입장 API — 피크 스로틀링에서 면제(@ThrottleExempt)
     */
    @ThrottleExempt
    @PostMapping("/{id}/join")
    public ResponseEntity<Map<String, Object>> joinMeeting(
        @PathVariable Long id,
        @RequestBody Map<String, String> body
    ) {
        String email = body.getOrDefault("email", "user@example.com");
        Map<String, Object> result = meetingJoinService.joinMeeting(id, email);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /meetings/{id}/conference-token?email={email}
     * AS-02: 서비스가 externalCallExecutor로 비동기 처리 → 컨트롤러는 CompletableFuture 반환(서블릿 스레드 즉시 반환)
     * AS-06: 핵심 발급 API — 피크 스로틀링에서 면제(@ThrottleExempt)
     */
    @ThrottleExempt
    @GetMapping("/{id}/conference-token")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getConferenceToken(
        @PathVariable Long id,
        @RequestParam String email
    ) {
        return meetingJoinService.getConferenceToken(id, email)
            .thenApply(ResponseEntity::ok);
    }
}
