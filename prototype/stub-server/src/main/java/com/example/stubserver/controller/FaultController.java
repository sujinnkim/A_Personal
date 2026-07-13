package com.example.stubserver.controller;

import com.example.stubserver.model.FaultState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * IV-04: Stub 장애 주입/복구 API
 * POST /stub/{server}/fault   — 장애 활성화
 * DELETE /stub/{server}/fault — 장애 해제 (정상 복구)
 * GET  /stub/status           — 전체 Stub 상태 조회
 */
@Slf4j
@RestController
@RequestMapping("/stub")
public class FaultController {

    // 서버별 장애 상태 (singelton 공유)
    static final FaultState meetingManagerFault = new FaultState("meeting-manager");
    static final FaultState acFault = new FaultState("ac");
    static final FaultState copilotFault = new FaultState("copilot");

    private FaultState resolveFaultState(String server) {
        return switch (server) {
            case "meeting-manager" -> meetingManagerFault;
            case "ac" -> acFault;
            case "copilot" -> copilotFault;
            default -> throw new IllegalArgumentException("알 수 없는 서버: " + server);
        };
    }

    /**
     * POST /stub/{server}/fault
     * Body: { "delayMs": 5000, "errorMode": true }
     */
    @PostMapping("/{server}/fault")
    public ResponseEntity<Map<String, Object>> enableFault(
        @PathVariable String server,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        try {
            FaultState state = resolveFaultState(server);
            int delayMs = body != null ? (Integer) body.getOrDefault("delayMs", 0) : 0;
            boolean errorMode = body == null || (Boolean) body.getOrDefault("errorMode", true);
            state.enableFault(delayMs, errorMode);
            log.warn("[Stub] 장애 활성화 server={} delayMs={} errorMode={}", server, delayMs, errorMode);
            return ResponseEntity.ok(Map.of(
                "server", server,
                "fault", true,
                "delayMs", delayMs,
                "errorMode", errorMode
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /stub/{server}/fault — 장애 해제
     */
    @DeleteMapping("/{server}/fault")
    public ResponseEntity<Map<String, Object>> disableFault(@PathVariable String server) {
        try {
            FaultState state = resolveFaultState(server);
            state.disableFault();
            log.info("[Stub] 장애 해제 server={}", server);
            return ResponseEntity.ok(Map.of("server", server, "fault", false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /stub/status — 전체 Stub 상태
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        for (FaultState state : new FaultState[]{meetingManagerFault, acFault, copilotFault}) {
            status.put(state.getServerName(), Map.of(
                "fault", state.isFaultEnabled(),
                "delayMs", state.getDelayMs(),
                "errorMode", state.isErrorMode()
            ));
        }
        return ResponseEntity.ok(status);
    }
}
