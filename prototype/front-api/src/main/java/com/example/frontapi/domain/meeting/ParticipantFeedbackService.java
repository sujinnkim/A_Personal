package com.example.frontapi.domain.meeting;

import com.example.frontapi.config.DataSourceContextHolder;
import com.example.frontapi.config.DataSourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ISSUE-07: 참석자 상태 write 집중 / read 경로 분리 (AS-07 CQRS)
 * - 상태 변경(cPaaS 피드백) = write → Primary(ServicePool)
 * - 상태 조회(참석자 목록)   = read  → Replica(QueryPool, @Transactional(readOnly=true))
 *
 * 실제 시스템에서는 cPaaS → Meeting Manager → server-api 경로로 UPDATE가 유입되나,
 * 프로토타입은 front-api 단일 구성이므로 server-api 역할을 ServicePool write로 시뮬레이션한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParticipantFeedbackService {

    private final ParticipantRepository participantRepository;
    private final MeetingRepository meetingRepository;

    /**
     * cPaaS 입장 성공 피드백 → 참석자 상태 JOINED (write, ServicePool)
     * 사전 초대 없이 입장한 셀프 참석자는 레코드가 없으므로 이 시점에 생성된다.
     */
    @Transactional
    public Map<String, Object> applyEntranceFeedback(Long meetingId, String email) {
        DataSourceContextHolder.set(DataSourceType.SERVICE);
        Participant participant = participantRepository.findByMeetingIdAndEmail(meetingId, email)
            .orElseGet(() -> {
                Meeting meeting = meetingRepository.findById(meetingId)
                    .orElseThrow(() -> new IllegalArgumentException("회의를 찾을 수 없습니다. id=" + meetingId));
                return new Participant(meeting, email);
            });
        participant.updateStatus(ParticipantStatus.JOINED);
        participantRepository.save(participant);
        log.info("[Feedback] 입장 피드백 meetingId={} email={} → JOINED", meetingId, email);

        return Map.of("meetingId", meetingId, "email", email, "status", participant.getStatus());
    }

    /**
     * cPaaS 퇴장·연결 끊김 피드백 → LEFT / DISCONNECTED (write, ServicePool)
     */
    @Transactional
    public Map<String, Object> applyLeaveFeedback(Long meetingId, String email, boolean disconnected) {
        DataSourceContextHolder.set(DataSourceType.SERVICE);
        Participant participant = participantRepository.findByMeetingIdAndEmail(meetingId, email)
            .orElseThrow(() -> new IllegalArgumentException("참석자를 찾을 수 없습니다. email=" + email));
        ParticipantStatus newStatus = disconnected ? ParticipantStatus.DISCONNECTED : ParticipantStatus.LEFT;
        participant.updateStatus(newStatus);
        participantRepository.save(participant);
        log.info("[Feedback] 퇴장 피드백 meetingId={} email={} → {}", meetingId, email, newStatus);

        return Map.of("meetingId", meetingId, "email", email, "status", newStatus);
    }

    /**
     * 참석자 상태 목록 조회 (read → QueryPool/Replica, AS-07)
     * write 집중 구간에도 조회가 Primary lock 경합 없이 Replica에서 처리된다.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getParticipantStatuses(Long meetingId) {
        List<Participant> participants = participantRepository.findByMeetingId(meetingId);
        List<Map<String, Object>> list = participants.stream()
            .map(p -> Map.<String, Object>of(
                "email", p.getEmail(),
                "status", p.getStatus(),
                "updatedAt", p.getUpdatedAt()))
            .toList();

        Map<String, Object> result = new HashMap<>();
        result.put("meetingId", meetingId);
        result.put("participantCount", list.size());
        result.put("participants", list);
        return result;
    }
}
