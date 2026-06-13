# 4.2.1. 실행 뷰 (Runtime View)

실행 뷰는 주요 유스케이스에서 컴포넌트 간 런타임 상호작용을 시퀀스 다이어그램으로 기술한다. 각 시나리오에서 AS 설계 전략이 어느 지점에서 동작하는지를 명시한다.

---

## 시나리오 1. UC-04 회의 입장 — 피크 집중 정상 처리

피크 시간대 8만 명 동시 입장 요청을 AS-04·AS-02·AS-08·AS-10이 협력하여 처리하는 흐름이다.

```mermaid
sequenceDiagram
    participant C as 클라이언트
    participant CON as 입장 전용 Connector<br/>(port 8081, AS-04)
    participant DE as domain.entry<br/>(AS-01)
    participant MM_INT as integration.meetingManager<br/>(AS-10 ACL · AS-09 CB)
    participant EX as externalCallExecutor<br/>(AS-02 @Async)
    participant JP as join-pool<br/>(AS-08 Bulkhead)
    participant DB as MariaDB Primary
    participant MM as Meeting Manager

    C->>CON: POST /meetings/{id}/join
    Note over CON: AS-04: 전용 스레드 풀에서 수신<br/>단순 조회와 스레드 경합 없음

    CON->>DE: 입장 요청 전달
    DE->>JP: DB 커넥션 획득 (AS-08)
    JP->>DB: 입장 가능 여부 확인<br/>conference-token 발급
    DB-->>JP: 결과 반환
    JP-->>DE: 커넥션 반환

    DE->>MM_INT: 참석자 입장 정보 조회 요청 (AS-10 Gateway 인터페이스 호출)
    MM_INT->>EX: @Async("externalCallExecutor") 위임 (AS-02)
    Note over CON: AS-02: 서블릿 스레드 즉시 반환<br/>다음 요청 수용 가능

    EX->>MM: Meeting Manager 비동기 호출<br/>(AS-09 CB Closed → 정상 호출)
    MM-->>EX: wyzProParam 조립용 응답

    EX-->>C: CompletableFuture 완료 → 입장 파라미터 응답
```

**AS 적용 지점 요약**

| 지점 | 적용 AS | 효과 |
|-----|--------|------|
| 입장 전용 Connector (port 8081) | AS-04 | 단순 조회·권한 갱신 요청과 스레드 경합 차단 |
| domain.entry → join-pool | AS-08 | 입장 커넥션 고갈이 service-pool·general-pool에 전파되지 않음 |
| integration.meetingManager Gateway | AS-10 | 포털 도메인 모델이 Meeting Manager API 스키마 직접 노출 차단 |
| externalCallExecutor @Async | AS-02 | 서블릿 스레드 즉시 반환 → 8만 건 동시 요청 스레드 풀 고갈 방지 |
| CB 상태 (Closed) | AS-09 | 정상 상태에서 Meeting Manager 직접 호출 |

---

## 시나리오 2. UC-01 권한 갱신 — 캐시 hit/miss 분기

로그인 후 권한 갱신 시 L1·L2 캐시 분기 흐름이다. AS-03의 핵심 동작을 보여준다.

```mermaid
sequenceDiagram
    participant C as 클라이언트
    participant CON as 일반 Connector<br/>(port 8080)
    participant TF as ThrottlingFilter<br/>(AS-05)
    participant DA as domain.auth<br/>(AS-01)
    participant L1 as L1 Caffeine<br/>(AS-03, TTL 5분)
    participant L2 as L2 Redis<br/>(AS-03, TTL 30분~1h)
    participant AC_INT as integration.ac<br/>(AS-10 ACL · AS-09 CB)
    participant CP_INT as integration.copilot<br/>(AS-10 ACL · AS-09 CB)
    participant EX as externalCallExecutor<br/>(AS-02 @Async)
    participant ACS as AC서버
    participant CPS as Copilot Admin

    C->>CON: GET /members/{email}
    CON->>TF: 요청 전달 (AS-05: 피크 구간 비핵심 API 처리량 확인)
    Note over TF: 피크 구간 중 @ThrottleExempt 없는 API는<br/>Bucket4j SlidingWindowCounter 처리량 제한

    TF->>DA: 통과 허용

    DA->>L1: L1 Caffeine 조회 (AS-03)
    alt L1 hit
        L1-->>DA: 권한 데이터 즉시 반환
        DA-->>C: 응답 (외부 서버 호출 없음)
    else L1 miss
        L1-->>DA: miss
        DA->>L2: L2 Redis 조회 (AS-03)
        alt L2 hit
            L2-->>DA: 권한 데이터 반환
            DA->>L1: L1 적재 (L2 hit 시 L1도 갱신)
            DA-->>C: 응답
        else L2 miss
            L2-->>DA: miss
            par AC권한 조회
                DA->>AC_INT: AC권한 조회 요청 (AS-10 Gateway)
                AC_INT->>EX: @Async("externalCallExecutor") 위임 (AS-02)
                EX->>ACS: AC서버 비동기 호출 (AS-09 CB)
                ACS-->>EX: AC 권한 데이터
            and Copilot권한 조회
                DA->>CP_INT: Copilot권한 조회 요청 (AS-10 Gateway)
                CP_INT->>EX: @Async("externalCallExecutor") 위임 (AS-02)
                EX->>CPS: Copilot Admin 비동기 호출 (AS-09 CB)
                CPS-->>EX: Copilot 권한 데이터
            end
            Note over EX: CompletableFuture.allOf() 수렴
            EX->>L1: L1 + L2 동시 적재 (AS-03)
            EX->>L2: L2 적재
            EX-->>C: CompletableFuture 완료 → 응답
        end
    end
```

**AS 적용 지점 요약**

| 지점 | 적용 AS | 효과 |
|-----|--------|------|
| ThrottlingFilter | AS-05 | 피크 구간 비핵심 API 처리량 제한 (권한 갱신은 피크 구간 처리량 상한 적용) |
| L1 Caffeine 조회 | AS-03 | 인스턴스 로컬 hit → 네트워크 없이 즉시 반환 |
| L2 Redis 조회 | AS-03 | 분산 인스턴스 간 공유 캐시로 외부 서버 중복 호출 방지 |
| AC·Copilot 병렬 호출 | AS-10 + AS-02 | AC권한·Copilot권한 CompletableFuture 병렬 조회 후 L1·L2 동시 적재 |
| L1 + L2 동시 적재 | AS-03 | L2 miss 후 외부 호출 결과를 양 계층에 동시 적재 |

---

## 시나리오 3. AS-06 Pre-warming 동작

피크 N분 전 PreWarmingScheduler가 L2 Redis를 선제 적재하고 ThrottlingFilter를 활성화하는 흐름이다.

```mermaid
sequenceDiagram
    participant SCH as PreWarmingScheduler<br/>(1분 주기, preWarmExecutor, AS-06)
    participant DB as MariaDB<br/>(예약 회의 조회)
    participant PD as PeakDetector<br/>(AS-05 Throttling 활성화)
    participant EXT as 외부 서버<br/>(AC서버 · Copilot Admin)
    participant L2 as L2 Redis<br/>(AS-03 캐시 인프라)

    Note over SCH: @Scheduled(fixedDelay=60_000)<br/>서블릿 스레드와 완전 분리

    SCH->>DB: 시작 시각 임박(N분 이내) + 참석자 수 ≥ 임계값 회의 조회
    DB-->>SCH: 대상 회의 목록 + 참석자 목록

    SCH->>PD: setActive(true) — AS-05 Throttling 동시 활성화
    Note over PD: 피크 구간 비핵심 API 처리량 제한 시작

    loop 50명/배치 (배치 간 100ms 딜레이)
        SCH->>EXT: 참석자 권한 배치 선제 조회 (저우선순위 실행)
        EXT-->>SCH: 권한 데이터
        SCH->>L2: L2 Redis 선제 적재 (AS-03 캐시 인프라 활용)
    end

    Note over L2: 피크 진입 시 캐시 hit율 높은 상태 유지<br/>Thundering Herd 없이 피크 대응
```

**AS 적용 지점 요약**

| 지점 | 적용 AS | 효과 |
|-----|--------|------|
| PreWarmingScheduler (preWarmExecutor) | AS-06 | 예약 회의 데이터 기반 동적 피크 감지 |
| setActive(true) → PeakDetector | AS-05 | 워밍 시작과 동시에 비핵심 API 처리량 제한 활성화 |
| L2 Redis 선제 적재 | AS-06 + AS-03 | 피크 진입 시점 cold start 없이 캐시 hit율 유지 |
| 50명/배치 분할 + 100ms 딜레이 | AS-06 | 워밍 호출이 외부 서버에 순간 부하를 주지 않도록 분산 |

---

## 시나리오 4. AS-08 Bulkhead 격리 — join-pool 고갈 시 service-pool 독립

join-pool이 포화 상태에서도 service-pool을 사용하는 UC-03(회의 시작)이 독립적으로 정상 처리됨을 보여준다.

```mermaid
sequenceDiagram
    participant C_J as 입장 요청 (8만 명)
    participant C_S as 회의 시작 요청 (UC-03)
    participant JP as join-pool<br/>(AS-08, 100conn)
    participant SP as service-pool<br/>(AS-08, 40conn)
    participant DB as MariaDB Primary

    Note over JP: join-pool connectionTimeout 만료<br/>→ 입장 처리 DB 접근 불가 (포화 상태)

    C_J->>JP: 커넥션 요청
    JP-->>C_J: connectionTimeout 초과 → SQLTransientConnectionException
    Note over C_J: join-pool 고갈 — 입장 처리 실패

    C_S->>SP: 커넥션 요청 (service-pool 독립 사용)
    SP->>DB: 회의 시작 처리 (UC-03)
    DB-->>SP: 정상 응답
    SP-->>C_S: 회의 시작 성공

    Note over SP: AS-08: join-pool 고갈이 service-pool에<br/>물리적으로 전파되지 않음 → QA-03 충족
```

**AS 적용 지점 요약**

| 지점 | 적용 AS | 효과 |
|-----|--------|------|
| join-pool 독립 DataSource | AS-08 | 입장 커넥션 고갈이 회의 시작·초대 커넥션에 영향 없음 |
| service-pool 독립 DataSource | AS-08 | join-pool 상태와 무관하게 독립 운영 |
| QA-03 달성 구조 | AS-01 + AS-08 | domain.entry 경계 기반 DataSource Bean 분리로 격리 구현 |

---

## 시나리오 5. AS-09 Circuit Breaker 동작

외부 서버 장애 발생 시 CB Open 상태 전환 및 서버별 차등 fallback 처리 흐름이다.

```mermaid
sequenceDiagram
    participant C as 클라이언트
    participant DE as domain.entry
    participant MM_INT as integration.meetingManager<br/>(AS-10 ACL)
    participant CB_MM as wcServer CircuitBreaker<br/>(AS-09, failureRate 50%, wait 10s)
    participant MM as Meeting Manager

    participant DA as domain.auth
    participant CP_INT as integration.copilot<br/>(AS-10 ACL)
    participant CB_CP as copilotAdmin CircuitBreaker<br/>(AS-09, failureRate 70%, wait 60s)
    participant L2 as L2 Redis<br/>(AS-03)
    participant DB_F as MariaDB<br/>(DB 폴백)

    Note over MM: Meeting Manager 장애 발생
    C->>DE: POST /meetings/{id}/join
    DE->>MM_INT: 참석자 입장 정보 조회
    MM_INT->>CB_MM: 호출 시도
    CB_MM->>MM: 호출 (실패율 누적 → threshold 도달)
    MM-->>CB_MM: 오류 응답
    Note over CB_MM: 실패율 ≥ 50% → CB Open 전환
    CB_MM-->>MM_INT: fail-fast (즉시 거부)
    MM_INT-->>DE: fallback: 사용자 오류 반환
    DE-->>C: 회의 입장 실패 응답 (명확한 원인 메시지)

    Note over MM: CB Open 상태 — timeout 없이 즉시 거부<br/>externalCallExecutor 스레드 블로킹 없음

    Note over DA: Copilot Admin 장애 발생
    C->>DA: GET /members/{email}
    DA->>CP_INT: 권한 조회
    CP_INT->>CB_CP: 호출 시도
    CB_CP-->>CP_INT: CB Open → fail-fast
    CP_INT->>L2: L2 Redis 폴백 조회 (AS-03 캐시)
    alt L2 hit
        L2-->>CP_INT: 마지막 적재값 반환
        CP_INT-->>DA: 캐시 기반 권한 데이터
        DA-->>C: 정상 응답 (서비스 연속성 유지)
    else L2 miss
        L2-->>CP_INT: miss
        CP_INT->>DB_F: DB 저장값 폴백
        DB_F-->>CP_INT: 권한 데이터
        CP_INT-->>DA: DB 기반 권한 데이터
        DA-->>C: 정상 응답
    end
```

**AS 적용 지점 요약**

| 지점 | 적용 AS | 효과 |
|-----|--------|------|
| wcServer CB Open → fail-fast | AS-09 | Meeting Manager 장애 시 timeout 없이 즉시 거부 → 스레드 블로킹 방지 |
| copilotAdmin CB Open → L2 Redis 폴백 | AS-09 + AS-03 | Copilot Admin 장애 시 캐시 기반 계층적 복구 |
| DB 저장값 최종 폴백 | AS-09 | L2 miss 시 DB 저장 권한값으로 서비스 연속성 유지 |
| 서버별 독립 CB 정책 | AS-09 + AS-10 | WC서버(50%, 10s) vs Copilot Admin(70%, 60s) 차등 적용 |
