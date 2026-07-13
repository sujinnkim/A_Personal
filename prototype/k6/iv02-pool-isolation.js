/**
 * IV-02: join-pool 고갈 시 service-pool 격리 검증
 * 검증 목표:
 *   - join-pool 의도적 포화 → JoinPool 커넥션 100개 모두 점유
 *   - 동시에 GET /meetings/{id} (QueryPool/ServicePool) 호출 성공률 100%
 * 관련 AS: AS-08 (커넥션 풀 분리)
 *
 * 실행:
 *   k6 run --env BASE_URL=http://localhost k6/iv02-pool-isolation.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost';

const joinSuccessRate = new Rate('join_success_rate');
const querySuccessRate = new Rate('query_success_rate');
const queryDuration = new Trend('query_duration_ms', true);
const queryErrors = new Counter('query_errors');

export const options = {
    scenarios: {
        // 시나리오 1: join 포화 (JoinPool 점유)
        join_saturation: {
            executor: 'constant-vus',
            vus: 120,          // JoinPool(100) 초과 → 의도적 대기 상태 유발
            duration: '3m',
            exec: 'joinScenario',
        },
        // 시나리오 2: 회의 조회 (QueryPool → 격리 검증)
        query_isolation: {
            executor: 'constant-vus',
            vus: 50,
            duration: '3m',
            exec: 'queryScenario',
            startTime: '30s', // join 포화 후 30초 뒤 시작
        },
    },
    thresholds: {
        // IV-02 합격 기준: 조회 API 성공률 100%
        'query_success_rate': ['rate>0.999'],             // 99.9% 이상
        'query_duration_ms': ['p(95)<500'],               // 조회 응답 빠를 것
        'http_req_failed{scenario:query_isolation}': ['rate<0.001'],
    },
};

const MEETING_IDS = Array.from({ length: 10 }, (_, i) => i + 1);

export function joinScenario() {
    const meetingId = MEETING_IDS[Math.floor(Math.random() * MEETING_IDS.length)];
    const email = `flood-user${__VU}-${__ITER}@example.com`;

    const res = http.post(
        `${BASE_URL}/meetings/${meetingId}/join`,
        JSON.stringify({ email }),
        { headers: { 'Content-Type': 'application/json' }, timeout: '30s' }
    );

    joinSuccessRate.add(res.status === 200);
    sleep(0.1 + Math.random() * 0.1); // 짧은 간격으로 커넥션 점유 유지
}

export function queryScenario() {
    const meetingId = MEETING_IDS[Math.floor(Math.random() * MEETING_IDS.length)];
    const start = Date.now();

    // QueryPool 사용 — JoinPool과 독립
    const res = http.get(
        `${BASE_URL}/meetings/${meetingId}`,
        { timeout: '5s' }
    );

    const elapsed = Date.now() - start;
    queryDuration.add(elapsed);

    const success = check(res, {
        'query status 200': (r) => r.status === 200,
        'query response time < 500ms': (r) => r.timings.duration < 500,
        'query has meeting data': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.id !== undefined && !body.error;
            } catch {
                return false;
            }
        },
    });

    querySuccessRate.add(success);
    if (!success) {
        queryErrors.add(1);
        console.error(`[IV-02] 조회 실패 meetingId=${meetingId} status=${res.status} body=${res.body}`);
    }

    sleep(0.2 + Math.random() * 0.3);
}

export function handleSummary(data) {
    return {
        'k6/results/iv02-summary.json': JSON.stringify(data, null, 2),
        stdout: makeTextSummary(data),
    };
}

function makeTextSummary(data) {
    const metrics = data.metrics;
    const lines = [
        '═══════════════════════════════════════════════════',
        ' IV-02: join-pool 고갈 시 service-pool 격리 검증 결과',
        '═══════════════════════════════════════════════════',
        `  join  성공률     : ${(metrics['join_success_rate']?.values?.rate * 100 || 0).toFixed(2)}%`,
        `  조회  성공률     : ${(metrics['query_success_rate']?.values?.rate * 100 || 0).toFixed(2)}%  (목표: ≥99.9%)`,
        `  조회 평균 응답   : ${(metrics['query_duration_ms']?.values?.avg || 0).toFixed(0)}ms`,
        `  조회 p95 응답    : ${(metrics['query_duration_ms']?.values?.['p(95)'] || 0).toFixed(0)}ms  (목표: <500ms)`,
        `  조회 실패 건수   : ${metrics['query_errors']?.values?.count || 0}  (목표: 0)`,
        '─────────────────────────────────────────────────',
        ' * join VU=120 → JoinPool(100) 초과 포화 상태에서',
        '   GET /meetings/{id}(QueryPool)은 독립 동작해야 함.',
        '═══════════════════════════════════════════════════',
    ];
    return lines.join('\n');
}
