-- ============================================================
-- 02-testdata.sql
-- 테스트용 초기 데이터 삽입
-- ============================================================

USE meetingdb;

-- 회의 테스트 데이터 (100건)
INSERT INTO meetings (title, organizer_email, scheduled_at, status) VALUES
('Q4 킥오프 회의', 'organizer1@example.com', DATE_ADD(NOW(), INTERVAL 1 HOUR), 'SCHEDULED'),
('아키텍처 리뷰', 'organizer2@example.com', DATE_ADD(NOW(), INTERVAL 2 HOUR), 'SCHEDULED'),
('성능 테스트 결과 공유', 'organizer3@example.com', DATE_ADD(NOW(), INTERVAL 3 HOUR), 'SCHEDULED'),
('DB 커넥션 최적화 논의', 'organizer1@example.com', NOW(), 'IN_PROGRESS'),
('Circuit Breaker 검증', 'organizer2@example.com', DATE_ADD(NOW(), INTERVAL 4 HOUR), 'SCHEDULED'),
('캐시 전략 검토', 'organizer3@example.com', DATE_ADD(NOW(), INTERVAL 5 HOUR), 'SCHEDULED'),
('PoC 결과 발표', 'organizer1@example.com', DATE_ADD(NOW(), INTERVAL 6 HOUR), 'SCHEDULED'),
('부하 테스트 계획', 'organizer2@example.com', DATE_ADD(NOW(), INTERVAL 7 HOUR), 'SCHEDULED'),
('인프라 구성 리뷰', 'organizer3@example.com', DATE_ADD(NOW(), INTERVAL 8 HOUR), 'SCHEDULED'),
('HikariCP 튜닝 논의', 'organizer1@example.com', DATE_ADD(NOW(), INTERVAL 9 HOUR), 'SCHEDULED');

-- 더 많은 회의 데이터 (IV-01 부하 테스트용)
INSERT INTO meetings (title, organizer_email, scheduled_at, status)
SELECT
    CONCAT('테스트 회의 ', n),
    CONCAT('user', n % 100, '@example.com'),
    DATE_ADD(NOW(), INTERVAL (n % 24) HOUR),
    CASE WHEN n % 10 = 0 THEN 'IN_PROGRESS' ELSE 'SCHEDULED' END
FROM (
    SELECT a.N + b.N * 10 + 1 AS n
    FROM (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
          UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
    CROSS JOIN (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
) nums
WHERE n <= 90;

-- 사용자 권한 테스트 데이터 (1000명, Pre-warming용)
INSERT INTO member_permissions (email, permission_level, ac_role, copilot_enabled)
SELECT
    CONCAT('user', n, '@example.com'),
    CASE WHEN n % 5 = 0 THEN 3 WHEN n % 3 = 0 THEN 2 ELSE 1 END,
    CASE
        WHEN n % 5 = 0 THEN 'ADMIN'
        WHEN n % 4 = 0 THEN 'MODERATOR'
        WHEN n % 3 = 0 THEN 'PRESENTER'
        WHEN n % 2 = 0 THEN 'EDITOR'
        ELSE 'VIEWER'
    END,
    n % 2 = 0
FROM (
    SELECT a.N + b.N * 10 + c.N * 100 + 1 AS n
    FROM
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
         UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
    CROSS JOIN
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
         UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
    CROSS JOIN
        (SELECT 0 AS N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
         UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c
) nums
WHERE n <= 1000;

-- 특수 테스트 사용자 (organizer용)
INSERT INTO member_permissions (email, permission_level, ac_role, copilot_enabled)
VALUES
    ('organizer1@example.com', 3, 'ADMIN', TRUE),
    ('organizer2@example.com', 3, 'ADMIN', TRUE),
    ('organizer3@example.com', 3, 'ADMIN', TRUE)
ON DUPLICATE KEY UPDATE permission_level = VALUES(permission_level);

-- 참석자 테스트 데이터 (회의 1~10에 참석자 추가)
--   회의당 5명 중 3명 JOINED(입장 완료), 2명 INVITED(미입장) — 상태 조회(read) 검증용
INSERT INTO participants (meeting_id, email, status)
SELECT m.id, CONCAT('participant', p.n, '@example.com'),
       CASE WHEN p.n <= 3 THEN 'JOINED' ELSE 'INVITED' END
FROM meetings m
CROSS JOIN (
    SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
) p
WHERE m.id <= 10;
