package com.example.frontapi.domain.meeting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * serviceDataSource 주입 — 회의 생성/시작/참석자 초대 등 쓰기 작업
 */
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByOrganizerEmail(String organizerEmail);
}
