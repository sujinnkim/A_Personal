package com.example.frontapi.integration.copilot;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * AS-09: Copilot Admin 외부 호출 Feign 클라이언트 (동기).
 * timeout은 application.yml의 feign.client.config.copilotAdmin 로 설정한다.
 * Circuit Breaker·Fallback은 CopilotAdapter가 담당한다.
 */
@FeignClient(name = "copilotAdmin", url = "${integration.copilot-admin.url}")
public interface CopilotClient {

    @GetMapping("/stub/copilot/enabled")
    Map<String, Object> fetchEnabled(@RequestParam("email") String email);
}
