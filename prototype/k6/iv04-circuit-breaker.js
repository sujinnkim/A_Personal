/**
 * IV-04: 외부 서버 장애 주입 시 핵심 기능 성공률 99.9% 이상
 * 검증 목표:
 *   - Meeting Manager 장애 → 입장(join) / 토큰 발급 성공률 99.9% 이상
 *   - AC서버 장애 → 권한 갱신 성공률 99.9% 이상 (DB 폴백)
 *   - Copilot 장애 → 권한 갱신 성공률 99.9% 이상 (L2 Redis → DB 폴백)
 * 관련 AS: AS-09 (Circuit Breaker)
 *
 * 실행:
 *   k6 run --env BASE_URL=http://localhost --env STUB_URL=http://localhost:8090 k6/iv04-circuit-breaker.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost';
const STUB_URL = __ENV.STUB_URL || 'http://localhost:8090';

// 각 시나리오별 메트릭
const joinSuccessRate = new Rate('join_success_rate');
const tokenSuccessRate = new Rate('token_success_rate');
const authSuccessRate = new Rate('auth_success_rate');
const querySuccessRate = new Rate('query_success_rate');
const joinDuration = new Trend('join_duration_ms', true);
const authDuration = new Trend('auth_duration_ms', true);

export const options = {
    scenarios: {
        fault_injection_test: {
            executor: 'per-vu-iterations',
            vus: 50,
            iterations: 20,
            maxDuration: '10m',
        },
    },
    thresholds: {
        // IV-04 합격 기준: 모든 핵심 기능 99.9% 이상
        'join_success_rate': ['rate>0.999'],
        'token_success_rate': ['rate>0.999'],
        'auth_success_rate': ['rate>0.999'],
        'query_success_rate': ['rate>0.999'],
    },
};

const MEETING_IDS = Array.from({ length: 10 }, (_, i) => i + 1);
const EMAILS = Array.from({ length: 100 }, (_, i) => `user${i + 1}@example.com`);

function enableFault(server, delayMs = 0, errorMode = true) {
    const res = http.post(
        `${STUB_URL}/stub/${server}/fault`,
        JSON.stringify({ delayMs, errorMode }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    console.log(`[IV-04] 장애 활성화 server=${server} status=${res.status}`);
    return res.status === 200;
}

function disableFault(server) {
    const res = http.del(`${STUB_URL}/stub/${server}/fault`);
    console.log(`[IV-04] 장애 해제 server=${server} status=${res.status}`);
    return res.status === 200;
}

export function setup() {
    // 초기 장애 해제 (이전 테스트 잔재 정리)
    disableFault('meeting-manager');
    disableFault('ac');
    disableFault('copilot');
    sleep(1);

    // 초기 상태 확인
    const statusRes = http.get(`${STUB_URL}/stub/status`);
    console.log('[IV-04] Stub 초기 상태:', statusRes.body);
}

export default function () {
    const meetingId = MEETING_IDS[__VU % MEETING_IDS.length];
    const email = EMAILS[(__VU * 7 + __ITER) % EMAILS.length];

    // ── Phase 1: 정상 동작 baseline ──────────────────────────────
    group('Phase1_Baseline', () => {
        testJoin(meetingId, email);
        testAuth(email);
        testQuery(meetingId);
        sleep(0.5);
    });

    // ── Phase 2: Meeting Manager 장애 주입 ───────────────────────
    if (__VU === 1 && __ITER === 5) {
        console.log('\n[IV-04] === Meeting Manager 장애 주입 시작 ===');
        enableFault('meeting-manager', 0, true);  // 즉시 503
    }

    group('Phase2_MeetingManagerFault', () => {
        testJoin(meetingId, email);       // fallback: LOCAL 토큰
        testToken(meetingId, email);      // fallback: 임시 토큰
        testQuery(meetingId);             // 영향 없어야 함
        sleep(0.5);
    });

    // ── Phase 3: AC서버 장애 주입 ────────────────────────────────
    if (__VU === 1 && __ITER === 10) {
        console.log('\n[IV-04] === AC서버 장애 주입 시작 ===');
        disableFault('meeting-manager');
        enableFault('ac', 0, true);  // 즉시 503
    }

    group('Phase3_AcServerFault', () => {
        testAuth(email);    // fallback: DB 저장값
        testJoin(meetingId, email);
        sleep(0.5);
    });

    // ── Phase 4: Copilot 장애 주입 ───────────────────────────────
    if (__VU === 1 && __ITER === 15) {
        console.log('\n[IV-04] === Copilot 장애 주입 시작 ===');
        disableFault('ac');
        enableFault('copilot', 0, true);  // 즉시 503
    }

    group('Phase4_CopilotFault', () => {
        testAuth(email);    // fallback: L2 Redis → DB
        testJoin(meetingId, email);
        sleep(0.5);
    });

    // ── Phase 5: 정상 복구 ───────────────────────────────────────
    if (__VU === 1 && __ITER === 18) {
        console.log('\n[IV-04] === 전체 장애 해제 (복구) ===');
        disableFault('meeting-manager');
        disableFault('ac');
        disableFault('copilot');
    }

    group('Phase5_Recovery', () => {
        testJoin(meetingId, email);
        testAuth(email);
        testQuery(meetingId);
        sleep(0.5);
    });
}

function testJoin(meetingId, email) {
    const start = Date.now();
    const res = http.post(
        `${BASE_URL}/meetings/${meetingId}/join`,
        JSON.stringify({ email: `iv04-${email}` }),
        { headers: { 'Content-Type': 'application/json' }, timeout: '15s' }
    );
    joinDuration.add(Date.now() - start);

    const success = check(res, {
        'join 성공 (200)': (r) => r.status === 200,
        'join body 유효': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.status === 'JOINED' || body.status === 'ALREADY_JOINED';
            } catch { return false; }
        },
    });
    joinSuccessRate.add(success);
    if (!success) console.error(`[join 실패] status=${res.status} body=${res.body}`);
}

function testToken(meetingId, email) {
    const res = http.get(
        `${BASE_URL}/meetings/${meetingId}/conference-token?email=${encodeURIComponent(email)}`,
        { timeout: '15s' }
    );
    const success = check(res, {
        'token 성공 (200)': (r) => r.status === 200,
        'token 값 존재': (r) => {
            try {
                return JSON.parse(r.body).token !== undefined;
            } catch { return false; }
        },
    });
    tokenSuccessRate.add(success);
    if (!success) console.error(`[token 실패] status=${res.status} body=${res.body}`);
}

function testAuth(email) {
    const start = Date.now();
    const res = http.get(
        `${BASE_URL}/members/${encodeURIComponent(email)}?type=detail`,
        { timeout: '15s' }
    );
    authDuration.add(Date.now() - start);

    const success = check(res, {
        'auth 성공 (200)': (r) => r.status === 200,
        'auth acRole 존재': (r) => {
            try {
                return JSON.parse(r.body).acRole !== undefined;
            } catch { return false; }
        },
    });
    authSuccessRate.add(success);
    if (!success) console.error(`[auth 실패] status=${res.status} body=${res.body}`);
}

function testQuery(meetingId) {
    const res = http.get(`${BASE_URL}/meetings/${meetingId}`, { timeout: '5s' });
    const success = check(res, {
        'query 성공 (200)': (r) => r.status === 200,
        'query id 존재': (r) => {
            try {
                return JSON.parse(r.body).id !== undefined;
            } catch { return false; }
        },
    });
    querySuccessRate.add(success);
    if (!success) console.error(`[query 실패] status=${res.status} body=${res.body}`);
}

export function teardown() {
    // 테스트 종료 후 장애 해제 보장
    disableFault('meeting-manager');
    disableFault('ac');
    disableFault('copilot');
    console.log('[IV-04] 모든 장애 해제 완료');
}

export function handleSummary(data) {
    return {
        'k6/results/iv04-summary.json': JSON.stringify(data, null, 2),
        stdout: makeTextSummary(data),
    };
}

function makeTextSummary(data) {
    const metrics = data.metrics;
    const lines = [
        '═══════════════════════════════════════════════════',
        ' IV-04: 외부 서버 장애 주입 시 핵심 기능 성공률 검증 결과',
        '═══════════════════════════════════════════════════',
        `  입장(join) 성공률  : ${(metrics['join_success_rate']?.values?.rate * 100 || 0).toFixed(3)}%  (목표: ≥99.9%)`,
        `  토큰 발급 성공률   : ${(metrics['token_success_rate']?.values?.rate * 100 || 0).toFixed(3)}%  (목표: ≥99.9%)`,
        `  권한 갱신 성공률   : ${(metrics['auth_success_rate']?.values?.rate * 100 || 0).toFixed(3)}%  (목표: ≥99.9%)`,
        `  회의 조회 성공률   : ${(metrics['query_success_rate']?.values?.rate * 100 || 0).toFixed(3)}%  (목표: ≥99.9%)`,
        `  입장 평균 응답시간 : ${(metrics['join_duration_ms']?.values?.avg || 0).toFixed(0)}ms`,
        `  권한 평균 응답시간 : ${(metrics['auth_duration_ms']?.values?.avg || 0).toFixed(0)}ms`,
        '─────────────────────────────────────────────────',
        ' 장애 주입 단계:',
        '   Phase1: 정상 baseline',
        '   Phase2: Meeting Manager 503 → 임시 토큰 fallback',
        '   Phase3: AC서버 503 → DB 저장값 fallback',
        '   Phase4: Copilot 503 → L2 Redis → DB fallback',
        '   Phase5: 전체 복구',
        '═══════════════════════════════════════════════════',
    ];
    return lines.join('\n');
}
