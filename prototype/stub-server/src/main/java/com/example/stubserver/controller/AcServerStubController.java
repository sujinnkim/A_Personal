package com.example.stubserver.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AC서버 Stub
 * - 정상 모드: 이메일 기반 역할 반환
 * - 장애 모드: 지연 또는 503 오류 (IV-04)
 */
@Slf4j
@RestController
@RequestMapping("/stub/ac")
public class AcServerStubController {

    private static final List<String> ROLES = List.of("ADMIN", "EDITOR", "VIEWER", "PRESENTER", "MODERATOR");

    /**
     * GET /stub/ac/role?email={email}
     */
    @GetMapping("/role")
    public ResponseEntity<Map<String, String>> getRole(@RequestParam String email) throws InterruptedException {
        applyFault();
        // email 해시 기반으로 일관된 역할 반환
        int idx = Math.abs(email.hashCode()) % ROLES.size();
        String role = ROLES.get(idx);
        log.debug("[Stub/AC] 역할 조회 email={} role={}", email, role);
        return ResponseEntity.ok(Map.of("email", email, "role", role));
    }

    private void applyFault() throws InterruptedException {
        FaultState state = FaultController.acFault;
        if (state.isFaultEnabled()) {
            if (state.getDelayMs() > 0) {
                Thread.sleep(state.getDelayMs());
            }
            if (state.isErrorMode()) {
                throw new StubFaultException("AC서버 장애 주입");
            }
        }
    }

    @ExceptionHandler(StubFaultException.class)
    public ResponseEntity<Map<String, String>> handleFault(StubFaultException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", e.getMessage()));
    }
}
