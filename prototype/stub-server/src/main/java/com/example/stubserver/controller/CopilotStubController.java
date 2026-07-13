package com.example.stubserver.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Copilot Admin Stub
 * - 정상 모드: 50% 확률로 enabled 반환 (이메일 해시 기반)
 * - 장애 모드: 지연 또는 503 오류 (IV-04)
 */
@Slf4j
@RestController
@RequestMapping("/stub/copilot")
public class CopilotStubController {

    /**
     * GET /stub/copilot/enabled?email={email}
     */
    @GetMapping("/enabled")
    public ResponseEntity<Map<String, Object>> getCopilotEnabled(@RequestParam String email) throws InterruptedException {
        applyFault();
        boolean enabled = Math.abs(email.hashCode()) % 2 == 0;
        log.debug("[Stub/Copilot] 조회 email={} enabled={}", email, enabled);
        return ResponseEntity.ok(Map.of("email", email, "enabled", enabled));
    }

    private void applyFault() throws InterruptedException {
        FaultState state = FaultController.copilotFault;
        if (state.isFaultEnabled()) {
            if (state.getDelayMs() > 0) {
                Thread.sleep(state.getDelayMs());
            }
            if (state.isErrorMode()) {
                throw new StubFaultException("Copilot Admin 장애 주입");
            }
        }
    }

    @ExceptionHandler(StubFaultException.class)
    public ResponseEntity<Map<String, String>> handleFault(StubFaultException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", e.getMessage()));
    }
}
