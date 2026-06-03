# GCP 검증 인프라 구축 가이드

Terraform으로 GKE 클러스터 + ArgoCD를 한 번에 프로비저닝한다.

## 결과물

| 자원 | 이름 / 사양 |
|---|---|
| GKE 클러스터 | `prod-gke-2601-team4` (zonal, asia-east2-a) |
| Spot 노드풀 | `prod-gke-2601-team4-spot` (e2-standard-4 × 3, pd-standard 50GB) — 총 12 vCPU / 48 GiB |
| 노드 전용 SA | `prod-gke-2601-team4-node@architect-certification-289902.iam.gserviceaccount.com` |
| ArgoCD | 네임스페이스 `argocd`, ClusterIP + port-forward |

월 비용 ~$65 (24/7 기준). 검증 종료 시 `terraform destroy`로 정리하면 더 저렴.

## 사전 조건

- GCP 프로젝트 + 결제 계정 활성화 (관리자가 사전에 준비)
- WSL2 / Linux 환경
- 인터넷 + 브라우저

---

## 각 절 적용 범위 표기

각 절 제목 옆 라벨로 어떤 상황에서 그 절이 필요한지 표시한다.

| 라벨 | 의미 |
|---|---|
| `[환경 설정 필수]` | 인프라가 이미 생성된 상태에서도 **다른 PC에서 gcloud/kubectl로 제어하려면 반드시 수행** |
| `[부분 수행]` | 해당 절 안에서 일부 명령만 필요 — 본문에 어느 부분인지 표기 |
| `[자원 생성 전용 · 스킵]` | 인프라 자원을 새로 만들거나 변경할 때만 필요. 환경 설정 목적이면 건너뛴다 |

### 다른 PC에서 환경만 갖추는 경우 빠른 경로

1번 → 2번 → 3번(`gcloud config set project`만) → 7번 → 9번(Pod 상태 확인 + 포트포워드만) 순서로 진행하면 된다. 4·5·6·8·자원 정리·변수 커스터마이즈는 건너뛴다.

> Terraform으로 기존 자원을 제어할 계획이라면 추가로 GCS backend 전환이 필요하다. 현재 가이드는 **로컬 state** 기준이라 다른 PC에서는 같은 state를 볼 수 없다 (자세한 내용은 [알려진 제약 — 운영 환경 아님](#운영-환경-아님) 참고). 단순히 클러스터에 `kubectl`로 붙고 ArgoCD를 쓰는 정도라면 Terraform 자체가 다른 PC에 필요 없다.

---

## 1. 도구 설치 — [환경 설정 필수]

```bash
# 버전 확인 — 없거나 모자라면 설치
gcloud --version       # 권장 400+
terraform version      # 필수 1.5.0 이상
kubectl version --client  # 권장 1.28+
```

설치 안 되어 있다면:

- **gcloud**: [공식 가이드](https://cloud.google.com/sdk/docs/install) — 보통 tarball 또는 apt 저장소
- **terraform**: [공식 가이드](https://developer.hashicorp.com/terraform/install)
- **kubectl**: `gcloud components install kubectl` 또는 별도 설치

> 환경 설정만이 목적이라면 `terraform`은 생략 가능 (위 "빠른 경로" 안내 참고). `gcloud`와 `kubectl`만 있어도 클러스터 접근·ArgoCD 사용에 충분.

---

## 2. GCP 인증 — [환경 설정 필수]

두 가지 인증이 필요하다. user 인증은 `gcloud` CLI 명령용, ADC(Application Default Credentials)는 Terraform `google` provider가 자동으로 집어가는 토큰.

```bash
# user 인증
gcloud auth login

# ADC — Terraform이 사용할 자격증명
gcloud auth application-default login
```

WSL에서 브라우저가 자동으로 안 뜨면 출력된 URL을 Windows 브라우저에 붙여넣고 받은 인증 코드를 터미널에 다시 붙여넣으면 된다.

확인:

```bash
gcloud auth list
gcloud auth application-default print-access-token | head -c 20 && echo
```

- 본인 계정이 `* (active)`로 표시되고
- ADC access token 일부가 출력되면 정상

---

## 3. 프로젝트 컨텍스트 + 결제 확인 — [부분 수행]

> 환경 설정만이 목적이라면 **`gcloud config set project architect-certification-289902` 한 줄만** 실행하면 된다. 결제 활성화 확인은 인프라 자원을 새로 만들 때만 의미 있으므로 스킵.

```bash
# 사용 가능한 프로젝트 목록
gcloud projects list

# 기본 프로젝트로 설정  ← 환경 설정만이라면 이 한 줄만 필요
gcloud config set project architect-certification-289902

# 결제 활성화 확인  ← [자원 생성 전용 · 스킵]
gcloud beta billing projects describe architect-certification-289902
```

- `billingEnabled: true` 여야 다음 단계 진행 가능
- `gcloud beta` 컴포넌트가 없다는 메시지가 나오면 `gcloud components install beta` 또는 Console > Billing에서 직접 확인

---

## 4. 필요한 API 활성화 — [자원 생성 전용 · 스킵]

> API는 프로젝트 단위 설정이라 누군가 한 번 켜두면 유지된다. 환경 설정만이 목적이면 스킵.

```bash
gcloud services enable \
  container.googleapis.com \
  compute.googleapis.com

# 확인
gcloud services list --enabled --filter="name:(container.googleapis.com OR compute.googleapis.com)"
```

두 줄 모두 출력되면 OK.

---

## 5. `terraform.tfvars` 작성 — [자원 생성 전용 · 스킵]

```bash
cd prototype/terraform

cp terraform.tfvars.example terraform.tfvars
```

`terraform.tfvars`의 `project_id`만 실제 GCP 프로젝트 ID로 채워 넣는다. 다른 값(이름·zone 등)은 default를 사용한다.

```hcl
project_id = "architect-certification-289902"
```

> `terraform.tfvars`는 `.gitignore`에 등록되어 있어 커밋되지 않는다.

---

## 6. Terraform 초기화 + 적용 — [자원 생성 전용 · 스킵]

```bash
terraform init
```

`google`, `kubernetes`, `helm`, `tls` 네 provider가 다운로드되고 마지막에 `Terraform has been successfully initialized!`이 출력되면 OK.

> **주의 — `terraform init -upgrade` 금지 (클러스터가 이미 떠 있는 경우).** google provider 5.40 → 5.45+ 사이에 `google_container_cluster` schema 가 strict 해져, upgrade 후 plan 이 클러스터를 destroy + recreate 하려고 시도한다. 이후 cluster endpoint 가 "known after apply" 가 되면서 `kubernetes` provider 가 `127.0.0.1:80` 으로 fallback → `connection refused` 에러로 plan 자체가 깨진다. 이미 떠 있는 클러스터에 새 리소스(예: argocd-bootstrap) 만 추가하려면 plain `terraform init` 만 사용. 새 provider 추가는 plain init 으로도 다운로드된다.

```bash
terraform plan
```

`Plan: 10 to add, 0 to change, 0 to destroy.` 가 마지막에 나와야 정상. 추가되는 10개:
- `google_service_account.gke_node`
- `google_container_cluster.poc`
- `google_container_node_pool.spot`
- `kubernetes_namespace.argocd`
- `helm_release.argocd`
- `tls_private_key.argocd_deploy_key` — ArgoCD ↔ GitHub SSH 키쌍
- `kubernetes_secret.argocd_repo` — ArgoCD 가 인식하는 repo credential
- `kubernetes_manifest.argocd_root_app` — App-of-Apps 루트
- `google_project_service.artifactregistry` — GAR API 활성화
- `google_artifact_registry_repository.poc` — 서비스 이미지 저장소

> 노드 SA 의 GAR pull 권한(`roles/artifactregistry.reader`)은 Terraform 에서 부여하지 않는다 — 본인 계정에 `setIamPolicy` 가 없는 환경이라 거부된다. 가이드 06 의 1번 절차(관리자 권한)로 따로 부여.

```bash
terraform apply
# yes 입력 → 10~15분 소요
```

마지막에 `Apply complete! Resources: 10 added, 0 changed, 0 destroyed.` 와 outputs 7개(`kubeconfig_command`, `argocd_port_forward`, `argocd_admin_password`, `cluster_endpoint`, `argocd_deploy_key_public`, `image_repository_base`, `gar_docker_configure_command`)가 출력된다.

> 이미 클러스터·ArgoCD 가 떠 있는 상태에서 argocd-bootstrap + GAR 만 추가하는 경우엔 `Plan: 5 to add, 0 to change, 0 to destroy.` (tls_private_key + kubernetes_secret + kubernetes_manifest + google_project_service + google_artifact_registry_repository) 만 나와야 정상. 만약 클러스터가 destroy 또는 replace 대상으로 잡힌다면 위 `terraform init -upgrade` 경고 확인.

> **사전 조건:** 이 apply 가 성공하려면 helm 차트(`prototype/helm/*`)와 ArgoCD Application 매니페스트(`prototype/argocd/applications/*`, `prototype/argocd/root.yaml`)가 GitHub 에 push 되어 있어야 한다. ArgoCD root 가 git 의 path 를 못 찾으면 sync 실패만 날 뿐 apply 자체는 끝난다 — 8번 절차에서 deploy key 등록 후 자동 복구된다.

---

## 7. kubeconfig + 인증 플러그인 설치 — [환경 설정 필수]

> 다른 PC에서는 6번을 건너뛰고 아래 `gcloud container clusters get-credentials …` 명령을 클러스터 이름·zone·프로젝트만 맞춰 직접 실행하면 된다.

apply output의 `kubeconfig_command`를 그대로 실행한다.

```bash
gcloud container clusters get-credentials prod-gke-2601-team4 \
  --zone asia-east2-a \
  --project architect-certification-289902
```

다음과 같은 경고가 나오면 `gke-gcloud-auth-plugin`을 설치해야 한다. GKE는 이 별도 바이너리를 통해 kubectl을 인증한다.

```
CRITICAL: ACTION REQUIRED: gke-gcloud-auth-plugin, which is needed for continued use of kubectl, was not found or is not executable.
```

설치 (gcloud 설치 방식에 따라 둘 중 하나):

```bash
# (A) gcloud를 tarball/installer로 설치한 경우
gcloud components install gke-gcloud-auth-plugin

# (B) gcloud를 apt로 설치한 경우
sudo apt-get install -y google-cloud-cli-gke-gcloud-auth-plugin
```

> (B)에서 `Unable to locate package` 에러가 나면 (A)로 시도. (A)에서 `Cloud SDK component manager is disabled` 에러가 나면 apt 저장소를 최신화해야 함:
> ```bash
> echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | \
>   sudo tee /etc/apt/sources.list.d/google-cloud-sdk.list
> curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | \
>   sudo gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg
> sudo apt-get update
> sudo apt-get install -y google-cloud-cli-gke-gcloud-auth-plugin
> ```

확인:

```bash
gke-gcloud-auth-plugin --version
kubectl get nodes -o wide
```

노드 2개가 STATUS `Ready` 로 보이면 정상.

---

## 8. GitHub Deploy Key 등록 — [자원 생성 전용 · 스킵]

Terraform 이 `tls_private_key.argocd_deploy_key` 로 SSH 키쌍을 만들었고, private key 는 ArgoCD repo Secret 으로 클러스터 안에 들어가 있다. 이제 public key 를 GitHub 측에 read-only deploy key 로 등록해야 root Application 이 git 에서 자식 매니페스트를 가져올 수 있다.

```bash
terraform output -raw argocd_deploy_key_public
```

출력된 한 줄(`ssh-ed25519 AAAA... terraform-generated`)을 그대로 복사한다.

GitHub Repo 페이지 → **Settings → Deploy keys → Add deploy key**:

- Title: `argocd-gke-team4` (혹은 식별 가능한 임의 이름)
- Key: 위에서 복사한 한 줄
- **Allow write access: 체크 해제 (read-only)**
- Add key

등록 후 ArgoCD 가 다음 자동 sync 주기(약 3분)에 자식 9개를 가져온다. 즉시 확인하려면 9번 UI 에서 root Application 의 *Refresh* 클릭.

> public key 만 GitHub 에 노출. private key 는 GKE 클러스터의 Secret 안에만 있다. PoC 종료 시 `terraform destroy` 하면 Secret + 키쌍 모두 같이 제거된다.

---

## 9. ArgoCD UI 접근 — [부분 수행]

> 환경 설정만이 목적이라면 **Pod 상태 확인 + 포트포워드만** 수행하면 된다. 초기 비밀번호 조회는 ArgoCD 최초 설치 직후 한 번만 의미 있는 단계 — 이미 운용 중인 클러스터에 새 PC로 붙는 상황이면 본인 비밀번호로 로그인하면 되므로 스킵.

```bash
# Pod 상태 확인 — 4개 모두 Running
kubectl get pods -n argocd

# UI 포트포워드 (foreground로 계속 떠 있어야 함 — 별도 터미널에서 띄우고 그대로 유지)
kubectl port-forward -n argocd svc/argocd-server 8080:443

# 다른 터미널에서 admin 초기 비밀번호 조회  ← [최초 설치 직후 1회 전용 · 이후엔 스킵]
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d && echo
```

브라우저로 접속:

- URL: **`https://localhost:8080`** (반드시 `https`, ArgoCD는 평문 HTTP를 받지 않음)
- 자체 서명 인증서 경고 → "고급" → "안전하지 않은 사이트로 이동"
- ID `admin` + 위에서 조회한 비밀번호로 로그인
- 로그인 후 좌측 메뉴 User Info에서 비밀번호 변경 권장

처음 로그인 시 화면에서 보이는 Application 목록(자원이 처음 생성되었다면):

| Application | sync wave | 기대 상태 |
|---|---:|---|
| `root` | (없음) | Synced / Healthy — 다른 8개를 자식으로 거느림 |
| `postgres` | 0 | Synced / Healthy |
| `flyway` | 10 | Synced / Healthy (Job 5개는 PostSync 로 Completed) |
| `auth` / `common-data` / `fuel-subsidy` / `child-care` / `disaster-relief` / `senior-pension` | 20 | Synced / Healthy |
| `gateway` | 30 | Synced / Healthy |

> WSL에서 브라우저 접속이 안 되면 `kubectl port-forward --address 0.0.0.0 ...`로 모든 인터페이스에 바인딩하거나, `curl -kv https://localhost:8080`으로 WSL 내부에서 응답이 오는지 먼저 확인.

> 서비스 차트의 `image.tag` 에 해당하는 이미지가 GAR 에 아직 없으면 Pod 는 `ImagePullBackOff` 로 떨어진다. GitHub Release 를 publish 하면 워크플로가 이미지를 빌드·푸시하고 `image.tag` 를 commit-back 해 ArgoCD 가 self-heal 한다 — 절차는 [가이드 06](./06-image-build-push.md) 참고.

---

## 알려진 제약

### 노드 SA의 IAM 역할 미부여

전용 SA(`prod-gke-2601-team4-node`)는 만들지만, IAM 역할 바인딩은 Terraform에서 생략되어 있다. 이유: 본인 계정에 `setIamPolicy` 권한이 없는 환경에서 apply가 실패하기 때문.

영향:
- 클러스터·Pod·ArgoCD 등 K8s 내부 동작 — **정상**
- 노드 로그/메트릭이 Cloud Logging/Monitoring으로 송신 — **안 됨**
- Artifact Registry에서 이미지 풀 — **권한 거부** (gcr.io, docker.io 공개 이미지는 권한 없이도 풀 가능)

필요 시 관리자가 콘솔에서 SA에 다음 5개 역할 수동 부여:

| Role | 용도 |
|---|---|
| `roles/logging.logWriter` | 노드/Pod 로그 → Cloud Logging |
| `roles/monitoring.metricWriter` | 메트릭 송신 |
| `roles/monitoring.viewer` | HPA 등이 메트릭 조회 |
| `roles/stackdriver.resourceMetadata.writer` | GKE 메타데이터 갱신 |
| `roles/artifactregistry.reader` | Artifact Registry 이미지 풀 |

### `google_container_cluster` 의 광범위한 `lifecycle.ignore_changes`

provider 5.40 → 5.45+ 의 schema 차이로 cluster default 가 plan diff 로 잡혀 force replace 가 발생한다. PoC 에서는 cluster 자체를 변경할 일이 없으므로 `gke.tf` 에 다음 필드들을 ignore 처리해 둔 상태:

```
node_config, ip_allocation_policy, addons_config, master_auth,
binary_authorization, database_encryption, default_snat_status,
cluster_autoscaling, logging_config, network, subnetwork
```

영향:
- 위 12개 필드는 코드에서 바꿔도 Terraform 이 변경을 적용하지 않는다.
- `name_prefix`, `release_channel`, `deletion_protection`, `resource_labels` 등 ignore 목록 밖 필드는 정상 동작.
- 운영 전환 시 provider 버전을 한 줄로 고정(`= 5.45.x`)하고 ignore 목록을 비워야 의도적 변경이 다시 반영된다.

### 운영 환경 아님

- **재현성**: 로컬 state. 팀 협업 시 GCS backend로 전환 필요.
- **네트워크 격리**: default VPC. 운영용은 별도 VPC + NAT + private cluster 필요.
- **HA**: zonal cluster (control plane 무료). 운영용은 regional cluster.
- **인증**: ArgoCD admin 단일 계정 + dex SSO 비활성화. 운영용은 IDP 연동 필수.

---

## 자원 정리 — [자원 생성 전용 · 스킵]

```bash
cd prototype/terraform
terraform destroy
# yes 입력 → 약 5분
```

GKE 클러스터·노드풀·SA가 모두 제거된다. K8s 자원(namespace, Helm release)은 클러스터와 함께 사라진다.

> **`name_prefix` 등 자원 이름을 변경한 후 apply하면** 이름이 ForceNew 필드라 destroy + recreate가 발생한다. 같은 모듈에 클러스터+k8s 자원이 함께 있으면 plan/destroy 단계에서 kubernetes provider가 cluster endpoint를 "known after apply"로 인식해 `dial tcp 127.0.0.1:80: connect: connection refused` 에러가 날 수 있다. 그 경우:
> ```bash
> terraform state rm kubernetes_namespace.argocd
> terraform state rm helm_release.argocd
> terraform destroy
> terraform apply
> ```
> K8s 자원은 클러스터와 함께 사라지므로 state에서 떼어내도 안전.

---

## 변수 커스터마이즈 — [자원 생성 전용 · 스킵]

다른 팀/프로젝트로 분리하거나 이름을 바꾸고 싶으면 `terraform.tfvars`에 다음 변수를 추가한다. 자원이 이미 생성된 상태에서는 destroy + recreate가 발생한다는 점에 주의.

```hcl
name_prefix       = "prod-gke-2601-team4"   # 자원 이름 베이스 (cluster·node pool·SA에 사용)
env               = "prod"                  # 라벨
cohort            = "2601"                  # 라벨
team              = "team4"                 # 라벨
region            = "asia-east2"
zone              = "asia-east2-a"
node_count        = 3
node_machine_type = "e2-standard-4"
node_disk_size_gb = 50
```
