/**
 * IV-01: 동시 입장 집중 구간 검증
 * 검증 목표:
 *   - DB 커넥션 풀(JoinPool) 사용률 80% 이하
 *   - 평균 응답시간 1초 이내
 * 관련 AS: AS-02 (비동기), AS-04 (입장 전용 Connector), AS-08 (커넥션 풀 분리)
 *
 * 실행:
 *   k6 run --env BASE_URL=http://localhost k6/iv01-join-load.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost';

// 커스텀 메트릭
const joinSuccessRate = new Rate('join_success_rate');
const joinDuration = new Trend('join_duration_ms', true);
const joinErrors = new Counter('join_errors');

/**
 * 부하 시나리오:
 * - 5분 안에 최대 500 VU까지 램프업 (8만 명 중 동시 입장 시뮬레이션)
 * - 3분 유지 후 1분 내 감소
 */
export const options = {
    scenarios: {
        join_surge: {
            executor: 'ramping-vus',
            startVUs: 10,
            stages: [
                { duration: '1m', target: 100 },  // 워밍업
                { duration: '2m', target: 500 },  // 피크 — 동시 입장 집중
                { duration: '3m', target: 500 },  // 유지
                { duration: '1m', target: 50 },   // 감소
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        // IV-01 합격 기준
        'join_success_rate': ['rate>0.95'],               // 성공률 95% 이상
        'join_duration_ms': ['p(50)<1000', 'p(95)<3000'], // 평균 1초 이내, p95 3초
        'http_req_failed': ['rate<0.05'],
    },
};

const MEETING_IDS = Array.from({ length: 100 }, (_, i) => i + 1);
const EMAILS = Array.from({ length: 1000 }, (_, i) => `user${i + 1}@example.com`);

export default function () {
    const meetingId = MEETING_IDS[Math.floor(Math.random() * MEETING_IDS.length)];
    const email = EMAILS[Math.floor(Math.random() * EMAILS.length)];

    const payload = JSON.stringify({ email });
    const params = {
        headers: { 'Content-Type': 'application/json' },
        timeout: '10s',
    };

    const startTime = Date.now();

    // AS-04: 입장 요청 → Nginx가 8081로 라우팅
    const joinRes = http.post(
        `${BASE_URL}/meetings/${meetingId}/join`,
        payload,
        params
    );

    const elapsed = Date.now() - startTime;
    joinDuration.add(elapsed);

    const success = check(joinRes, {
        'join status 200': (r) => r.status === 200,
        'join response time < 1s': (r) => r.timings.duration < 1000,
        'join has entryId or ALREADY_JOINED': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.status === 'JOINED' || body.status === 'ALREADY_JOINED';
            } catch {
                return false;
            }
        },
    });

    joinSuccessRate.add(success);
    if (!success) {
        joinErrors.add(1);
    }

    sleep(Math.random() * 0.5); // 0~0.5초 랜덤 간격
}

export function handleSummary(data) {
    return {
        'k6/results/iv01-summary.json': JSON.stringify(data, null, 2),
        stdout: makeTextSummary(data),
    };
}

function makeTextSummary(data) {
    const metrics = data.metrics;
    const lines = [
        '═══════════════════════════════════════════════════',
        ' IV-01: 동시 입장 집중 구간 검증 결과',
        '═══════════════════════════════════════════════════',
        `  성공률           : ${(metrics['join_success_rate']?.values?.rate * 100 || 0).toFixed(2)}%  (목표: ≥95%)`,
        `  평균 응답시간     : ${(metrics['join_duration_ms']?.values?.avg || 0).toFixed(0)}ms  (목표: <1000ms)`,
        `  p50 응답시간      : ${(metrics['join_duration_ms']?.values?.['p(50)'] || 0).toFixed(0)}ms`,
        `  p95 응답시간      : ${(metrics['join_duration_ms']?.values?.['p(95)'] || 0).toFixed(0)}ms  (목표: <3000ms)`,
        `  총 요청 수        : ${metrics['http_reqs']?.values?.count || 0}`,
        `  요청 실패 수      : ${metrics['join_errors']?.values?.count || 0}`,
        '─────────────────────────────────────────────────',
        ' * HikariCP JoinPool 활성 커넥션은 Actuator에서 확인:',
        '   GET http://localhost:8080/actuator/metrics/hikaricp.connections.active?tag=pool:JoinPool',
        '═══════════════════════════════════════════════════',
    ];
    return lines.join('\n');
}
