# 프로토타입 검토 · 검증 진행 상황

기준일: 2026-07-05
작성 목적: 프로토타입 정밀 검토 결과와 로컬 검증 시도 중단 지점을 기록하여, 다음 세션에서 이어서 진행하기 위한 핸드오프 노트.

## 1. 한 줄 요약

정적 검토 완료. 이후 **미구현/부분 구현이던 AS-01·AS-02·AS-06을 채택안대로 코드 구현**했다(2026-07-13, 6절 참조). 다만 로컬 toolchain·Docker 미가용으로 **컴파일·실행 검증은 아직 못 함**(정적 정합만 확인). 실측 결과도 0건(`k6/results/`에 `.gitkeep`만).

## 2. AS-01~09 구현 상태

| AS | 상태 | 근거 파일 / 메모 |
|----|------|----------------|
| AS-01 도메인 경계(ArchUnit) | 구현(2026-07-13) | `src/test/.../architecture/DomainBoundaryArchTest.java` 신규 + `build.gradle` archunit 의존성. 슬라이스 순환·연계 인터페이스 참조·역의존·계층 규칙 4종. 컴파일/통과 미검증 |
| AS-02 @Async externalCallExecutor | 구현(2026-07-13) | MM 호출 경로 비동기화: `MeetingJoinService.getConferenceToken`·`MeetingService.startMeeting` `@Async("externalCallExecutor")`+`CompletableFuture`, 컨트롤러 `CompletableFuture<ResponseEntity>`. `ContextPropagatingTaskDecorator`(MDC 전파) 부착. 컴파일 미검증 |
| AS-03 L1 Caffeine + L2 Redis | 구현 | `config/CacheConfig.java`, `domain/auth/AuthService.java` |
| AS-04 입장 전용 Connector(8081) | 구현 | `config/EntryConnectorConfig.java`, `nginx/nginx.conf` |
| AS-05 Pre-warming(preWarmExecutor) | 구현 | `scheduler/PreWarmingScheduler.java`. 라벨을 AS-05로 정정, `PeakDetector` 공유 주입. (`k6/iv03` 주석의 "AS-06" 표기는 공유 검증 파일이라 미변경) |
| AS-06 Throttling | 구현(2026-07-13) | `config/ThrottlingConfig`·`ThrottlingInterceptor`·`PeakDetector`·`ThrottleExempt` 신규 + Bucket4j 의존성 + `application.yml` throttling. 핵심 3개 API `@ThrottleExempt`. 컴파일/동작 미검증 |
| AS-07 CQRS 라우팅 | 구현 | `RoutingDataSource`+`DataSourceRoutingAspect`+`DataSourceContextHolder`+`DataSourceType` |
| AS-08 Hikari Bulkhead | 구현 | `config/DataSourceConfig.java` (Join100/Service40/General60/Query80) |
| AS-09 Resilience4j CB 차등 | 구현 | 3개 어댑터 `@CircuitBreaker`+인라인 fallback, `application.yml` 인스턴스별 임계 |

기타 정적 검토 메모:
- AS-09 외부 호출은 Feign으로 구현(2026-07-15 RestTemplate→Feign 전환, C-01 스택 유지). 각 `integration.*`에 `@FeignClient`(MeetingManagerClient·AcServerClient·CopilotClient), Adapter가 위임 + `@CircuitBreaker`·fallback. `WebClientConfig` 제거, `@EnableFeignClients`(앱) + `feign.client.config`(yml). build.gradle에 spring-cloud-starter-openfeign(BOM 2023.0.3). 컴파일 미검증.
- resilience4j `timelimiter`는 동기 Feign 호출에선 무효(실제 타임아웃은 `feign.client.config` read 3s).
- 자동화 테스트: `FrontApiApplicationTest.contextLoads`(인프라 필요) + `DomainBoundaryArchTest`(ArchUnit, 인프라 불필요, 미실행).

## 3. 검증 환경 상태

- docker-compose: primary/replica/redis/stub-server/front-api/nginx + monitoring(prometheus/grafana) 구성 완비.
- k6 IV-01~05: 5종 모두 존재, 엔드포인트·시드데이터 일치 확인.
- stub-server: 장애 주입 API(`/stub/{server}/fault`) + 3개 외부서버 stub 완비.
- Grafana: datasource만 프로비저닝, 대시보드 JSON 없음(수작업 필요).

## 4. 확인된 블로커

1. (환경) Docker 데몬 미기동 — 현재 최우선 블로커
   - `/var/run/docker.sock` 없음, `~/.rd/docker.sock` 없음, `DOCKER_HOST` 미설정, context=default.
   - Rancher Desktop 실행 + Container Engine=dockerd(moby) + WSL Integration(현 배포판) ON 필요. 변경 후 재시작.
   - 확인: `docker version` / `docker ps` / `ls -la /var/run/docker.sock`.
   - Rancher가 `~/.rd/docker.sock`에만 노출하면: `export DOCKER_HOST=unix://$HOME/.rd/docker.sock`.

2. (구성 의심, 미확인) replica 읽기 경로
   - compose가 공식 `mariadb:10.11` 이미지에 Bitnami 규약 변수(`MARIADB_REPLICATION_MODE`/`MARIADB_MASTER_HOST`)를 사용 → 공식 이미지는 무시. init 스크립트는 primary에만 마운트.
   - 예상 증상: replica의 `meetingdb`에 테이블 없음 → QueryPool 조회(`/meetings/{id}`, `/participants/status`) 500 → IV-02·IV-05 및 read 경로 검증 불성립.
   - 스택 기동 즉시 진단(부하 테스트보다 먼저):
     ```
     docker compose exec mariadb-replica mariadb -uapp -papppass -e "SHOW TABLES IN meetingdb;"
     docker compose exec mariadb-replica mariadb -uroot -prootpass -e "SHOW SLAVE STATUS\G"
     curl http://localhost/meetings/1
     ```
   - 정상(테이블 있음 + Slave_IO_Running: Yes + JSON 응답)이면 진단 오류로 정정. 아니면 replica 구성 수정이 IV-02/05 선행 조건.

## 5. 로컬 검증 재개 절차 (resume checklist)

전제: 로컬에 JDK17/Gradle 없음. Docker로 빌드하는 경로 사용.

1. Docker 데몬 복구(4-1) → `docker ps` 정상 확인.
2. jar 빌드(설치 불필요):
   ```
   cd prototype
   docker run --rm -v "$PWD/front-api":/app -w /app gradle:8.10.2-jdk17 gradle clean build -x test
   docker run --rm -v "$PWD/stub-server":/app -w /app gradle:8.10.2-jdk17 gradle clean build -x test
   ```
3. 스택 기동: `docker compose --profile monitoring up -d --build`
4. 헬스: `docker compose ps` / `curl http://localhost/health` / `curl http://localhost:8080/actuator/health`
5. replica 진단(4-2) — 부하 전 필수.
6. 스모크 테스트 후 k6 IV-01~05 실행. Pre-warming은 1~2분 후 캐시 적재되므로 IV-03은 그 뒤 실행.
7. 결과 저장: `k6/results/ivXX-summary.json` + Grafana 캡처. 실측만 기록.

대안: WSL에 SDKMAN으로 java17+gradle 설치, 또는 Dockerfile 멀티스테이지화(파일 수정 필요, 사용자 승인 후).

## 6. 구현 변경 이력 (2026-07-13) — 채택안대로 코드 구현

방침: 구현 대상과 검증 대상을 분리. 미구현/부분이던 AS를 채택 설계(리포트 3.2·4.2.2.5)대로 코드 구현하되, 실행 검증은 Docker 복구 후로 분리한다. 문서/리포트는 미변경(docs·report 에이전트 담당).

- AS-06 Throttling (신규 구현, 후보3 피크 예측 차등 스로틀링)
  - `config/ThrottleExempt.java`(면제 어노테이션), `config/PeakDetector.java`(고정 시간창 08:30~09:30·12:30~13:30 자동 판정 + setActive 수동 오버라이드), `config/ThrottlingInterceptor.java`(피크+비핵심 한정 Bucket4j 초당 1,000 상한, 초과 시 429+Retry-After), `config/ThrottlingConfig.java`(인터셉터 등록).
  - `build.gradle`에 `com.bucket4j:bucket4j-core:8.10.1`, `application.yml`에 `throttling.*`.
  - 핵심 API 면제: `MeetingJoinController.joinMeeting`·`getConferenceToken`, `MeetingController.startMeeting`에 `@ThrottleExempt`.
- AS-02 @Async externalCallExecutor (배선, 후보3 하이브리드)
  - MM 외부 호출 경로만 선택 비동기화: `MeetingJoinService.getConferenceToken`·`MeetingService.startMeeting`을 `@Async("externalCallExecutor")`+`CompletableFuture` 반환으로, 컨트롤러를 `CompletableFuture<ResponseEntity>`로 전환(서블릿 스레드 즉시 반환).
  - `config/ContextPropagatingTaskDecorator.java`(MDC 전파) 추가 후 externalCallExecutor에 부착. (트랜잭션은 비동기 메서드의 @Transactional이 워커 스레드에서 새로 시작, 보안 컨텍스트는 인증 부재로 대상 아님.)
  - CB는 어댑터(동기)에 그대로 유지 → 워커 스레드에서 동기 호출되어 CB+async 충돌 없음.
- AS-01 도메인 경계(ArchUnit) (신규 구현)
  - `src/test/.../architecture/DomainBoundaryArchTest.java`: 도메인 슬라이스 순환 금지, 도메인→연계 인터페이스 참조만 허용, 연계→도메인 역의존 금지, 서비스→컨트롤러 참조 금지.
  - `build.gradle`에 `com.tngtech.archunit:archunit-junit5:1.3.0`.
- AS-05 라벨 정정: `PreWarmingScheduler` 헤더 AS-06→AS-05, `PeakDetector` 공유 주입(피크 감지 공유 로깅). `k6/iv03` 주석의 "AS-06" 표기는 공유 검증 파일이라 미변경(추후 검증 정리 시 반영).

미검증 항목(컴파일·실행 못 함, 정적 정합만 확인):
- toolchain(JDK17/Gradle) 없음 + Docker 데몬 미기동 → `gradle build` 미실행. 4-2절 Docker 복구 후 `docker run ... gradle build`로 컴파일 확인 필요.
- 확인 우선순위: (1) 컴파일, (2) `DomainBoundaryArchTest` 통과(인프라 불필요), (3) 컨텍스트 로딩, (4) 런타임 동작.
- 외부 라이브러리 좌표·API 가정: Bucket4j 8.10.1(`Bandwidth.builder().capacity().refillGreedy()`), ArchUnit 1.3.0. 버전 미존재/ API 상이 시 조정 필요.

## 7. 레포트 작성 가능 범위 (현재 시점)

- 작성 가능: 검증 시나리오 설계(5.1), 검증 환경·구현 서술(5.2). 프로토타입 코드·compose·k6·stub 실재.
- 작성 불가(현 시점): 실측 결과·판정(5.3). 실행 이력 0건 → 수치·그래프 없음. 임의 기입 금지.
- 문서 동기화 완료(2026-07-13): 이번 구현을 docs·report 4·5장에 반영 완료. AS-06 "미구현/구현 예정" 전량 제거(표 61 △=구현·전용IV 미실행), `ThrottlingFilter→ThrottlingInterceptor` 명칭 통일, AS-02 "MM 경로 배선"·AS-01 ArchUnit 비고 반영, docs↔report 표61/62 동기(AS-07 O·IV-05, IV-04→QA-05·IV-05→QA-04). "구현 완료 ≠ 검증 완료"는 △·"전용 부하 IV 미실행"으로 구분 표기. 상세는 루트 `HANDOFF.md` 참조.
- 잔여 한계: replica read 경로 미검증(IV-02/05 결과 pending), 신규 구현분 컴파일·실행 미검증, 실측 5.2/5.3 미작성(`k6/results/` 공백), 다이어그램 PNG 재렌더 미반영.
