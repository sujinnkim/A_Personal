# k6 로 부하 테스트 실행 — `prototype/verification/`

`memo/20260519_부하테스트_시나리오_및_도구.md` 의 설계를 k6 시나리오로 구현한
부하 테스트. 본 가이드는 설치·실행·트러블슈팅을 다룬다. 시나리오 설계의
의도(통상 4,800 TPS, UC 비율, 세션 로테이션 등)는 memo 참조.

> **prod 환경 부하 시험은 반드시 클러스터 안에서 실행한다** (§ 클러스터 k6).
> `pf.sh gateway` 로 port-forward 한 뒤 로컬 k6 를 돌리면 SPDY 스트림이 부하에
> 깨져 *연결 실패가 자체 결과로 보고*되며 측정 신뢰성이 사라진다. 로컬 k6 는
> 로컬 docker-compose 환경의 디버깅 / 소규모 검증 용도.

## 사전 조건

1. **세션·사용자 데이터가 auth DB에 적재되어 있음** — 가이드 7 의 DB 접속으로
   확인하거나 `prototype/verification/data/load-seed.js` 로 적재.

   ```bash
   cd prototype/verification/data
   npm install
   node load-seed.js                              # 로컬 docker-compose
   PGPORT=15432 node load-seed.js                 # prod (port-forward 후)
   ```

   재생성이 필요하면: `python3 generate-load-test-seed.py` (deterministic UUID,
   재실행 동일 결과).

2. **게이트웨이 접근 가능**
   - 로컬: `prototype/.local/docker-compose up -d` + 백엔드 기동 후
     `http://localhost:8080`
   - prod (클러스터 안에서 실행): 게이트웨이 Service `gateway.default` 도달
     가능 — `kubectl get svc gateway` 확인

3. **k6 실행 환경** — § 클러스터 k6 (권장) 또는 § 로컬 k6 (디버깅용).

## 세션 JSON 생성 (1회)

k6 가 사용할 세션 풀을 CSV → JSON 으로 변환. `databases/data/sessions-500.csv`
는 데이터, `k6/sessions.json` 은 k6 진입 시 `SharedArray` 로 로드되는 형태.

```bash
node prototype/verification/export-sessions.js
# wrote /.../k6/sessions.json (500 sessions)
```

## 실행 — 클러스터 k6 (★ prod 권장)

`prototype/scripts/k6-run.sh` 가 ConfigMap 으로 시나리오를 업로드하고 Kubernetes
Job 으로 실행한다. 종료 후 Job·Pod·ConfigMap 이 `ttlSecondsAfterFinished=300`
으로 자동 청소되어 *터미널 닫힘·네트워크 끊김·Ctrl-C 와 무관하게* 잔여 리소스
없음.

```bash
# VS0 워밍업 (50 VU × 2분) — 본 시험 전 1회 선행
prototype/scripts/k6-run.sh prototype/verification/k6/scenarios/vs0-warmup.js \
  --env BASE_URL=http://gateway:8080

# VS1 — AS-02 커넥션 통제 (0 → 600 RPS 점증 20분, scale-out 1→2 중 커넥션 증감 관측)
prototype/scripts/k6-run.sh prototype/verification/k6/scenarios/vs1-connection-control.js \
  --env BASE_URL=http://gateway:8080

# VS2 — AS-03 워크로드 격리 (OLTP 600 RPS + 분석 슬로우 쿼리 주입, OLTP p50 유지 확인)
prototype/scripts/k6-run.sh prototype/verification/k6/scenarios/vs2-workload-isolation.js \
  --env BASE_URL=http://gateway:8080

# (참고) 통상×3 급등은 추후 VS3(AS-04)로 구성 — 현재 s2-burst.js 가 급등 재현
```

### BASE_URL — 클러스터 내부 DNS

| 환경 | BASE_URL |
|---|---|
| 클러스터 안 (k6-run.sh) | `http://gateway:8080` (같은 namespace) 또는 `http://gateway.default.svc.cluster.local:8080` |
| 로컬 docker-compose | `http://localhost:8080` |

### Ctrl-C 동작

- 로그 스트리밍 중 Ctrl-C: *스트리밍만 끊김*. Job 은 계속 동작 → 의도된 보호.
- 실제로 중단하려면 `kubectl delete job/<job-name>` (스크립트가 시작 시 출력).

### 결과 사후 확인 (ttl=300s 안)

```bash
kubectl logs job/<job-name>                      # 직후 다시 보기
kubectl describe job/<job-name>                  # 상태 / 종료 코드

# JSON summary 가 필요하면
prototype/scripts/k6-run.sh vs1-connection-control.js \
  --env BASE_URL=http://gateway:8080 \
  --summary-export=/tmp/summary.json

POD=$(kubectl get pod -l job-name=<job-name> -o jsonpath='{.items[0].metadata.name}')
kubectl cp $POD:/tmp/summary.json ./summary.json   # ttl 안에서만 가능
```

## 실행 — 로컬 k6 (디버깅용)

로컬 docker-compose 환경 또는 PoC 소규모 호출 검증에 한정. **prod 환경의 정량
측정에는 사용하지 말 것** (port-forward 의 SPDY 한계로 부하 시 측정 무효화).

### k6 설치

```bash
# Linux / WSL2 (Ubuntu/Debian)
curl -fsSL https://dl.k6.io/key.gpg | sudo gpg --dearmor -o /usr/share/keyrings/k6-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

# macOS
brew install k6

# Windows
winget install k6 --source winget
```

### 실행

```bash
cd prototype/verification

k6 run k6/scenarios/vs0-warmup.js                 # VS0 워밍업, 50 VU × 2분
BASE_URL=http://localhost:8080 \
  k6 run k6/scenarios/vs1-connection-control.js   # VS1, 0 → 600 RPS 점증

# 결과 저장
k6 run --out json=results.json k6/scenarios/vs1-connection-control.js
```

Prometheus remote-write / InfluxDB 백엔드와 연동 (monitoring 차트의 Prometheus
사용 시 `xk6-output-prometheus-remote` 확장 필요).

## 환경변수

| 변수 | 기본값 | 용도 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 게이트웨이 URL |
| `SESSION_HOLD` | `5` | VU 가 한 세션을 유지할 iteration 수. 너무 짧으면 PgBouncer transaction-pool 재사용율이 과대평가됨 (memo §3.2) |

## 측정 지표 / threshold

`prototype/verification/k6/scenarios/*.js` 의 `options.thresholds`:

- `http_req_duration{sync:true}` p50 ≤ 100ms — QA-02 정량 기준
- `http_req_failed{sync:true}` rate < 1% (S2 burst 는 2%)

`sync:true` 태그는 공무원·부서장 동기 처리 UC(UC-01, 02, 03, 04r, 04w, 05, 07)
에만 부착된다. 시스템·관리자 호출(UC-06, 09)은 임계 제외.

분포는 k6 콘솔 출력 또는 `--out json` 으로 분석. PgBouncer/Postgres 메트릭은
monitoring 차트의 Prometheus 가 수집 (가이드 09 의 `pf.sh grafana` 로 대시보드
접근).

## 트러블슈팅

### `dial tcp 127.0.0.1:8080: connect: connection refused` (로컬 k6 + port-forward 시)

원인: 로컬 k6 가 port-forward 경유로 부하를 보내면 SPDY 스트림이 부하에 깨져
listener 자체가 사라진다. 한 번 깨지면 자동 복구 안 됨 — 이후 모든 요청 실패.

조치: § 클러스터 k6 로 전환. port-forward 경유 측정은 정량 검증에 부적합.

### `dial tcp: lookup gateway: no such host` (클러스터 k6)

원인: `--env BASE_URL=` 미지정 또는 다른 namespace 의 서비스를 짧은 이름으로
호출. 조치: FQDN `gateway.default.svc.cluster.local:8080` 사용 또는 같은
namespace 에서 실행.

### Pod 가 `ImagePullBackOff` (클러스터 k6)

`grafana/k6:latest` pull 정책 또는 사내 mirror 필요. Job spec 의 image 를 사내
Artifact Registry mirror 로 변경하거나 `imagePullPolicy: IfNotPresent` 적용.

### `WARN[xxx] Insufficient VUs, reached XXX active VUs`

원인: `ramping-arrival-rate` 가 도달해야 할 RPS 대비 `preAllocatedVUs` /
`maxVUs` 가 부족.

조치: 해당 시나리오 파일의 `preAllocatedVUs` / `maxVUs` 를 키운다. 4,800 RPS
에 대략 200~400 VU, 14,400 RPS 에 1,200~2,000 VU 가 일반적인 출발점.

### 모든 요청이 4xx — 의도된 동작인지 확인

쓰기 UC (UC-02 / 04w / 05 / 06) 는 사전 시드 파이프라인이 없어 랜덤 UUID 를
사용한다. 4xx 응답이 정상이며 auth → 게이트웨이 → 서비스 → DB lookup 경로는
그대로 측정에 반영된다. 정상 트랜잭션 비율로 재현하려면 (carrier/vehicle/claim)
시드 후 ID 풀을 공유해야 함 — `prototype/verification/README.md` 의 TODO 참조.

### 세션이 만료되어 401

`sessions-500.csv` 의 `expires_at` 은 `2999-12-31` 이라 실제 만료는 일어나지
않는다. 401 이 나오면 (a) auth DB 에 세션이 안 들어갔거나, (b) 게이트웨이가
잘못된 DB 를 본다. `psql -d auth -c "SELECT count(*) FROM sessions"` 로 확인.
