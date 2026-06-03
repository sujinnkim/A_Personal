# AS-08. CQRS

## 적용 대상

> **전제**: AS-01(MSA / 도메인 서비스 분리)의 파생 전략. AS-01이 설정한 도메인 경계 내에서 Command/Query 모델을 분리한다.

- **아키텍처 드라이버**: AD-03 (DB 커넥션 풀 장애 격리)
- **해결 이슈**:
  - ISSUE-07: 대규모 회의 시작 시점에 사용자 입장 write(front-api 경유), cPaaS 피드백 write(server-api 경유), 참석자 목록·대기실 상태 read가 동일 DB 테이블에 동시 집중된다. write lock 경합으로 입장 처리(UC-04) 자체가 지연되고, 조회 응답 latency도 급증한다.
- **설계 목표**: DG-03 (특정 기능 커넥션 고갈 시 타 기능 정상 운영), DG-04 (핵심 기능 성공률 99.9%)
- **관련 유스케이스**: UC-04 (회의 입장), UC-05 (회의 퇴장), UC-06 (참석자 초대)
- **관련 품질 요구사항**: QA-03 (DB 커넥션 풀 격리 신뢰성), QA-04 (핵심 기능 가용성)

## 설계 근거

ISSUE-07의 경합 구조를 상세히 분석하면 두 가지 write 경로가 동시에 동일 테이블을 타격한다.

첫째 **사용자 요청 경유 write**: `User → front-api → Meeting Manager → server-api → participants 테이블 write` (입장 처리, 대기실 승인 등)

둘째 **cPaaS 피드백 경유 write**: `cPaaS → Meeting Manager → server-api → participants 테이블 write` (퇴장, 연결 끊김 등 참석자 상태 변경)

두 write 경로는 사용자 요청과 무관하게 독립적으로 발생하므로, 대규모 회의 시작 시점에는 두 경로의 write가 동시에 최고조에 달한다. 여기에 참석자 목록 조회(read)까지 집중되면, 동일 레코드에 대한 read/write lock 경합이 최대화된다.

이 상황에서 입장 처리(write)가 조회(read)의 lock 대기에 의해 지연되거나, 역으로 조회가 입장 write lock에 블로킹되는 상황이 반복된다. QA-04(핵심 기능 성공률 99.9%)에서 "가장 중요한 회의 입장 처리가 피드백 write 및 조회 트래픽에 의해 지연된다"는 것은 구조적 설계 결함이다.

해결의 핵심은 **write(Command)와 read(Query)가 서로의 DB lock을 경쟁하지 않는 구조**를 만드는 것이다.

## 대안

### 대안 1. 현행 단일 DB 모델 (Command/Query 동일 DataSource)

**개념**: 현행 구조 유지. 입장 write, 피드백 write, 참석자 조회 read가 모두 동일한 HikariCP DataSource와 DB Primary 인스턴스를 사용한다.

**이 시스템 적용 방식**: 변경 없음. `participants` 테이블에 대한 write/read 경합이 피크 시에도 그대로 발생.

**한계**: DB 레벨에서 lock 경합을 피할 방법이 없다. write 부하와 read 부하가 높아질수록 경합이 심화된다. 인덱스 최적화나 쿼리 튜닝으로 일부 완화는 가능하나, 2만 명 동시 입장이라는 규모에서는 구조적 한계가 있다.

---

### 대안 2. 완전한 이벤트 소싱 + CQRS

**개념**: 모든 Command를 이벤트 스트림으로 저장하고, Query는 이벤트를 기반으로 생성된 별도의 Read Model(투영)을 조회한다. Command DB와 Query DB가 완전히 분리된다.

**이 시스템 적용 방식**: `participants` 테이블의 변경을 `ParticipantJoined`, `ParticipantLeft` 등의 이벤트로 저장하는 Event Store 도입. 별도 Read Model DB에 참석자 목록 투영 유지.

**한계**: 기존 JPA 엔티티 기반 도메인 모델을 이벤트 소싱 모델로 전면 재설계해야 한다. C-04(점진적 적용) 제약에 정면으로 충돌한다. 이벤트 소싱 도입은 이벤트 순서 보장, 투영 복구, 이벤트 버저닝 등 복잡한 문제를 수반한다. 현재 시스템의 성숙도에서 도입하기에 위험 대비 효과가 높지 않다.

---

### 대안 3. 경량 CQRS (Primary/Replica 라우팅 분리)

**개념**: 동일한 MariaDB 구조를 유지하면서 Primary(write) 인스턴스와 Replica(read) 인스턴스를 분리하고, Command(write)는 Primary로, Query(read)는 Replica로 라우팅한다. Spring의 `AbstractRoutingDataSource`로 트랜잭션의 read-only 여부에 따라 DataSource를 동적으로 선택한다.

**이 시스템 적용 방식**:
- MariaDB Primary–Replica 복제 설정 (이미 운영 환경에서 일반적으로 구성됨)
- `RoutingDataSource extends AbstractRoutingDataSource` 구현: `@Transactional(readOnly = true)` 여부로 Replica/Primary 라우팅 결정
- 참석자 목록 조회 서비스 메서드에 `@Transactional(readOnly = true)` 적용 → Replica 라우팅
- 입장 write, 피드백 write는 기존 `@Transactional` 유지 → Primary 라우팅
- 기존 JPA 엔티티·레포지토리 코드 변경 없음, DataSource 설정만 추가

**장점**: 기존 도메인 모델을 재작성하지 않아 C-04(점진적 적용) 준수. Replica에서 참석자 조회를 처리하므로 Primary write lock 경합에서 조회가 분리된다. Primary DB의 write 처리 용량이 조회 부하에서 해방된다.

## 채택

**채택 대안**: 대안 3 — 경량 CQRS (Primary/Replica 라우팅 분리)

**채택 근거**: 대안 2(이벤트 소싱)는 도메인 모델 전면 재설계를 요구하여 C-04 위반이다. 대안 3은 `AbstractRoutingDataSource`와 `@Transactional(readOnly = true)` 조합으로 기존 코드 최소 변경으로 구현 가능하다. 대규모 회의 시작 시점에 참석자 목록 조회(read 집중)를 Replica로 분산하면, Primary DB의 write 처리(입장·피드백)가 조회 lock 경합에서 벗어나 DG-03·DG-04를 충족한다.

**적용 방향**:
- `DataSourceRouter extends AbstractRoutingDataSource`: 현재 트랜잭션의 `readOnly` 속성 조회 후 Primary/Replica DataSource 반환
- `LazyConnectionDataSourceProxy`로 래핑하여 실제 커넥션 획득을 트랜잭션 시작까지 지연 (readOnly 판단 이후 커넥션 선택)
- AS-09(Bulkhead)와 결합: Primary DataSource는 `join-pool`·`service-pool`, Replica DataSource는 `query-pool`로 별도 HikariCP 풀 설정
- `@Transactional(readOnly = true)` 적용 대상: 참석자 목록 조회, 대기실 상태 조회, 회의 목록 조회 등 모든 Query 서비스 메서드
