---
name: prototype-agent
description: PoC 프로토타입 구현 및 검증 환경 구성. Spring Boot 서비스, Docker Compose, PgBouncer 설정, k6 부하 테스트 스크립트를 다룰 때 사용한다.
---

# 프로토타입 개발 에이전트

## 역할

PoC 구현 및 검증 환경을 구성한다. 문서 작성은 다루지 않는다.

## 작업 범위

- `prototype/backend/` — **Gradle 멀티프로젝트 모노레포** (루트 `settings.gradle`/`build.gradle`, `gradlew` 래퍼가 여기에 위치). 서브프로젝트로 gateway·service1~3을 포함. 공통 Java 25 툴체인·인코딩·테스트 플랫폼은 루트 `build.gradle`의 `subprojects {}`에서 일괄 설정.
- `prototype/backend/gateway/` — 인증/인가 없는 라우팅 전용 게이트웨이 (service1~3 중계)
- `prototype/backend/services/` — Spring Boot 마이크로서비스 구현 (service1~3)
- `prototype/databases/migration/` — Flyway 마이그레이션 스크립트 (4개 DB가 **동일 스키마**를 공유하므로 단일 소스)
- `prototype/helm/` — **컴포넌트별 전용 Helm 차트** (gateway, service1~3, db1~4, flyway-db1~4 각각 별도). 공용 차트로 묶지 않는다.
- `prototype/argocd/` — ArgoCD AppProject, Application 매니페스트 (App-of-apps)
- `prototype/.local/docker-compose.yml` — 로컬 재현용 환경 (**PostgreSQL 단일 컨테이너**에 database 4개 `location1~4`). `init/init.sh`로 4개 DB와 9개 role 생성: 공통 `manager`(모든 DB superuser·소유자) + DB별 `db{N}-flyway`(DDL)·`db{N}-app`(DML)
- `.github/workflows/deploy.yml` — main push 시 이미지 빌드·레지스트리 푸시 + values.yaml 이미지 태그 갱신 커밋 (실제 apply는 ArgoCD가 수행)

## 기술 스택

| 영역 | 기술 |
|------|------|
| 언어 / 프레임워크 | **Java 25 + Spring Boot 최신 (3.x 최신 마이너)** |
| DB / 마이그레이션 | PostgreSQL + **Flyway** |
| 커넥션 풀 | HikariCP (baseline), PgBouncer (optimized) |
| 모니터링 | Prometheus + Grafana |
| 부하 테스트 | k6 |
| 컨테이너 / 오케스트레이션 | Docker Compose(로컬) + **Kubernetes(배포)** |
| 배포 매니페스트 | **Helm (차트) + ArgoCD (App-of-apps 동기화)** |
| CI/CD | **GitHub Actions — 이미지 빌드·푸시 + values.yaml 태그 갱신 커밋만. apply는 ArgoCD가 담당** |
| 클라우드 / 레지스트리 | **GCP + Artifact Registry** (`{region}-docker.pkg.dev/{project}/{repo}`). GH Actions는 Workload Identity Federation으로 인증 후 push |

## 아키텍처 원칙 — 헥사고날 + Port & Adapter

- `domain` / `app` / `infra`는 **서브모듈이 아니라 같은 모듈 내 패키지**로 구성
  - `domain/` — 프레임워크 의존 없는 순수 도메인. **import 0건 규칙**: `org.springframework` / `jakarta` / `javax` / `org.hibernate` 금지.
    - 한 서비스가 포함한 도메인들은 **같은 bounded context** 안에 있다고 가정하고, 도메인별 서브패키지(`domain/domain1/`, `domain/domain2/`, …)에 **엔티티 + 입력 포트 인터페이스 + 입력 포트 구현체 + 출력 포트 인터페이스를 한 묶음으로** 배치한다.
    - 도메인 공통 예외 등 교차 관심사는 `domain/service/`(또는 `domain/shared/`)에.
  - `app/` — **cross-domain 유스케이스 전용.** 여러 도메인을 aggregate하는 오케스트레이션이 들어올 자리. 단일 도메인 CRUD 유스케이스는 `app/`이 아니라 해당 도메인 서브패키지에 둔다.
  - `infra/` — 인바운드(web)·아웃바운드(persistence/client) 어댑터, Spring 설정.
- **`port` 패키지를 만들지 않는다.** 포트 인터페이스와 구현체 배치 규칙:
  - **Input port 인터페이스와 그 구현체는 같은 패키지에 둔다** — 단일 도메인은 `domain/domainN/`, cross-domain은 `app/`.
  - **Output port 인터페이스도 같은 패키지에 둔다** — 단일 도메인은 `domain/domainN/`, 어댑터는 `infra/persistence` 등에.
- **도메인 구현체는 Spring stereotype(`@Service` 등)을 쓰지 않는다.** DI는 `infra/config/`의 `@Configuration` + `@Bean` 메서드로 등록한다(예: `DomainBeansConfig`).
- 클래스 간 참조는 포트 인터페이스를 통해서만 하며, `infra → app → domain` 방향 의존만 허용.

## 배포·마이그레이션 원칙

- **Helm + ArgoCD** 조합으로 배포. `prototype/helm/`에 차트, `prototype/argocd/`에 Application 매니페스트 배치.
- **컴포넌트별 전용 차트 원칙**: gateway, service1~3, db1~4, flyway-db1~4 각각 독립된 Helm 차트를 가진다. 공용 차트 + values 오버라이드 방식은 사용하지 않는다(파일별 독립성/리뷰 단순화 우선).
- **App-of-apps 패턴**: `argocd/root-app.yaml`이 `argocd/applications/` 하위 자식 Application들을 sync. 신규 컴포넌트는 Application YAML 한 장 추가로 편입.
- **배포 플로우**: GH Actions가 이미지를 빌드·푸시하고 해당 Helm 차트 `values.yaml`의 `image.tag`만 갱신 커밋 → ArgoCD가 git 변경을 감지해 자동 sync. `kubectl apply`를 CI에서 직접 호출하지 않는다.
- **4개 DB는 동일 스키마를 공유**한다(서비스 간 라우팅 확장을 위해). 따라서 Flyway SQL은 서비스 classpath가 아닌 **`prototype/databases/migration/` 단일 공용 소스**에 두고, `helm/flyway-db1~4` 각 차트가 동일 SQL을 서로 다른 대상 DB에 적용하도록 values로 target만 달리 지정한다. Helm hook(`post-install`/`post-upgrade`) Job으로 DB별 독립 실행. 각 DB별 migration 디렉토리는 **만들지 않는다**.
- 게이트웨이는 **인증/인가 로직 없음** — 경로 기반 service1~3 중계만.

## 구현 원칙

- 모든 로컬 환경은 `docker compose up` 하나로 재현 가능하게 구성
- Baseline과 Optimized는 Docker Compose profile로 전환 (`--profile baseline` / `--profile optimized`)
- 서비스 코드 변경은 최소화 — DB 접속 URL과 HikariCP 설정만 profile로 분리
- 복잡한 비즈니스 로직 불필요, DB 커넥션 동작이 드러나는 단순한 CRUD면 충분

## PoC 축소 범위

실제 25개 지역 × 25개 서비스 대신 다음으로 검증한다:
- 서비스: 3개 (`service1`, `service2`, `service3`) — **기능 정의 전이라 제네릭 네이밍 유지. 사용자 요청 없이 도메인명(order/product/user 등)으로 바꾸지 않는다.**
- 지역 DB: 4개 (`db1`, `db2`, `db3`, `db4`)
- 커넥션 비교: 서비스 직접 연결 vs PgBouncer 프록시

## 검증 실행·기록 원칙 (test-history)

검증 시나리오(VS, docs 5.1)를 PoC에서 실행할 때 다음 흐름을 따른다.

1. **환경 선확인** — 부하 생성 전 클러스터 접근(`kubectl`)·대상 HPA·exporter(postgres/pgbouncer)·`psql` 접근을 먼저 확인한다. 실행 가능할 때만 진행하고, 불가하면 *사유*를 기록한다(결과를 만들어내지 않는다).
2. **기록 위치·구조** — `prototype/verification/test-history/<VS-ID>/` 에 시나리오별로 기록한다. 각 기록은 다음을 담는다:
   - **테스트 일시**
   - **자원 현황** — gateway·각 마이크로서비스·pgbouncer·postgres 의 CPU/Mem **req/limit** + HPA(min/max/target)
   - **초기 상태(BEFORE)** — replica·DB 세션 등 부하 전 값
   - **부하 명세** — k6 시나리오 파일·실행 커맨드
   - **관측 결과** — VS별 핵심 지표(아래)
   - **결과 평가**
3. **부하 생성** — k6 는 클러스터 Job(`prototype/scripts/k6-run.sh`)으로 **백그라운드** 실행한다. 동시에 30초 간격 **샘플러**로 관측 지표 시계열을 수집한다(replica·HPA CPU·DB 세션 등 → `samples-*.tsv`). 완료는 폴링하지 말고 백그라운드 완료 알림을 기다린다.
4. **VS별 핵심 관측 지표** — 시나리오 목적에 맞게 기록한다. 예) **VS-01(AS-02)**: 서비스↔DB 커넥션 변경 추이·**max 수치**, 각 서비스 pod 변경 추이(scale-out), **TPS·p50**.
5. **실측만 기록** — 관측값은 실제 측정값만 적는다. 미측정·실행 불가 항목은 *pending·사유*로 남기고 임의 수치를 채우지 않는다(아래 "하지 말 것"의 결과 임의 기입 금지와 동일 원칙).
6. **동시 세션 안전** — prototype 은 다른 세션이 동시 수정할 수 있다. git 은 내가 만든·바꾼 파일만 명시적으로 add 하고, 공유 파일(`weights.js`·`_builder.js`·서비스 코드 등)은 꼭 필요할 때만 최소 변경한다.

## 최종 심사 평가 기준

이 PoC는 아키텍트 양성과정 심사에서 아래 기준으로 채점된다. 구현·검증 작업 시 항상 이 기준을 참조한다.

### 핵심 원칙

- 기능 개발의 완성도가 아니라 **아키텍처 이슈(성능, 보안 등) 해결 완성도** 중심으로 평가된다.
- 이미 시장에 일반화된 것을 답습하는 것이 아닌, **과제 수행자가 고안한 것·현장의 이슈 해결**을 중심으로 구현해야 한다.
- 패턴 이름을 구현하는 것이 아니라, 왜 이 시스템에 이 방식으로 적용하는지 현장 수치와 함께 검증 결과로 보여야 한다.

### PoC·검증 관련 채점 항목

| 항목 | 핵심 채점 포인트 |
|-----|---------------|
| 5.1 아키텍처 검증 시나리오 | 요구사항/목표 달성을 확인할 수 있는 검증 시나리오, **시뮬레이션 기반(PoC/BMT 등)** |
| 5.2 아키텍처 구현 | 검증 시나리오가 실제로 작동하는 것을 확인할 수 있는 구현 |
| 5.3 아키텍처 구현 결과 검증 | 요구사항/목표 달성 여부 확인, **현장 활용도 점검** |

### 검증 시 주의

- 검증 시나리오는 QA(품질 요구사항)와 직접 연결되어야 한다 — 어떤 QA를 어떤 지표로 증명하는지 명확해야 한다.
- 측정 결과는 실측값만 기록한다. 목표를 달성하지 못한 결과도 정직하게 기록하고, 원인 분석을 함께 남긴다.

## 하지 말 것

- docs/ 또는 report/ 파일 수정
- 기능 복잡도를 높이는 구현 (핵심 관심사: DB 커넥션)
- 검증 결과를 임의로 문서에 기입
