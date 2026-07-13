# 미팅 포털 프로토타입 — 검증 환경 구성 및 실행 가이드

이 문서는 프로토타입을 **로컬에서 직접 실행하고 부하 검증(IV-01~IV-05)을 수행**하기 위한 절차다.
설계 문서(4장)의 설계 전략(AS)이 실제로 품질 요구사항(QA)을 충족하는지 확인하고, 5장(검증)에 넣을 결과·그래프를 확보하는 것이 목표다.

> 실행·캡처는 **사용자 로컬 환경(Windows + WSL2 + Docker Desktop)**에서 수행한다.
> k6 부하 결과(터미널 summary)와 Grafana 그래프 캡처를 확보하면 5장 문서에 반영한다.

---

## 0. 검증 대상 요약

| IV | 검증 내용 | 관련 AS | 판정 기준 |
|----|---------|--------|---------|
| IV-01 | 동시 입장 집중 정상 처리 | AS-02·04·08 | 커넥션 풀 사용률 ≤ 80%, 응답 p95 목표 이내 |
| IV-02 | join-pool 고갈 시 타 기능 격리 | AS-08 | 조회 성공률 ≥ 99.9% |
| IV-03 | 권한 캐시 hit | AS-03·06 | 캐시 hit 시 응답 단축, 외부 호출 감소 |
| IV-04 | 외부 서버 장애 격리 | AS-09 | CB Open 후 fallback, 무관 기능 정상 |
| IV-05 | 참석자 상태 write/read 분리 | AS-07 | write 집중 중 상태 조회 성공률 ≥ 99.9% |

---

## 1. 사전 설치

| 도구 | 용도 | 설치 |
|------|------|------|
| **Docker Desktop** (WSL2 backend) | 컨테이너 인프라 기동 | https://www.docker.com/products/docker-desktop — 설정에서 WSL2 integration 활성화 |
| **JDK 17** (Temurin 권장) | front-api·stub-server 빌드 | `winget install EclipseAdoptium.Temurin.17.JDK` 또는 IntelliJ 내장 JDK 사용 |
| **Gradle** | jar 빌드 (gradlew 래퍼 없음) | `winget install Gradle.Gradle` 또는 **IntelliJ의 Gradle 사용**(별도 설치 불필요) |
| **k6** | 부하 테스트 | `winget install k6` 또는 `choco install k6` — https://k6.io/docs/get-started/installation |

> **참고**: 현재 Dockerfile은 미리 빌드된 jar(`build/libs/*.jar`)를 복사하는 방식이라, **컨테이너 기동 전에 로컬에서 jar를 빌드**해야 한다(2단계). Docker만으로 빌드까지 자동화하려면 Dockerfile을 multi-stage로 바꾸면 되며, 원하면 그렇게 수정해 줄 수 있다(로컬 JDK/Gradle 불필요해짐).

---

## 2. 애플리케이션 빌드 (jar 생성)

`front-api`와 `stub-server` 각각 jar를 만든다. (WSL 터미널 기준, 프로젝트 루트 = `prototype/`)

```bash
# front-api 빌드
cd front-api
gradle clean build -x test          # 또는 IntelliJ Gradle 창 → build/build
cd ..

# stub-server 빌드
cd stub-server
gradle clean build -x test
cd ..
```

빌드 후 `front-api/build/libs/*.jar`, `stub-server/build/libs/*.jar`가 생성됐는지 확인한다.

> IntelliJ를 쓰면: 우측 Gradle 패널 → `front-api > Tasks > build > build` 실행(테스트 제외하려면 Run/Debug 설정 또는 `-x test`).

---

## 3. 인프라 기동 (Docker Compose)

```bash
# prototype/ 디렉토리에서
docker compose --profile monitoring up -d --build
```

기동되는 컨테이너:

| 서비스 | 포트 | 역할 |
|--------|------|------|
| mariadb-primary | 3306 | Primary (write) |
| mariadb-replica | 3307 | Replica (read, QueryPool) |
| redis | 6379 | L2 캐시 |
| stub-server | 8090 | 외부 서버(Meeting Manager·AC·Copilot) Mock |
| front-api | 8080·8081 | 본체 (8081=입장 전용 Connector) |
| nginx | 80 | AS-04 URL 라우팅 (`/join`·`/conference-token` → 8081, 그 외 → 8080) |
| prometheus | 9090 | 메트릭 수집 |
| grafana | 3000 | 대시보드 (캡처 대상) |

**헬스체크 (모두 UP 될 때까지 대기, 최초 1~2분):**

```bash
docker compose ps                                   # State=healthy 확인
curl http://localhost/health                        # nginx → OK
curl http://localhost:8080/actuator/health          # front-api → {"status":"UP"}
```

DB 초기 데이터(`db/init/01-schema.sql`·`02-testdata.sql`)는 primary 최초 기동 시 자동 적재된다(회의 100건, 권한 1000명, 참석자 등).

---

## 4. 기능 스모크 테스트 (부하 전 정상 동작 확인)

`http/` 디렉토리의 `.http` 파일을 **IntelliJ HTTP Client**로 열어 순서대로 실행하거나, curl로 확인한다.

```bash
# 회의 조회 (QueryPool/Replica)
curl http://localhost/meetings/1

# 입장 (JoinPool, 8081로 라우팅)
curl -X POST http://localhost/meetings/1/join -H 'Content-Type: application/json' -d '{"email":"smoke@example.com"}'

# 참석자 상태 피드백 (ISSUE-07, write) + 조회 (read/Replica)
curl -X POST http://localhost/meetings/1/feedback/entrance -H 'Content-Type: application/json' -d '{"email":"participant1@example.com"}'
curl http://localhost/meetings/1/participants/status
```

- `http/meeting.http` — 회의·참석자 상태 API (①~⑲)
- `http/join.http` — 입장·토큰
- `http/auth.http` — 권한 갱신(캐시)
- `http/stub.http` — 외부 서버 장애 주입(IV-04용)

---

## 5. 부하 검증 실행 (k6)

각 스크립트는 `k6/` 아래에 있다. 프로토타입 루트에서 실행:

```bash
# IV-01: 동시 입장 부하
k6 run --env BASE_URL=http://localhost k6/iv01-join-load.js

# IV-02: 풀 격리
k6 run --env BASE_URL=http://localhost k6/iv02-pool-isolation.js

# IV-03: 캐시 hit
k6 run --env BASE_URL=http://localhost k6/iv03-cache-hit.js

# IV-04: 서킷 브레이커 — 먼저 장애 주입 후 실행 (stub 경로: POST/DELETE /stub/{server}/fault)
curl -X POST http://localhost:8090/stub/ac/fault -H 'Content-Type: application/json' -d '{"errorMode":true}'   # AC서버 장애 ON
k6 run --env BASE_URL=http://localhost k6/iv04-circuit-breaker.js
curl -X DELETE http://localhost:8090/stub/ac/fault             # 검증 후 정상 복구
# (server 값: meeting-manager · ac · copilot / delayMs 지정 시 지연 장애: -d '{"delayMs":5000}')

# IV-05: 참석자 상태 write 집중 시 read 분리 (ISSUE-07/AS-07)
k6 run --env BASE_URL=http://localhost k6/iv05-participant-cqrs.js
```

각 실행이 끝나면 터미널에 **결과 summary**(성공률·응답시간·판정)가 출력되고, `k6/results/ivXX-summary.json`에 저장된다.

> **리소스 주의**: 스크립트는 로컬에서 실행 가능하도록 최대 수백 VU 규모로 설정돼 있다(예: IV-01은 ramping-vus 최대 500). 실서비스의 8만 동시 입장을 그대로 재현하는 것이 아니라, 그 규모를 **대표하는 부하로 설계 동작(풀 격리·캐시 hit·CB 전이·write/read 분리)을 확인**하는 것이 목적이다. 로컬 사양이 넉넉하면 `options.scenarios`의 `vus`/`target`을 키워도 되고, 부족하면 낮춘다.

---

## 6. 모니터링 그래프 캡처 (5장용)

부하 실행 중 또는 직후, 브라우저에서 캡처한다.

**Grafana**: http://localhost:3000  (admin / admin)
**Prometheus**: http://localhost:9090  (쿼리 직접 확인용)

캡처 대상 (IV별):

| IV | k6 터미널 summary | Grafana / 메트릭 그래프 |
|----|------------------|----------------------|
| IV-01 | 입장 성공률·p95 응답 | HikariCP JoinPool 사용률(≤80% 확인), 응답시간 추이 |
| IV-02 | 조회 성공률(100%) | JoinPool 포화 vs QueryPool 정상(풀별 active connections) |
| IV-03 | 캐시 hit율·응답 단축 | 캐시 hit/miss, 외부 호출 감소 |
| IV-04 | CB 동작 후 무관 기능 성공률 | resilience4j CB 상태(Closed→Open→Half-Open), fallback 비율 |
| IV-05 | 상태 조회 성공률(≥99.9%) | ServicePool(write 집중) vs QueryPool(read) 분리, 조회 p95 |

> Grafana 메트릭이 비어 보이면: Prometheus가 `front-api:8080/actuator/prometheus`를 스크레이프하는지(타깃 UP) 9090에서 확인. front-api actuator prometheus 노출이 켜져 있어야 한다.

---

## 7. 정리

```bash
docker compose --profile monitoring down          # 컨테이너 중지
docker compose --profile monitoring down -v       # 볼륨(DB 데이터)까지 삭제 → 완전 초기화
```

---

## 8. 트러블슈팅

| 증상 | 원인 / 조치 |
|------|-----------|
| `front-api` 헬스체크 실패 | jar 미빌드(2단계) 또는 DB 미기동. `docker compose logs front-api` 확인 |
| replica 연결 실패 | primary가 healthy 된 뒤 replica가 뜨는지(`depends_on`) 확인, 최초 40초 대기 |
| `COPY build/libs/*.jar` 오류 | 2단계 빌드 누락. `gradle build` 후 `docker compose build front-api` |
| k6 `connection refused` | nginx(80) 또는 front-api 미기동. `curl http://localhost/health` 먼저 확인 |
| Grafana 그래프 빈 값 | Prometheus 타깃(9090 → Status/Targets) UP 여부, actuator prometheus 엔드포인트 확인 |
| 대규모 VU에서 로컬 멈춤 | 스크립트 `vus` 하향 조정 |
