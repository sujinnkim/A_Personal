package com.example.frontapi.domain.meeting;

/**
 * 참석자 상태 (ISSUE-07)
 * - INVITED: 사전 초대됨 (아직 미입장)
 * - JOINED: 입장 성공 (cPaaS 입장 피드백 수신)
 * - LEFT: 정상 퇴장 (cPaaS 퇴장 피드백)
 * - DISCONNECTED: 연결 끊김 (cPaaS 연결 끊김 피드백)
 */
public enum ParticipantStatus {
    INVITED,
    JOINED,
    LEFT,
    DISCONNECTED
}
