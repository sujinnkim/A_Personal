package com.example.frontapi.domain.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Port 8080 — 사용자 권한 갱신 API
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/members")
public class AuthController {

    private final AuthService authService;

    /**
     * GET /members/{email}?type=detail
     * IV-03: 피크 시 권한 갱신 응답시간 1초 이내 — 캐시 hit율 90% 이상
     */
    @GetMapping("/{email}")
    public ResponseEntity<Map<String, Object>> getMemberDetail(
        @PathVariable String email,
        @RequestParam(value = "type", defaultValue = "basic") String type
    ) {
        if ("detail".equals(type)) {
            Map<String, Object> result = authService.getMemberDetail(email);
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.ok(Map.of("email", email, "type", type));
    }
}
