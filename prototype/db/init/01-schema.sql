-- ============================================================
-- 01-schema.sql
-- 미팅 포털 프로토타입 테이블 생성
-- ============================================================

CREATE DATABASE IF NOT EXISTS meetingdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE meetingdb;

-- 회의 테이블
CREATE TABLE IF NOT EXISTS meetings (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    organizer_email VARCHAR(100) NOT NULL,
    scheduled_at  DATETIME,
    started_at    DATETIME,
    status        VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_meetings_organizer (organizer_email),
    INDEX idx_meetings_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 참석자 테이블 (ISSUE-07: 참석자 상태 write 집중 대상)
--   status: INVITED(사전 초대) → JOINED(입장 성공, cPaaS 피드백) → LEFT/DISCONNECTED(퇴장·연결 끊김)
--   상태 UPDATE(write)는 Primary(ServicePool), 참석자 상태 조회(read)는 Replica(QueryPool, AS-07)
CREATE TABLE IF NOT EXISTS participants (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id  BIGINT NOT NULL,
    email       VARCHAR(100) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    invited_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_participants_meeting (meeting_id),
    INDEX idx_participants_email (email),
    INDEX idx_participants_status (meeting_id, status),
    CONSTRAINT fk_participants_meeting FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 입장 기록 테이블 (JoinPool 전용)
CREATE TABLE IF NOT EXISTS entry_records (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id        BIGINT NOT NULL,
    email             VARCHAR(100) NOT NULL,
    joined_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    conference_token  VARCHAR(500),
    UNIQUE KEY uk_entry (meeting_id, email),
    INDEX idx_entry_meeting (meeting_id),
    INDEX idx_entry_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 사용자 권한 테이블 (GeneralPool 전용)
CREATE TABLE IF NOT EXISTS member_permissions (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    email            VARCHAR(100) NOT NULL UNIQUE,
    permission_level INT NOT NULL DEFAULT 1,
    ac_role          VARCHAR(50) DEFAULT 'VIEWER',
    copilot_enabled  BOOLEAN DEFAULT FALSE,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_member_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
