package com.example.frontapi.domain.meeting;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * queryDataSource 주입 — 읽기 전용(Replica). @Transactional(readOnly=true)와 함께 사용.
 * DataSourceRoutingAspect에 의해 QueryPool로 라우팅된다.
 */
public interface MeetingQueryRepository extends JpaRepository<Meeting, Long> {
}
