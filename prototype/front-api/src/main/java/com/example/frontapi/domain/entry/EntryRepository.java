package com.example.frontapi.domain.entry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * joinDataSource 주입 — 입장 전용 (JoinPool 사용)
 */
public interface EntryRepository extends JpaRepository<EntryRecord, Long> {

    Optional<EntryRecord> findByMeetingIdAndEmail(Long meetingId, String email);
}
