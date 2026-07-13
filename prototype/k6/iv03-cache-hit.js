/**
 * IV-03: 피크 시 권한 갱신 응답시간 1초 이내 + 캐시 hit율 90% 이상
 * 검증 목표:
 *   - GET /members/{email}?type=detail 응답시간 평균 1초 이내
 *   - L1 Caffeine 캐시 hit율 90% 이상
 * 관련 AS: AS-03 (L1/L2 캐시), AS-06 (Pre-warming Scheduler)
 *
 * 실행:
 *   k6 run --env BASE_URL=http://localhost k6/iv03-cache-hit.js
 *
 * 사전 조건: Pre-warming Scheduler가 1분 이상 실행된 상태 (캐시 선적재 완료)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost';

const authSuccessRate = new Rate('auth_success_rate');
const authDuration = new Trend('auth_duration_ms', true);
const authErrors = new Counter('auth_errors');

export const options = {
    scenarios: {
        // 피크 시 권한 갱신 요청 집중 시뮬레이션
        auth_peak: {
            executor: 'ramping-vus',
            startVUs: 10,
            stages: [
                { duration: '1m', target: 200 },  // 피크 진입
                { duration: '3m', target: 300 },  // 피크 유지
                { duration: '1m', target: 0 },    // 종료
            ],
        },
    },
    thresholds: {
        // IV-03 합격 기준
        'auth_success_rate': ['rate>0.99'],
        'auth_duration_ms': ['avg<1000', 'p(95)<2000'],  // 평균 1초 이내
        'http_req_failed': ['rate<0.01'],
    },
};

// Pre-warming에서 선적재된 1000명 이메일 (DB testdata와 동일)
const EMAILS = Array.from({ length: 1000 }, (_, i) => `user${i + 1}@example.com`);
// 일부 이메일은 반복해서 캐시 hit 유도
const HOT_EMAILS = EMAILS.slice(0, 100);

export function setup() {
    // 캐시 선적재 대기 (Pre-warming이 실행 중이면 추가 워밍)
    console.log('[IV-03] 캐시 워밍 상태 확인...');
    const checkRes = http.get(`${BASE_URL}/actuator/metrics/cache.gets?tag=name:member-permissions&tag=result:hit`);
    if (checkRes.status === 200) {
        const data = JSON.parse(checkRes.body);
        console.log(`[IV-03] 현재 캐시 hit 수: ${JSON.stringify(data?.measurements)}`);
    }
}

export default function () {
    // 80%는 hot email (캐시 hit 유도), 20%는 cold (캐시 miss)
    const useHot = Math.random() < 0.8;
    const emails = useHot ? HOT_EMAILS : EMAILS;
    const email = emails[Math.floor(Math.random() * emails.length)];

    const start = Date.now();
    const res = http.get(
        `${BASE_URL}/members/${encodeURIComponent(email)}?type=detail`,
        { timeout: '5s' }
    );
    const elapsed = Date.now() - start;
    authDuration.add(elapsed);

    const success = check(res, {
        'auth status 200': (r) => r.status === 200,
        'auth response time < 1s': (r) => r.timings.duration < 1000,
        'auth has acRole': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.acRole !== undefined;
            } catch {
                return false;
            }
        },
    });

    authSuccessRate.add(success);
    if (!success) {
        authErrors.add(1);
    }

    sleep(Math.random() * 0.3);
}

export function teardown() {
    // 테스트 종료 후 캐시 hit율 수집
    console.log('\n[IV-03] 캐시 메트릭 수집...');

    const hitRes = http.get(
        `${BASE_URL}/actuator/metrics/cache.gets?tag=name:member-permissions&tag=result:hit`
    );
    const missRes = http.get(
        `${BASE_URL}/actuator/metrics/cache.gets?tag=name:member-permissions&tag=result:miss`
    );

    if (hitRes.status === 200 && missRes.status === 200) {
        try {
            const hitData = JSON.parse(hitRes.body);
            const missData = JSON.parse(missRes.body);
            const hits = hitData?.measurements?.[0]?.value || 0;
            const misses = missData?.measurements?.[0]?.value || 0;
            const total = hits + misses;
            const hitRate = total > 0 ? (hits / total * 100).toFixed(2) : 0;
            console.log(`\n[IV-03 캐시 결과]`);
            console.log(`  L1 캐시 hit  : ${hits}`);
            console.log(`  L1 캐시 miss : ${misses}`);
            console.log(`  hit율        : ${hitRate}%  (목표: ≥90%)`);
        } catch (e) {
            console.error('[IV-03] 캐시 메트릭 파싱 오류:', e.message);
        }
    }
}

export function handleSummary(data) {
    return {
        'k6/results/iv03-summary.json': JSON.stringify(data, null, 2),
        stdout: makeTextSummary(data),
    };
}

function makeTextSummary(data) {
    const metrics = data.metrics;
    const lines = [
        '═══════════════════════════════════════════════════',
        ' IV-03: 캐시 hit율 + 권한 갱신 응답시간 검증 결과',
        '═══════════════════════════════════════════════════',
        `  성공률           : ${(metrics['auth_success_rate']?.values?.rate * 100 || 0).toFixed(2)}%`,
        `  평균 응답시간     : ${(metrics['auth_duration_ms']?.values?.avg || 0).toFixed(0)}ms  (목표: <1000ms)`,
        `  p95 응답시간      : ${(metrics['auth_duration_ms']?.values?.['p(95)'] || 0).toFixed(0)}ms  (목표: <2000ms)`,
        `  실패 건수         : ${metrics['auth_errors']?.values?.count || 0}`,
        '─────────────────────────────────────────────────',
        ' * L1 캐시 hit율은 teardown 로그 및 아래 Actuator에서 확인:',
        '   GET http://localhost:8080/actuator/metrics/cache.gets',
        '═══════════════════════════════════════════════════',
    ];
    return lines.join('\n');
}
