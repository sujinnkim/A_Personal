# 여러 서비스 동시 port-forward — `prototype/scripts/pf.sh`

`kubectl port-forward` 는 한 번에 한 서비스만 다룬다. 클러스터 안의 Grafana·
Prometheus·ArgoCD·게이트웨이 등을 동시에 로컬에서 보려면 터미널 여러 개를 띄우거나
백그라운드 잡으로 묶어야 한다. 이 스크립트는 한 셸에 다중 forward 를 띄우고 Ctrl+C
한 번으로 일괄 종료한다.

> **부하 시험에는 본 스크립트(port-forward) 를 쓰지 말 것.** SPDY 스트림이 부하에
> 깨지면 listener 자체가 사라져 신뢰성 있는 측정 불가. k6 등 부하 시험은
> [가이드 10](10-load-test-k6.md) 의 클러스터 내 Job 패턴 사용.

## 사전 조건

- 가이드 05·06 으로 GKE 클러스터가 떠 있고 `kubectl get pods -A` 가 정상
- monitoring Application 이 sync 됐고 `kubectl -n monitoring get pods` 에 grafana·
  prometheus·operator 가 Running (가이드 05·06 이후 머지된 변경)
- bash 4 이상 (associative array 사용). WSL 기본 bash 만족.

## 사용법

```bash
# default 5종 (grafana 3000, prometheus 9090, argocd 8081, gateway 8080, kafka-ui 8090)
prototype/scripts/pf.sh

# 특정 alias 만
prototype/scripts/pf.sh grafana argocd

# 정의된 전부
prototype/scripts/pf.sh all

# 사용 가능한 alias 와 매핑 확인
prototype/scripts/pf.sh -l

# 도움말
prototype/scripts/pf.sh -h
```

## 정의된 alias

| alias | namespace | target | local : pod |
|---|---|---|---|
| `grafana` | monitoring | `svc/monitoring-grafana` | 3000 → 80 |
| `prometheus` | monitoring | `svc/monitoring-kube-prometheus-prometheus` | 9090 → 9090 |
| `argocd` | argocd | `svc/argocd-server` | 8081 → 443 |
| `gateway` | default | `svc/gateway` | 8080 → 8080 |
| `kafka-ui` | default | `svc/kafka-ui` | 8090 → 8080 |

DB(PgBouncer·Postgres) 접근은 본 스크립트에서 제외 — DB 툴 연결은 가이드 07
(IntelliJ DataGrip 으로 GKE Postgres 붙기) 의 단일 forward 방식을 사용한다.

## 접속

스크립트가 살아 있는 동안 다음 URL 이 열린다.

| 서비스 | URL | 기본 인증 |
|---|---|---|
| Grafana | http://localhost:3000 | admin / admin (PoC 한정 — `prototype/helm/monitoring/values.yaml`) |
| Prometheus | http://localhost:9090 | 없음 (in-cluster, 외부 노출 안함) |
| ArgoCD | https://localhost:8081 | admin / `kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" \| base64 -d` |
| Gateway | http://localhost:8080 | seed 토큰은 가이드 08 참조. *수동 HTTP 호출 / .http 시나리오 용도 한정* — 부하 시험은 [가이드 10](10-load-test-k6.md) |
| Kafka UI | http://localhost:8090 | 없음 (PoC). `local` 클러스터로 등록된 in-cluster `kafka:9092` 브로커 1대를 브라우저로 탐색 — 토픽·메시지·컨슈머 그룹 확인 |

## 종료

- 스크립트가 켜진 터미널에서 **Ctrl+C** — trap 이 자식 forward 를 일괄 kill.
- 비정상 종료(`kill -9`) 후 좀비 forward 가 남으면:
  ```bash
  pkill -f "kubectl.*port-forward"
  ```

## 동작 원리

스크립트는 각 alias 의 `kubectl port-forward` 를 백그라운드 잡으로 띄우고
`wait` 으로 셸을 붙잡는다. `trap … EXIT INT TERM` 이 셸 종료 신호를 받으면
`jobs -p` 로 모든 자식 PID 를 가져와 일괄 kill 한다.

```bash
trap 'kill $(jobs -p) 2>/dev/null || true' EXIT INT TERM
kubectl ... port-forward ... &
kubectl ... port-forward ... &
wait
```

## 새 alias 추가

`prototype/scripts/pf.sh` 의 `targets` 연관 배열에 한 줄 추가:

```bash
declare -A targets=(
  ...
  [myname]="<namespace> <target> <localport>:<podport>"
)
```

target 은 `svc/<name>`, `pod/<name>`, `deployment/<name>` 등 `kubectl
port-forward` 가 받는 모든 형태. 같은 로컬 포트가 다른 alias 와 충돌하면 안 됨.

## 트러블슈팅

- **`unable to forward port because pod is not running`** — 해당 Pod 가 아직 ready
  안 됐거나 namespace 가 다름. `kubectl -n <ns> get pod` 로 확인.
- **`bind: address already in use`** — 같은 로컬 포트를 다른 프로세스가 점유 중.
  `lsof -i :<port>` 또는 `ss -lntp | grep :<port>` 로 잡고 종료.
- **Service 이름이 다르다** — `helm install` 의 release 이름에 따라 svc 명이
  바뀐다. `prototype/helm/monitoring/values.yaml` 에 release 이름을 고정하거나
  스크립트의 alias 값을 실제 svc 명으로 수정.
- **ArgoCD UI 가 *Service Unavailable*** — argocd-server 가 TLS 종단을 자체 처리
  하므로 https 로 접근. 자체 서명 인증서라 브라우저 경고는 무시.
