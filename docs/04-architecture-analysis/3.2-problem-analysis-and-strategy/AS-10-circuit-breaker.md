# AS-10. Circuit Breaker / Fallback

## 적용 대상

- **아키텍처 드라이버**: AD-04 (핵심 기능 성공률 99.9%)
- **해결 이슈**:
  - ISSUE-02: `GET /members/{email}` API에서 외부 서버(AC서버, Copilot Admin 서버)가 장애 상태에 빠지면, `CompletableFuture.allOf()` 대기 중 해당 Future가 timeout(3,000ms)까지 블로킹된다. 서킷이 없으면 장애 외부 서버에 대한 호출이 계속 누적된다.
  - ISSUE-06: WC서버·VC서버·AC서버 장애 시 Feign read timeout(3,000ms) 만료까지 스레드가 점유된다. VC + AC 순차 호출 시 최대 6,000ms. AS-09 Bulkhead로 스레드 풀이 격리되더라도, 서킷 브레이커 없이는 `externalCallExecutor` 스레드 풀 자체가 장애 외부 서버 호출로 소진된다.
  - ISSUE-08: 10개 이상의 외부 연계가 단일 코드베이스에 혼재하면 외부 서버별로 차별화된 CB 정책 적용이 사실상 불가능하다. AS-11 ACL이 연계별 모듈을 분리하고 나서야 각 모듈 내에서 독립적인 CB 정책 적용이 가능해진다.
- **설계 목표**: DG-04 (핵심 기능 성공률 99.9%)
- **관련 유스케이스**: UC-01 (사용자 권한 갱신), UC-03 (회의 시작), UC-04 (회의 입장)
- **관련 품질 요구사항**: QA-04 (핵심 기능 가용성), QA-05 (외부 서버 장애 격리)

## 설계 근거

ISSUE-06의 핵심 메커니즘은 다음과 같다. WC서버가 장애 상태에 빠지면 Hystrix CB가 개방되기 전까지 Feign 호출은 read timeout(3,000ms) 만료까지 블로킹된다. 2만 명 동시 입장 구간에 WC서버 장애가 겹치면, CB 개방 이전 구간에만 `externalCallExecutor` 스레드 풀에 3,000ms씩 블로킹된 스레드가 빠르게 누적된다. CB가 개방된 이후에도 전역 일괄 설정으로는 WC서버(fail-fast 후 재시도 큐 전환)와 Copilot Admin 서버(캐시·DB 폴백)처럼 서버 역할에 따라 다른 복구 경로를 취할 수 없어, 모든 외부 서버 장애가 동일한 방식으로 처리된다.

서킷 브레이커의 역할은 이 상황을 조기 차단하는 것이다. WC서버의 연속 실패율이 임계값(예: 50%)을 넘으면 서킷을 Open 상태로 전환하고, 이후 WC서버로의 호출 시도를 즉시 거부(fail-fast)하여 스레드 블로킹 없이 fallback을 수행한다. 이를 통해 WC서버 장애가 포털 전체 스레드 고갈로 전파되는 것을 차단한다.

그런데 현재 시스템의 외부 서버들은 특성이 서로 다르다. WC서버는 모든 입장 처리에 필수적이고, Copilot Admin 서버(LLM·용어사전 권한)는 장애 시 DB 저장값으로 폴백 가능하다. AC서버 장애 시에는 AC 미포함 회의의 입장·시작은 계속 가능해야 한다. 외부 서버별로 timeout·실패율 임계값·fallback 전략이 달라야 하는 이유다.

## 대안

### 대안 1. 현행 Feign + Hystrix CB 유지

**개념**: 현행 Feign + Hystrix 서킷 브레이커를 그대로 유지한다. Hystrix는 Feign과 결합하여 연계 서버별로 타임아웃·CB 임계값을 개별 설정할 수 있으며, 현행 시스템에도 서버별 Hystrix Feign 설정이 적용되어 있다.

**이 시스템 적용 방식**: 변경 없음.

**한계**: 첫째, Hystrix는 Netflix가 2018년 유지보수 중단을 선언했으며 Spring Cloud에서도 공식 지원이 종료되어, Spring Boot 3.x 환경에서 장기적인 신뢰를 보장하기 어렵다. 둘째, 서버별 CB 설정 변경이 필요할 때마다 전체 서비스 재배포가 강제된다. ISSUE-08에서 지적한 "피크 대응 시 긴급 연계 정책 조정 불가" 문제가 CB 임계값 조정에도 동일하게 적용된다. 셋째, Hystrix의 fallback 메서드 구조로는 AS-03(L2 Redis → DB) · AS-02(비동기 재시도 큐) · AS-05(Eager Response)와 연동하는 계층적 fallback 체인을 구현하기 어렵다.

---

### 대안 2. Resilience4j Circuit Breaker 일괄 적용 (모든 외부 서버 동일 설정)

**개념**: Resilience4j `@CircuitBreaker` 어노테이션을 모든 외부 서버 Feign 호출에 동일한 설정으로 일괄 적용한다.

**이 시스템 적용 방식**: `application.yml`에 글로벌 CB 설정 (`slidingWindowSize=10`, `failureRateThreshold=50%`, `waitDurationInOpenState=30s`) 적용.

**한계**: 외부 서버 특성을 무시한 균일 정책이므로, 응답이 느리지만 정상인 서버(예: AC서버, 처리 시간 2,000ms)를 과도하게 차단하거나, 빠르게 장애가 확대되는 서버(WC서버 대규모 장애)를 늦게 감지할 수 있다. 또한 fallback 전략이 모든 서버에 동일하게 적용되어 서버 특성에 맞는 세밀한 복구 처리가 불가능하다.

---

### 대안 3. 외부 서버별 차등 CB + 계층적 Fallback

**개념**: 외부 서버 특성에 따라 timeout·실패율 임계값·halfOpen 요청 수를 차등 적용한다. Fallback 전략도 각 서버의 특성과 포털 서비스에서의 역할에 맞게 차별화한다. AS-11 ACL과 결합하여 각 연계 모듈 내에서 CB 정책을 독립 관리한다.

**이 시스템 적용 방식**:

**[외부 서버별 CB 설정]**

| 외부 서버 | slidingWindowSize | failureRateThreshold | waitDuration | 근거 |
|---------|-------------------|---------------------|-------------|------|
| WC서버 | 20 | 50% | 10s | 입장 처리 필수. 빠른 감지·복구 필요 |
| VC서버 | 10 | 60% | 30s | AC 포함 회의 개설에만 영향. 일시 장애 허용 범위 넓음 |
| AC서버 | 10 | 60% | 30s | AC 권한 갱신. DB 폴백 가능하므로 관대한 임계값 |
| Copilot Admin | 5 | 70% | 60s | 권한 변경 빈도 낮음. DB 폴백으로 충분히 운영 가능 |

**[계층적 Fallback 전략]**

- **Copilot Admin 서버 장애** → AS-03 L2 Redis 캐시(마지막 적재값)로 폴백. Redis도 미스 시 DB 저장값 반환. 권한 갱신 실패이므로 기능에 영향 없음.
- **WC서버 장애** → AS-05 Eager Response 패턴과 결합: conference-token·입장 파라미터는 정상 생성·반환. WC서버 전달은 AS-02 재시도 큐에 등록. 클라이언트는 입장 파라미터를 수신하나 WC서버 접속은 재시도 필요 가능성 있음 → 모니터링 알림.
- **AC서버 장애** → AC 권한 DB 저장값으로 폴백. WC 전용·VC 포함 회의는 정상 처리 계속. AC 포함 회의 개설만 부분 차단.
- **VC서버 장애** → VC 포함 회의 개설 실패. WC 전용 회의 입장·시작은 정상. 에러 응답에 명확한 원인 메시지 포함.

**장점**: 외부 서버 특성에 맞는 정책으로 과도 차단·과소 차단 없이 정밀하게 동작한다. 서버별 fallback 전략이 AS-03 캐시, AS-02 비동기 큐와 연동하여 서비스 연속성을 최대화한다. AS-11 ACL에서 각 연계 모듈 내에 CB 설정이 캡슐화되므로 정책 변경 시 해당 모듈만 수정하면 된다.

## 채택

**채택 대안**: 대안 3 — 외부 서버별 차등 CB + 계층적 Fallback

**채택 근거**: 대안 1은 Hystrix deprecated로 Spring Boot 3.x 장기 운영이 불확실하고, 전역 일괄 설정으로는 서버 특성별 정밀 제어가 불가능하여 QA-04·QA-05 달성 불충분. 대안 2는 Resilience4j로 전환하나 균일 정책이라 서버 특성 무시. 대안 3은 각 외부 서버의 역할과 장애 영향 범위를 반영한 정책으로 QA-05(장애 격리)를 정밀하게 충족한다. AS-02 비동기 처리, AS-03 캐시, AS-05 Eager Response, AS-11 ACL과 연동하여 fallback 효과가 극대화된다.

**적용 방향**:
- `spring-boot-starter-actuator` + `resilience4j-spring-boot3` 의존성 추가
- `application.yml`에 외부 서버별 `resilience4j.circuitbreaker.instances.{name}` 설정 분리
- AS-11 ACL의 각 연계 모듈(`integration.wc`, `integration.ac`, `integration.copilot`) 내에 `@CircuitBreaker(name = "wcServer", fallbackMethod = "wcFallback")` 어노테이션 적용
- fallback 메서드: AS-03 캐시 조회 → DB 조회 → 서비스 부분 제공 순서의 계층적 폴백 구현
- Actuator endpoint `/actuator/circuitbreakers`로 CB 상태 실시간 모니터링
