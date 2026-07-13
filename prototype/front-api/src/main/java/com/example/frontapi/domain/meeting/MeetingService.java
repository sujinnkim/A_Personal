package com.example.frontapi.domain.meeting;

import com.example.frontapi.config.DataSourceContextHolder;
import com.example.frontapi.config.DataSourceType;
import com.example.frontapi.integration.meetingmanager.MeetingManagerGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 회의 도메인 서비스
 * - 쓰기 작업: serviceDataSource (ServicePool)
 * - 읽기 작업: queryDataSource (QueryPool, Replica) — DataSourceRoutingAspect가 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingQueryRepository meetingQueryRepository;
    private final ParticipantRepository participantRepository;
    private final MeetingManagerGateway meetingManagerGateway;

    /**
     * POST /meetings — 회의 예약 (serviceDataSource)
     */
    @Transactional
    public Map<String, Object> createMeeting(String title, String organizerEmail, LocalDateTime scheduledAt) {
        DataSourceContextHolder.set(DataSourceType.SERVICE);
        Meeting meeting = new Meeting(title, organizerEmail, scheduledAt);
        meetingRepository.save(meeting);
        log.info("[MeetingService] 회의 예약 완료 id={} title={}", meeting.getId(), title);

        Map<String, Object> result = new HashMap<>();
        result.put("id", meeting.getId());
        result.put("title", meeting.getTitle());
        result.put("organizerEmail", meeting.getOrganizerEmail());
        result.put("scheduledAt", meeting.getScheduledAt());
        result.put("status", meeting.getStatus());
        return result;
    }

    /**
     * GET /meetings/{id} — 회의 조회 (queryDataSource, readOnly)
     * IV-02: join-pool 고갈 시에도 service-pool이 격리되어 성공률 100% 유지
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getMeeting(Long id) {
        // readOnly=true → DataSourceRoutingAspect가 QueryPool로 자동 라우팅
        Optional<Meeting> meetingOpt = meetingQueryRepository.findById(id);
        if (meetingOpt.isEmpty()) {
            return Map.of("error", "회의를 찾을 수 없습니다. id=" + id);
        }
        Meeting meeting = meetingOpt.get();
        List<Participant> participants = participantRepository.findByMeetingId(id);

        Map<String, Object> result = new HashMap<>();
        result.put("id", meeting.getId());
        result.put("title", meeting.getTitle());
        result.put("organizerEmail", meeting.getOrganizerEmail());
        result.put("scheduledAt", meeting.getScheduledAt());
        result.put("startedAt", meeting.getStartedAt());
        result.put("status", meeting.getStatus());
        result.put("participantCount", participants.size());
        return result;
    }

    /**
     * POST /meetings/{id}/start — 회의 시작 (serviceDataSource + Meeting Manager 알림)
     *
     * AS-02: Meeting Manager 회의 시작 알림(외부 호출)을 포함하므로 externalCallExecutor로 비동기 위임한다.
     * 서블릿 스레드는 즉시 반환되고, DB 갱신 + 외부 알림은 워커 스레드에서 수행 후 CompletableFuture로 응답한다.
     */
    @Async("externalCallExecutor")
    @Transactional
    public CompletableFuture<Map<String, Object>> startMeeting(Long id) {
        DataSourceContextHolder.set(DataSourceType.SERVICE);
        Meeting meeting = meetingRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + id));

        meeting.setStatus(MeetingStatus.IN_PROGRESS);
        meeting.setStartedAt(LocalDateTime.now());
        meetingRepository.save(meeting);

        // AS-09: CB 적용 — 실패해도 회의 시작은 성공
        boolean notified = meetingManagerGateway.notifyMeetingStarted(id);
        log.info("[MeetingService] 회의 시작 id={} managerNotified={}", id, notified);

        return CompletableFuture.completedFuture(Map.of(
            "id", meeting.getId(),
            "status", meeting.getStatus(),
            "startedAt", meeting.getStartedAt(),
            "managerNotified", notified
        ));
    }

    /**
     * POST /meetings/{id}/participants — 참석자 초대 (serviceDataSource)
     */
    @Transactional
    public Map<String, Object> inviteParticipant(Long id, String email) {
        DataSourceContextHolder.set(DataSourceType.SERVICE);
        Meeting meeting = meetingRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + id));

        Participant participant = new Participant(meeting, email);
        participantRepository.save(participant);
        log.info("[MeetingService] 참석자 초대 meetingId={} email={}", id, email);

        return Map.of(
            "meetingId", id,
            "email", email,
            "invitedAt", participant.getInvitedAt()
        );
    }
}
