package com.example.frontapi.domain.meeting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    List<Participant> findByMeetingId(Long meetingId);

    // ISSUE-07: cPaaS 피드백 → 참석자 상태 UPDATE 대상 조회
    Optional<Participant> findByMeetingIdAndEmail(Long meetingId, String email);
}
