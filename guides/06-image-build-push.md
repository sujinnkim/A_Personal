# 컨테이너 이미지 빌드 + GAR 푸시 가이드

GKE/ArgoCD 부트스트랩(가이드 05) 직후, 서비스 7개 이미지를 Artifact Registry 에 올려 ArgoCD 가 ImagePullBackOff 에서 빠져나오게 만드는 절차.

빌드·푸시·helm tag 갱신은 GitHub Actions 워크플로(`.github/workflows/release.yml`)가 GitHub **Release publish 이벤트**를 받아 자동 수행한다. 로컬에서 docker build 를 돌릴 필요가 없다.

## 결과물

- GAR repository (`asia-east2-docker.pkg.dev/architect-certification-289902/docker-repo-2601-team4`)에 7개 이미지가 release tag 로 push
  - `gateway`, `auth`, `common-data`, `fuel-subsidy`, `child-care`, `disaster-relief`, `senior-pension`
- helm 차트 7개의 `image.tag` 가 동일 tag 로 갱신되어 main 에 자동 commit
- ArgoCD self-heal 이 변경을 감지해 새 이미지로 Pod 재배포

## 사전 조건

- 가이드 05 의 `terraform apply` 완료:
  - GKE 클러스터 + 노드풀
  - ArgoCD + App-of-Apps
  - **GAR repository** (`google_artifact_registry_repository.poc`)
- GitHub repository 의 admin 권한 (Secret 등록 + workflow 권한 설정)

---

## 1. 노드 SA 에 `artifactregistry.reader` 부여 — [한 번만, 관리자 권한 필요]

GAR 의 이미지는 노드 SA(`prod-gke-2601-team4-node@architect-certification-289902.iam.gserviceaccount.com`)가 pull 권한이 있어야 클러스터가 가져올 수 있다. 과정용 제한 계정에는 `setIamPolicy` 권한이 없어 Terraform·본인 계정으로는 binding 을 못 한다 — **`setIamPolicy` 권한이 있는 계정(과정 관리자 등)** 이 아래 중 하나를 실행한다.

```bash
# repo 레벨 (최소 권한 권장)
gcloud artifacts repositories add-iam-policy-binding docker-repo-2601-team4 \
  --location=asia-east2 \
  --project=architect-certification-289902 \
  --member="serviceAccount:prod-gke-2601-team4-node@architect-certification-289902.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.reader"

# 또는 프로젝트 레벨 (다른 repo 도 커버)
gcloud projects add-iam-policy-binding architect-certification-289902 \
  --member="serviceAccount:prod-gke-2601-team4-node@architect-certification-289902.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.reader"
```

콘솔로: **Artifact Registry → docker-repo-2601-team4 repo → PERMISSIONS → ADD PRINCIPAL** → SA 입력 → Role `Artifact Registry Reader`. (로그·메트릭 등 다른 role 도 누락이면 가이드 05 "알려진 제약 — 노드 SA의 IAM 역할 미부여" 의 5개를 한꺼번에.)

권한 부여 후 이미 `ImagePullBackOff` 로 떨어진 Pod 는 kubelet 의 exponential backoff 때문에 곧장 복구가 안 될 수 있다. 즉시 재시도하려면:

```bash
kubectl delete pod -l app.kubernetes.io/managed-by=Helm   # 또는 -l app.kubernetes.io/name=gateway 등
```

---

## 2. GitHub Actions 용 SA + key 발급 — [한 번만]

워크플로가 GAR 에 push 하려면 `roles/artifactregistry.writer` 권한이 붙은 SA 의 JSON key 가 필요하다. 본인 계정에 SA 생성·IAM 부여 권한이 없다면 관리자에게 요청.

```bash
PROJECT_ID=architect-certification-289902
SA_NAME=gha-image-push-2601-team4
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

# SA 생성
gcloud iam service-accounts create "$SA_NAME" \
  --project="$PROJECT_ID" \
  --display-name="GitHub Actions image push (team4)"

# GAR writer 부여 (repo 레벨)
gcloud artifacts repositories add-iam-policy-binding docker-repo-2601-team4 \
  --location=asia-east2 \
  --project="$PROJECT_ID" \
  --member="serviceAccount:${SA_EMAIL}" \
  --role="roles/artifactregistry.writer"

# JSON key 발급 — 파일명은 .gitignore 의 *-key.json 패턴에 걸리도록 끝을 -key.json 으로
gcloud iam service-accounts keys create gha-key.json \
  --iam-account="$SA_EMAIL"
```

> `gha-key.json` 같은 SA private key 는 절대 commit 금지. `.gitignore` 에 `*-key.json`, `gha-key.json` 패턴이 들어가 있지만 안전을 위해 별도 디렉토리에 두는 것을 권장.

---

## 3. GitHub Secret 등록 — [한 번만]

발급한 JSON key 전체를 GitHub repo Secret 에 넣는다.

GitHub Repo 페이지 → **Settings → Secrets and variables → Actions → New repository secret**:

- Name: **`GCP_SA_KEY`** (워크플로 코드와 정확히 일치해야 함)
- Secret: `gha-key.json` 의 **파일 내용 전체** 를 그대로 붙여넣기 (JSON 객체)
- Add secret

확인: Secrets 목록에 `GCP_SA_KEY` 가 보이면 OK. 값은 다시 조회할 수 없으므로 잃어버리면 재발급.

---

## 4. Workflow 쓰기 권한 활성화 — [한 번만]

`bump-values` job 이 helm `values.yaml` 의 `image.tag` 를 갱신하고 main 에 push 한다. 기본값(read-only) 으로는 push 가 거부된다.

GitHub Repo 페이지 → **Settings → Actions → General → Workflow permissions**:

- **Read and write permissions** 선택
- Save

---

## 5. Release 발행 → 자동 빌드·배포

준비가 끝났으면 GitHub Release 를 publish 하기만 하면 된다. CLI(`gh`) 또는 GitHub UI 어느 쪽이든.

### gh CLI

```bash
# 새 tag 부여 + Release 발행 (예: 0.0.1)
gh release create 0.0.1 \
  --title "0.0.1" \
  --notes "first deployable build" \
  --target main
```

### GitHub UI

Repo → **Releases → Draft a new release**:

- Choose a tag: `0.0.1` (없으면 `Create new tag: 0.0.1 on publish`)
- Target: `main`
- Title / Description 자유 입력
- **Publish release** 클릭

`release: published` 이벤트가 발생하면 `.github/workflows/release.yml` 이 자동 트리거된다.

워크플로 흐름:

1. `build-push` job 7개가 병렬 실행 — 각 서비스를 `prototype/backend/Dockerfile` 로 빌드해 `asia-east2-docker.pkg.dev/architect-certification-289902/docker-repo-2601-team4/<svc>:<tag>` 로 push.
2. 7개가 모두 성공하면 `bump-values` job 이 7개 `values.yaml` 의 `image.tag` 를 release tag 로 갱신해 `release: helm image.tag <tag> 로 갱신 [skip ci]` commit 으로 push.
3. ArgoCD 가 다음 reconcile 주기(default 3분)에 변경을 감지해 새 이미지로 Pod 재배포.

진행 상황은 **Repo → Actions → Release - build images & bump helm tags** 에서 확인.

---

## 6. 결과 확인

```bash
kubectl get pods
```

```
NAME                               READY   STATUS    RESTARTS   AGE
auth-...                           1/1     Running   0          1m
child-care-...                     1/1     Running   0          1m
common-data-...                    1/1     Running   0          1m
disaster-relief-...                1/1     Running   0          1m
fuel-subsidy-...                   1/1     Running   0          1m
gateway-...                        1/1     Running   0          1m
senior-pension-...                 1/1     Running   0          1m
postgres-0                         1/1     Running   0          (older)
flyway-*                           0/1     Completed 0          (older)
```

서비스 7개가 모두 Running 이면 완료. 게이트웨이 헬스체크:

```bash
kubectl port-forward svc/gateway 8080:8080
# 다른 터미널:
curl http://localhost:8080/actuator/health
```

`{"status":"UP"}` 가 나오면 정상.

---

## 트러블슈팅

### 워크플로 자체가 실행되지 않는다

- 이벤트가 `release: published` 인지 확인 (단순 tag push 로는 트리거되지 않음).
- Actions 가 비활성화돼 있지는 않은지: Settings → Actions → General → **Allow all actions and reusable workflows**.

### `bump-values` job 이 `Permission denied (push)` 로 실패

4번 절차의 Workflow permissions 가 `Read and write` 가 아닌 경우. 변경 후 같은 Release 의 워크플로를 **Re-run failed jobs** 로 다시 실행.

### `denied: Permission "artifactregistry.repositories.uploadArtifacts" denied`

GitHub Secret `GCP_SA_KEY` 가 비어 있거나, SA 에 `roles/artifactregistry.writer` 가 부여되지 않은 경우. 2번 절차 재확인.

### `ImagePullBackOff` 상태가 안 풀린다

- 노드 SA 에 `roles/artifactregistry.reader` 가 부여됐는지 IAM 콘솔에서 재확인.
- kubelet 의 image pull retry 가 exponential backoff 라 권한 부여 직후엔 시간이 걸릴 수 있다. Pod 를 한 번 지우면 즉시 재시도:

  ```bash
  kubectl delete pod -l app.kubernetes.io/name=gateway
  ```

### `manifest unknown` 또는 Pod 에서 image not found

푸시 직후 ArgoCD 가 너무 빨리 sync 했고 image 가 아직 GAR 에 전파 중일 수 있다. 잠시 후 Pod 재시작.

### 같은 tag 로 재발행하고 싶다

GitHub Release 를 삭제 + 같은 tag 로 다시 publish 하면 워크플로가 재실행되고, GAR 의 동일 tag 가 새 digest 로 덮어쓰인다. 단 helm `image.tag` 값은 동일하므로 ArgoCD 가 sha 변화를 감지하지 못해 Pod 가 재시작되지 않을 수 있다. 그 경우 `kubectl rollout restart deployment/<svc>` 로 강제 회전.
