package com.example.frontapi.integration.ac;

/**
 * 외부 AC서버 연동 포트 (AS-09 Circuit Breaker 대상)
 */
public interface AcServerGateway {

    /**
     * AC서버에서 사용자 역할 조회
     * @return 역할 문자열, CB Open 시 null (DB 저장값 폴백)
     */
    String fetchAcRole(String email);
}
