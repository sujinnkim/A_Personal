package com.example.frontapi.integration.ac;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * AS-09: AC서버 외부 호출 Feign 클라이언트 (동기).
 * timeout은 application.yml의 feign.client.config.acServer 로 설정한다.
 * Circuit Breaker·Fallback은 AcServerAdapter가 담당한다.
 */
@FeignClient(name = "acServer", url = "${integration.ac-server.url}")
public interface AcServerClient {

    @GetMapping("/stub/ac/role")
    Map<String, String> fetchRole(@RequestParam("email") String email);
}
