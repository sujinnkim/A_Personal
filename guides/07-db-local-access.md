# IntelliJ Database 툴로 GKE Postgres 붙기

GKE 안의 Postgres(`default/postgres` ClusterIP svc, 5432)는 외부에 노출돼 있지 않다. 로컬에서 IntelliJ Database 툴로 접근하려면 **port-forward 로 localhost 에 끌어와서** PostgreSQL 데이터소스로 연결한다.

## 사전 조건

- 가이드 05 의 `terraform apply` 완료 + 가이드 06 으로 `postgres` Pod 가 `Running`
- kubectl 이 해당 클러스터를 가리키는 상태 (`kubectl get pod postgres-0` 으로 확인)
- IntelliJ IDEA (Ultimate 권장 — Database 툴 기본 내장)

---

## 1. IntelliJ Kubernetes 플러그인 설치 + 클러스터 등록 — [PC 별 1회]

**Settings → Plugins → Marketplace** → `Kubernetes` 검색 → Install → IDE 재시작.

설치 후 좌측 `Services` 툴 윈도우(`Alt+8`) 에 `Kubernetes` 노드가 보인다. 별도 등록 없이 `~/.kube/config` 의 current-context 를 자동 인식한다. 컨텍스트가 여러 개라면 노드 우클릭 → `Configure Plugin...` 에서 활성 컨텍스트를 고른다.

### WSL 사용자: Windows 쪽에도 gcloud + kubectl 셋업 — [WSL 한정, PC 별 1회]

IntelliJ 는 WSL 안이 아니라 **Windows 호스트** 에서 돌아가므로 `~/.kube/config` 도 `C:\Users\<사용자>\.kube\config` 를 읽는다. WSL 쪽에서만 가이드 05 의 인증·`get-credentials` 를 돌렸으면 Windows 쪽 kubeconfig 는 비어 있어 플러그인 트리에 클러스터가 안 나타난다. PowerShell 또는 cmd 에서 한 번 더 셋업한다.

1. **Google Cloud CLI for Windows 설치** — [공식 인스톨러](https://cloud.google.com/sdk/docs/install#windows) 의 `.exe` 를 받아 실행. 설치 마지막 단계에서 `Run gcloud init` 체크는 켜둬도 되고, 끄고 아래 단계를 수동으로 해도 된다.

2. **PowerShell 새 창** 을 열고 (인스톨러 직후의 기존 셸은 PATH 갱신 전일 수 있다):

   ```powershell
   gcloud --version
   gcloud auth login
   gcloud config set project architect-certification-289902

   # GKE 인증 플러그인 + kubectl
   gcloud components install gke-gcloud-auth-plugin kubectl

   # 클러스터 자격증명을 Windows-side kubeconfig 에 기록
   gcloud container clusters get-credentials prod-gke-2601-team4 `
     --zone asia-east2-a --project architect-certification-289902

   kubectl get nodes
   ```

   `kubectl get nodes` 가 노드 2개를 `Ready` 로 보여주면 IntelliJ Kubernetes 플러그인도 같은 컨텍스트를 즉시 인식한다 (IDE 재시작 불필요, `Services` 트리에서 한 번 refresh).

> WSL 과 Windows 의 kubeconfig 는 완전히 독립이라 양쪽을 따로 관리하거나 한쪽을 다른 쪽에 복사해두면 된다. WSL 쪽에서 컨텍스트를 바꿔도 IntelliJ 는 모른다.

> `gcloud auth login` 은 브라우저를 띄우므로 WSL 안의 가이드 05 처럼 URL 수동 복붙은 필요 없다.

---

## 2. Postgres 서비스에 port-forward

두 가지 방법 중 편한 걸 고른다.

### 방법 A. IntelliJ GUI

1. `Services` 툴 윈도우 → `Kubernetes → <컨텍스트> → Namespaces → default → Services → postgres` 우클릭
2. **`Forward Ports...`** → Local port `5432`, Remote port `5432` → OK
3. 5432 가 점유돼 있으면 `15432` 같은 다른 포트 사용

forward 상태는 같은 트리에서 확인 가능. IDE 종료 시 자동 해제.

### 방법 B. kubectl CLI (별도 터미널을 띄워둬야 함)

```bash
kubectl port-forward -n default svc/postgres 5432:5432
# 또는 충돌 회피용:
kubectl port-forward -n default svc/postgres 15432:5432
```

---

## 3. IntelliJ Database 툴에서 데이터소스 추가

`Database` 툴 윈도우(`View → Tool Windows → Database`) → `+` → `Data Source` → `PostgreSQL`.

| 필드 | 값 |
|------|-----|
| Host | `localhost` |
| Port | `5432` (방법 B 에서 다른 포트로 했으면 그 값) |
| Database | `auth` / `location1` / `location2` / `location3` 중 하나 |
| User / Password | 아래 표 참고 |

처음이면 다이얼로그 하단 `Download driver` 한 번 클릭. `Test Connection` 으로 확인 후 OK.

DB 4개에 각각 별도 데이터소스를 만들어 두면 편하다.

### 사용 가능한 계정

`prototype/helm/postgres/templates/configmap-init.yaml` 의 `init.sh` 가 만드는 role 들. 비밀번호는 username 과 동일하게 통일돼 있다.

| 용도 | username | password | 권한 |
|---|---|---|---|
| 슈퍼유저 | `postgres` | `postgres` | 전부 |
| manager | `manager` | `manager` | 4개 DB owner, SUPERUSER |
| location1 (DDL/마이그레이션) | `db1-flyway` | `db1-flyway` | location1 public schema owner |
| location1 (앱 read/write) | `db1-app` | `db1-app` | SELECT/INSERT/UPDATE/DELETE |
| location2 | `db2-flyway` / `db2-app` | 동일 | (위와 동형) |
| location3 | `db3-flyway` / `db3-app` | 동일 | (위와 동형) |
| auth (DDL/마이그레이션) | `auth-flyway` | `auth-flyway` | auth public schema owner |
| auth (앱 read/write) | `auth-app` | `auth-app` | SELECT/INSERT/UPDATE/DELETE |

> PoC 전용 평문 비밀번호. 운영 전환 시 sealed-secrets / ExternalSecret 으로 대체.

조회/디버깅 용도면 `manager` 가 가장 편하다 — 4개 DB 어디에 붙어도 모두 보인다.

---

## 트러블슈팅

### Test Connection 시 `connection refused`

- port-forward 가 끊긴 상태. 방법 A 면 Services 트리에서 forward 항목 확인, 방법 B 면 터미널의 `kubectl port-forward` 가 살아 있는지 확인.
- 5432 가 로컬 Postgres 등에 의해 이미 점유 — `lsof -i :5432` (Linux/Mac) 또는 `netstat -ano | findstr 5432` (Windows) 로 확인하고 다른 포트로.

### `FATAL: password authentication failed`

- 위 표의 username/password 쌍을 다시 확인. `db1-flyway` 처럼 `-` 가 포함된 role 은 IntelliJ 입력에서 그대로 넣으면 된다 (따옴표 불필요).
- `Database` 필드의 DB 와 user 의 권한이 안 맞는 경우. 예: `db1-app` 으로 `location2` 에 붙으면 `FATAL: permission denied for database "location2"`.

### `kubectl port-forward` 가 곧 끊긴다

- VPN/네트워크 단절 또는 GKE control plane 의 idle timeout. 끊기면 같은 명령으로 다시 띄우면 된다. 장시간 유지가 필요하면 IntelliJ 의 forward 가 좀 더 안정적.

### Pod 가 재시작되면 데이터가 사라지나

- `postgres` 차트는 StatefulSet + PVC (default 8Gi, GKE `standard-rwo`). Pod 재시작은 데이터 유지. 단, **`helm uninstall postgres`** 또는 ArgoCD 의 prune 으로 PVC 가 삭제되면 모든 DB 가 날아간다.
