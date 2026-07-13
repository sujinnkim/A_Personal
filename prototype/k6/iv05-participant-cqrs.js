/**
 * IV-05: 참석자 상태 write 집중 시 read 조회 분리 검증 (ISSUE-07 / AS-07)
 * 검증 목표:
 *   - cPaaS 피드백 UPDATE(write)를 Primary(ServicePool)에 집중시킴
 *   - 동시에 참석자 상태 조회(read)를 Replica(QueryPool)로 라우팅하여
 *     write 집중 구간에도 조회 성공률·응답시간이 유지되는지 확인
 * 관련 AS: AS-07 (조회·입장 DB 경로 분리), AS-08 (커넥션 풀 분리)
 *
 * 실행:
 *   k6 run --env BASE_URL=http://localhost k6/iv05-participant-cqrs.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost';

const feedbackSuccessRate = new Rate('feedback_write_success_rate');
const statusReadSuccessRate = new Rate('status_read_success_rate');
const statusReadDuration = new Trend('status_read_duration_ms', true);
const statusReadErrors = new Counter('status_read_errors');

export const options = {
    scenarios: {
        // 시나리오 1: cPaaS 피드백 write 집중 (Primary / ServicePool)
        feedback_write_flood: {
            executor: 'constant-vus',
            vus: 80,
            duration: '3m',
            exec: 'feedbackWriteScenario',
        },
        // 시나리오 2: 참석자 상태 조회 read (Replica / QueryPool) — 분리 검증
        status_read_isolation: {
            executor: 'constant-vus',
            vus: 50,
            duration: '3m',
            exec: 'statusReadScenario',
            startTime: '30s', // write 집중 후 30초 뒤 조회 시작
        },
    },
    thresholds: {
        // IV-05 합격 기준: write 집중 중에도 조회 성공률·응답 유지
        'status_read_success_rate': ['rate>0.999'],       // 99.9% 이상
        'status_read_duration_ms': ['p(95)<500'],         // Replica 조회 응답 유지
        'http_req_failed{scenario:status_read_isolation}': ['rate<0.001'],
    },
};

const MEETING_IDS = Array.from({ length: 10 }, (_, i) => i + 1);
// 회의당 사전 초대 참석자 participant1~5@example.com + 셀프 참석자
const PARTICIPANTS = Array.from({ length: 5 }, (_, i) => `participant${i + 1}@example.com`);

export function feedbackWriteScenario() {
    const meetingId = MEETING_IDS[Math.floor(Math.random() * MEETING_IDS.length)];
    // 입장/퇴장 피드백을 번갈아 유발 → 참석자 상태 UPDATE(write) 집중
    const roll = Math.random();

    let res;
    if (roll < 0.6) {
        // 입장 성공 피드백 (사전 초대 참석자 또는 셀프 참석자)
        const email = roll < 0.4
            ? PARTICIPANTS[Math.floor(Math.random() * PARTICIPANTS.length)]
            : `self-user${__VU}-${__ITER}@example.com`;
        res = http.post(
            `${BASE_URL}/meetings/${meetingId}/feedback/entrance`,
            JSON.stringify({ email }),
            { headers: { 'Content-Type': 'application/json' }, timeout: '10s' }
        );
    } else {
        // 퇴장·연결 끊김 피드백
        const email = PARTICIPANTS[Math.floor(Math.random() * PARTICIPANTS.length)];
        const disconnected = Math.random() < 0.5;
        res = http.post(
            `${BASE_URL}/meetings/${meetingId}/feedback/leave`,
            JSON.stringify({ email, disconnected: String(disconnected) }),
            { headers: { 'Content-Type': 'application/json' }, timeout: '10s' }
        );
    }

    feedbackSuccessRate.add(res.status === 200);
    sleep(0.05 + Math.random() * 0.1); // 짧은 간격으로 write 집중 유지
}

export function statusReadScenario() {
    const meetingId = MEETING_IDS[Math.floor(Math.random() * MEETING_IDS.length)];
    const start = Date.now();

    // 참석자 상태 조회 — Replica(QueryPool) 라우팅, write 집중과 독립
    const res = http.get(
        `${BASE_URL}/meetings/${meetingId}/participants/status`,
        { timeout: '5s' }
    );

    const elapsed = Date.now() - start;
    statusReadDuration.add(elapsed);

    const success = check(res, {
        'status read 200': (r) => r.status === 200,
        'status read < 500ms': (r) => r.timings.duration < 500,
        'has participants': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.participantCount !== undefined && Array.isArray(body.participants);
            } catch {
                return false;
            }
        },
    });

    statusReadSuccessRate.add(success);
    if (!success) {
        statusReadErrors.add(1);
        console.error(`[IV-05] 상태 조회 실패 meetingId=${meetingId} status=${res.status} body=${res.body}`);
    }

    sleep(0.2 + Math.random() * 0.3);
}

export function handleSummary(data) {
    return {
        'k6/results/iv05-summary.json': JSON.stringify(data, null, 2),
        stdout: makeTextSummary(data),
    };
}

function makeTextSummary(data) {
    const metrics = data.metrics;
    const lines = [
        '═══════════════════════════════════════════════════',
        ' IV-05: 참석자 상태 write 집중 시 read 조회 분리 (ISSUE-07/AS-07)',
        '═══════════════════════════════════════════════════',
        `  피드백 write 성공률 : ${(metrics['feedback_write_success_rate']?.values?.rate * 100 || 0).toFixed(2)}%`,
        `  상태 read  성공률   : ${(metrics['status_read_success_rate']?.values?.rate * 100 || 0).toFixed(2)}%  (목표: ≥99.9%)`,
        `  상태 read  평균     : ${(metrics['status_read_duration_ms']?.values?.avg || 0).toFixed(0)}ms`,
        `  상태 read  p95      : ${(metrics['status_read_duration_ms']?.values?.['p(95)'] || 0).toFixed(0)}ms  (목표: <500ms)`,
        `  상태 read  실패 건수: ${metrics['status_read_errors']?.values?.count || 0}  (목표: 0)`,
        '─────────────────────────────────────────────────',
        ' * feedback write VU=80(Primary/ServicePool 집중) 구간에서',
        '   GET /participants/status(Replica/QueryPool)가 독립 유지되어야 함.',
        '═══════════════════════════════════════════════════',
    ];
    return lines.join('\n');
}
