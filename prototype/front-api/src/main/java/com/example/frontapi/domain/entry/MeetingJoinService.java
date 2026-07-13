package com.example.frontapi.domain.entry;

import com.example.frontapi.config.DataSourceContextHolder;
import com.example.frontapi.config.DataSourceType;
import com.example.frontapi.integration.meetingmanager.MeetingManagerGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AS-04/AS-08: 입장 전용 서비스 — JoinPool 사용
 * IV-01: 동시 입장 집중 구간에서 DB 커넥션 풀 사용률 80% 이하
 * IV-02: join-pool 고갈 시 service-pool 격리 검증용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingJoinService {

    private final EntryRepository entryRepository;
    private final MeetingManagerGateway meetingManagerGateway;

    /**
     * POST /meetings/{id}/join — 회의 입장 (joinDataSource)
     */
    @Transactional
    public Map<String, Object> joinMeeting(Long meetingId, String email) {
        DataSourceContextHolder.set(DataSourceType.JOIN);
        log.debug("[JoinService] 입장 요청 meetingId={} email={}", meetingId, email);

        // 중복 입장 체크
        boolean alreadyJoined = entryRepository.findByMeetingIdAndEmail(meetingId, email).isPresent();
        if (alreadyJoined) {
            return Map.of(
                "meetingId", meetingId,
                "email", email,
                "status", "ALREADY_JOINED"
            );
        }

        EntryRecord record = new EntryRecord(meetingId, email);
        entryRepository.save(record);

        log.info("[JoinService] 입장 완료 meetingId={} email={}", meetingId, email);
        return Map.of(
            "meetingId", meetingId,
            "email", email,
            "status", "JOINED",
            "entryId", record.getId()
        );
    }

    /**
     * GET /meetings/{id}/conference-token — conference-token 발급 (joinDataSource + MeetingManager CB)
     *
     * AS-02: Meeting Manager 조회가 포함되는 병목 구간이므로 externalCallExecutor로 비동기 위임한다.
     * 서블릿 스레드는 즉시 반환되고, 외부 호출 + 토큰 처리는 워커 스레드에서 수행 후
     * CompletableFuture 완료 시 응답한다. (설계원칙 1·3)
     */
    @Async("externalCallExecutor")
    @Transactional
    public CompletableFuture<Map<String, Object>> getConferenceToken(Long meetingId, String email) {
        DataSourceContextHolder.set(DataSourceType.JOIN);
        log.debug("[JoinService] 토큰 발급 요청 meetingId={} email={}", meetingId, email);

        // 기존 토큰 조회
        EntryRecord record = entryRepository.findByMeetingIdAndEmail(meetingId, email)
            .orElseGet(() -> {
                EntryRecord newRecord = new EntryRecord(meetingId, email);
                return entryRepository.save(newRecord);
            });

        // 이미 토큰 있으면 재사용
        if (record.getConferenceToken() != null) {
            return CompletableFuture.completedFuture(Map.of(
                "meetingId", meetingId,
                "email", email,
                "token", record.getConferenceToken(),
                "source", "CACHED"
            ));
        }

        // AS-09: Meeting Manager에서 토큰 발급 (CB 적용)
        String token = meetingManagerGateway.issueConferenceToken(meetingId, email);
        if (token == null) {
            // CB fallback: 임시 토큰 생성
            token = "LOCAL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            log.warn("[JoinService] Meeting Manager 장애 → 임시 토큰 발급 meetingId={}", meetingId);
        }

        record.setConferenceToken(token);
        entryRepository.save(record);

        return CompletableFuture.completedFuture(Map.of(
            "meetingId", meetingId,
            "email", email,
            "token", token,
            "source", "ISSUED"
        ));
    }
}
