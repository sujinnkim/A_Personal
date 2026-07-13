package com.example.frontapi.integration.copilot;

/**
 * Copilot Admin 서버 연동 포트 (AS-09 Circuit Breaker 대상)
 */
public interface CopilotAdminGateway {

    /**
     * Copilot 활성화 여부 조회
     * @return true/false, CB Open 시 null (Redis → DB 계층적 폴백)
     */
    Boolean fetchCopilotEnabled(String email);
}
