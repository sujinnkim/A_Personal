package com.example.frontapi.integration.meetingmanager;

/**
 * 외부 Meeting Manager 서버 연동 포트 (AS-09 Circuit Breaker 대상)
 */
public interface MeetingManagerGateway {

    /**
     * Meeting Manager에 회의 시작 알림
     * @return 성공 시 true, 실패(CB Open 포함) 시 false
     */
    boolean notifyMeetingStarted(Long meetingId);

    /**
     * conference-token 발급 요청
     * @return 토큰 문자열, 실패 시 null
     */
    String issueConferenceToken(Long meetingId, String email);
}
