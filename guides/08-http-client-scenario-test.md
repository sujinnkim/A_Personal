# IntelliJ HTTP Client 로 시나리오 테스트

`prototype/test/` 의 .http 파일은 gateway 를 경유해 각 행정 서비스의 happy-path
를 위에서 아래로 검증하는 시나리오다. 0번 health 부터 12번 stats 까지 순서대로
실행하면 *한 사용자(=한 location)* 의 신청 → 심사 → 지급 → 통계까지 한 번에
흐른다.

## 사전 조건

- 가이드 05·06 으로 GKE 클러스터가 떠 있고 `kubectl get pods` 가 gateway / auth /
  common-data / fuel-subsidy / child-care / disaster-relief / senior-pension /
  postgres / pgbouncer-location{1,2,3} 모두 `Running`
- `prototype/databases/data/seed-auth.sql` 의 seed 가 auth DB 에 인입된 상태
  (Session 12개 토큰)
- IntelliJ IDEA Ultimate (HTTP Client 내장) 또는 VS Code + REST Client 확장

## 사용 가능 시나리오

| 파일 | 시나리오 단계 |
|---|---|
| `prototype/test/fuel-subsidy.http` | DIESEL 유가 보조: carrier → vehicle → claim → audit → award → transfer |
| `prototype/test/child-care.http` | TODDLER 보육 지원: guardian → child → enrollment → screening → authorization → payout |
| `prototype/test/disaster-relief.http` | 재해 구호: victim → property → claim → assessment → award → settlement |
| `prototype/test/senior-pension.http` | 노령연금: (동일 패턴) |
| `prototype/test/common-data.http` | AS-06 단일 원천(정책 단가 마스터): create → get/by-code → update → delete (CRUD only, 다른 4개와 다른 패턴) |

각 .http 는 step 0~12 로 구성되며 step 2~7 사이에서 ID 들이 IntelliJ `client.global`
에 캡처되어 다음 step 에서 자동 참조된다 → **위에서 아래로 순서대로 실행 필수**.

---

## 1. Gateway port-forward — [매 세션 1회]

gateway 만 ClusterIP 로 노출돼 있어 로컬에서 호출하려면 port-forward 가 필요하다.

### 방법 A. IntelliJ Kubernetes 플러그인

(가이드 07 의 플러그인 + 클러스터 등록이 끝나 있다면)

1. `Services` 툴 윈도우 → `Kubernetes → <컨텍스트> → Namespaces → default → Services → gateway` 우클릭
2. **`Forward Ports...`** → Local port `8080`, Remote port `8080` → OK

forward 상태는 같은 트리에서 확인. IDE 종료 시 자동 해제.

### 방법 B. kubectl CLI (별도 터미널)

```bash
kubectl port-forward svc/gateway 8080:8080
# WSL 호스트(Windows 브라우저·IntelliJ) 에서도 접근하려면 모든 인터페이스 바인딩:
kubectl port-forward --address 0.0.0.0 svc/gateway 8080:8080
```

확인:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## 2. 세션(=사용자) 선택

`prototype/test/http-client.env.json` 에 12개 세션이 정의돼 있다. 각 세션은
`token`(UUID, seed-auth.sql 에서 인입) 과 `location`(LOCATION1~3) 한 쌍을 가진다.

| location | 사용자 |
|---|---|
| LOCATION1 | alice / bob / carol / dave |
| LOCATION2 | erin / frank / grace / henry |
| LOCATION3 | ivy / jack / kate / liam |

다른 location 사용자로 같은 시나리오를 돌리면 *다른 location DB* 에 격리되어
데이터가 분리된다. AS-08 지역 데이터 경계 검증의 기초 시나리오로 활용 가능.

### IntelliJ HTTP Client

.http 파일 우측 상단의 **env-selector 드롭다운** ("No environment" 라고 표시된
부분 클릭) → 사용자 이름 선택. 같은 워크스페이스의 모든 .http 파일에 자동 적용.

### VS Code REST Client

`Ctrl/Cmd+Alt+E` → 환경 이름 선택. 또는 `settings.json` 의
`rest-client.environmentVariables` 에 동일 구조를 복사.

---

## 3. 시나리오 실행

원하는 .http 파일을 열고 각 `### N. ...` 블록 왼쪽 ▶ 마크를 위에서 아래로 순서대로
클릭.

- step 0(health) 만 빼고 모두 `Authorization: Bearer {{token}}` 필요 — env 가
  선택돼야 200 응답
- step 2(rate 등록) → step 7(award/authorization) 사이에서 `fuelRateId`·
  `carrierId`·`vehicleId`·`claimId` 등이 `client.global` 에 캡처되어 다음 step
  에서 `{{...}}` 로 자동 참조
- step 1, 11 의 dashboard counter 가 시나리오 실행 전후로 1씩 증가해야 정상

### 한 번에 일괄 실행

IntelliJ HTTP Client 의 **Run with...** → `Run All Requests in File`. 단 캡처
의존이 있어 step 간 대기 없이 일괄 실행하면 race 가 가능 — 1차 실행은 ▶ 단계별
클릭으로 응답을 확인하는 편을 권장.

### 재실행

- `residentNumber`·`businessNumber`·`plateNumber` 등은 `$random.integer` 로 매번
  새 값을 만들어 UNIQUE 제약과 충돌하지 않는다 → 같은 사용자로 시나리오 반복 가능
- 캡처된 ID 는 `client.global` 에 디스크 persisted → IntelliJ 재시작 후에도 유지.
  특정 단일 step 만 재실행 시 그 ID 가 살아있는 동안 동작

---

## 4. 검증 포인트

| 확인 항목 | 어떻게 |
|---|---|
| Gateway → 서비스 라우팅 | step 0~11 모두 2xx 응답 |
| location 경계 격리 (AS-08 컨셉) | alice(LOC1) 로 등록 → erin(LOC2) 로 같은 서비스 dashboard 호출 시 그 데이터가 안 보임 |
| PgBouncer 경유 (AS-02) | 시나리오 도중 PgBouncer Pod 에서 `SHOW POOLS` (아래 박스) |
| HikariCP 풀 상태 | 서비스 Pod 의 `/actuator/metrics/hikaricp.connections.active` |

### PgBouncer SHOW POOLS 빠른 확인

```bash
kubectl exec -it deploy/pgbouncer-location1 -- \
  psql -h 127.0.0.1 -p 6432 -U db1-app pgbouncer -c 'SHOW POOLS'
```

`cl_active` (클라이언트 활성), `sv_active` (서버 활성) 가 분리되어 보이고
`sv_active ≤ default_pool_size` 면 AS-02 곱셈 차단이 동작 중.

---

## 트러블슈팅

### 401 Unauthorized

env 가 선택되지 않은 상태. 우상단 env-selector 에서 alice~liam 중 하나 선택.
status bar 에 현재 env 가 표기되는지 확인.

### 409 Conflict / UNIQUE 위반

이미 같은 (location, residentNumber 등) 으로 등록된 상태. 시나리오는 `$random.integer`
로 매번 새 값을 생성하므로 전체 흐름 재실행 시엔 충돌이 없다. 특정 step 만 단독
실행한 경우라면 step 3 부터 다시 ▶.

### step 6~7 에서 422 / 도메인 오류

선행 step 의 captured id 가 없거나 다른 location 의 데이터. 우측 응답창에서
ID 가 `client.global` 에 들어갔는지(`> {% client.global.set(...) %}` 블록의 실행
결과) 확인. 안 들어갔다면 그 step 부터 ▶ 재실행.

### `curl: (52) Empty reply from server` 또는 connection refused

`kubectl port-forward svc/gateway 8080:8080` 가 끊긴 상태. 같은 명령을 다시
띄우거나 IntelliJ 의 Forward Ports 항목에서 재시작.

### WSL 환경에서 IntelliJ(Windows) 가 localhost:8080 에 못 붙는다

WSL 안의 `kubectl port-forward` 는 기본적으로 WSL 의 127.0.0.1 에만 바인딩되어
Windows 호스트의 IntelliJ 가 접근 못 한다. `--address 0.0.0.0` 옵션을 붙이거나,
IntelliJ Kubernetes 플러그인의 Forward Ports (Windows 측 kubectl 사용) 를 활용.

### Pod 가 ImagePullBackOff / pgbouncer 이미지 못 끌어옴

가이드 06 의 트러블슈팅 절 참조.
