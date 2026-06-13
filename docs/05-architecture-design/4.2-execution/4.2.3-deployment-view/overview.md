# 4.2.3. 배치 뷰 (Deployment View)

배치 뷰는 front-api가 운영 환경에서 어떤 하드웨어·소프트웨어 구성으로 배포되는지를 기술한다. 각 인프라 컴포넌트가 AS 전략과 어떻게 대응되는지를 함께 명시한다.

---

## 인프라 토폴로지

```mermaid
flowchart TD
    CLIENT["클라이언트\n(미팅 웹포탈 · 모바일 앱)"]

    subgraph INFRA["운영 인프라"]
        direction TB

        L4["L4 Load Balancer\n(IP Hash 또는 Round Robin)"]

        subgraph NGINX_TIER["Nginx 계층  — AS-04 URL 패턴 라우팅"]
            NGINX["Nginx\n/join · /conference-token → 8081\n그 외 → 8080"]
        end

        subgraph APP_TIER["front-api (Spring Boot + Tomcat)  — 수평 확장 인스턴스"]
            direction LR
            C8081["Tomcat Connector\nport 8081\n입장 전용  AS-04\nmaxThreads=200\nminSpareThreads=50"]
            C8080["Tomcat Connector\nport 8080\n일반  AS-04\nmaxThreads=300"]

            subgraph FILTER["AS-05 ThrottlingFilter"]
                TF["ThrottlingInterceptor\n(port 8080 경로만 적용)"]
            end

            subgraph DOMAIN_LAYER["domain.*  AS-01"]
                DE["domain.entry"]
                DA["domain.auth"]
                DM["domain.meeting"]
            end

            subgraph INTEGRATION_LAYER["integration.*  AS-10 ACL"]
                MM_INT["integration.meetingManager\nAS-09 CB"]
                AC_INT["integration.ac\nAS-09 CB"]
                CP_INT["integration.copilot\nAS-09 CB"]
            end

            ASYNC["externalCallExecutor\nAS-02 @Async\ncorePoolSize=100 · maxPoolSize=500"]

            SCH["PreWarmingScheduler\nAS-06\n(front-api 내장, 1분 주기)"]
        end

        subgraph CACHE_TIER["캐시 계층  AS-03"]
            direction LR
            L1_C["L1 Caffeine\n인스턴스 로컬\nTTL 5분"]
            REDIS["Redis\nL2 분산 공유\nTTL 30분~1시간"]
        end

        subgraph POOL_TIER["HikariCP Bulkhead  AS-08"]
            direction LR
            JP["join-pool\n100 conn  AS-08"]
            SP["service-pool\n40 conn  AS-08"]
            GP["general-pool\n60 conn  AS-08"]
            QP["query-pool\n80 conn  AS-07 AS-08"]
        end

        subgraph DB_TIER["MariaDB  AS-07 CQRS"]
            direction LR
            PRI["Primary\nWrite 전담"]
            REP["Replica\nRead 전담"]
        end
    end

    subgraph EXT_TIER["외부 서버 (외부 의존)"]
        direction LR
        MM["Meeting Manager"]
        ACS["AC서버"]
        CPS["Copilot Admin"]
    end

    %% 요청 흐름
    CLIENT --> L4
    L4 --> NGINX
    NGINX -->|"/join · /conference-token\nport 8081"| C8081
    NGINX -->|"그 외 API\nport 8080"| C8080
    C8080 --> TF
    TF --> DA & DM
    C8081 --> DE

    %% domain → integration
    DE --> MM_INT
    DM --> AC_INT
    DA --> AC_INT & CP_INT

    %% async
    MM_INT & AC_INT & CP_INT --> ASYNC
    ASYNC --> MM & ACS & CPS

    %% 캐시
    DA --> L1_C
    L1_C -->|miss| REDIS
    SCH --> REDIS

    %% 커넥션 풀
    DE --> JP
    DM --> SP
    DA --> GP
    DM -->|readOnly| QP

    %% DB
    JP & SP & GP --> PRI
    QP --> REP

    style C8081 fill:#d4edda,color:#000
    style JP fill:#d4edda,color:#000
    style REDIS fill:#cce5ff,color:#000
```

---

## 컴포넌트별 설정 요약

### Nginx 라우팅 설정

| 패턴 | 라우팅 대상 | 비고 |
|-----|-----------|------|
| `/meetings/*/join` | front-api:8081 | AS-04: 입장 전용 Connector |
| `/meetings/*/conference-token` | front-api:8081 | AS-04: 입장 전용 Connector |
| 그 외 모든 경로 | front-api:8080 | AS-04: 일반 Connector |

### Tomcat Connector 설정 (AS-04)

| Connector | 포트 | maxThreads | minSpareThreads | 용도 |
|---------|-----|-----------|----------------|------|
| 입장 전용 | 8081 | 200 | 50 | /join, /conference-token 전용 |
| 일반 | 8080 | 300 | 기본값 | 조회·권한 갱신·관리 |

### AsyncTaskExecutor 설정 (AS-02)

| Bean | corePoolSize | maxPoolSize | queueCapacity | 용도 |
|-----|------------|------------|--------------|------|
| `externalCallExecutor` | 100 | 500 | 2,000 | 외부 서버 Feign 호출 전담 |
| `preWarmExecutor` | 10 | 50 | 1,000 | Pre-warming 전담 (저우선순위) |

### HikariCP 커넥션 풀 구성 (AS-08)

| 풀 이름 | 대상 DataSource | maximumPoolSize | connectionTimeout | 용도 |
|--------|--------------|----------------|-----------------|------|
| join-pool | joinDataSource (Primary) | 100 | 3,000ms | 입장 처리 전용 |
| service-pool | serviceDataSource (Primary) | 40 | 5,000ms | 회의 시작·초대 |
| general-pool | generalDataSource (Primary) | 60 | 5,000ms | 권한 갱신·일반 조회 |
| query-pool | queryDataSource (Replica) | 80 | 3,000ms | Read 전용 (AS-07 CQRS) |

### 캐시 계층 구성 (AS-03)

| 계층 | 구현체 | TTL | 범위 | 비고 |
|-----|------|-----|-----|------|
| L1 | Caffeine | 5분 | 인스턴스 로컬 | front-api 인스턴스마다 독립 |
| L2 | Redis | AC 권한 1시간 / LLM·용어사전 권한 30분 | 분산 공유 | 다중 인스턴스 간 공유 |

### MariaDB 구성 (AS-07)

| 노드 | 역할 | 라우팅 조건 | 연결 풀 |
|-----|-----|-----------|--------|
| Primary | Write 전담 | `@Transactional(readOnly=false)` | join-pool · service-pool · general-pool |
| Replica | Read 전담 | `@Transactional(readOnly=true)` | query-pool |
