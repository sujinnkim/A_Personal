# 프로젝트 인수인계 (세션 간 이어달리기 노트)

기준일: 2026-07-13
목적: 다른 세션에서 **구멍 없이** 이어서 작업하기 위한 단일 진입점. 설계(docs·report) · 구현(prototype) · 검증(verification) 세 축의 현재 상태와 다음 할 일을 한곳에 정리한다.

관련 상세 문서: 프로토타입·검증 딥다이브는 `prototype/verification/prototype-review-status.md`(핸드오프 원본, 5절 재개 절차·6절 구현 이력).

---

## 0. 세 축 상태 한눈에

| 축 | 상태 | 남은 것 |
|----|------|--------|
| 설계 (docs·report) | 4·5장 정합 수정·동기화 완료 | 다이어그램 PNG 재렌더(수작업), 5.2/5.3 실측 서술(실행 후) |
| 구현 (prototype) | AS-01~09 코드 구현 완료 | 컴파일·실행 검증(환경 복구 후) |
| 검증 (verification) | IV-01~05 스크립트·환경 구성 완비 | 실측 0건 → 실행·수집, replica 오설정 선결 |

핵심 원칙: **"구현 완료 ≠ 검증 완료".** 신규 구현분(AS-06/02/01)은 코드만 있고 컴파일·실행·부하측정은 아직 안 됐다. 문서는 이를 △(구현·전용 부하 IV 미실행)로 정직하게 구분해 뒀다. 실측 없이 5.3 결과를 채우지 말 것.

---

## 1. 이번 세션에서 한 일

1. **report 4·5장 리뷰** — 오류 3건 발견·수정
   - AS-06 상태 3중 모순(4.2.1 구현·4.1 △·4.2.2/5.1 미구현) → 실제 구현으로 통일
   - docs↔report desync(표 61 AS-07 △, 표 62 IV-05 누락) → docs 4.1.3 기준 동기
   - 표 61 행 순서(AS-06이 AS-05보다 먼저) → 정정
2. **프로토타입 검토(prototype-agent)** — AS-01~09 실측 근거 매트릭스, 블로커 도출
3. **AS-06·AS-02·AS-01 코드 구현**(prototype-agent, 사용자 지시) — 상세 `prototype-review-status.md` 6절
4. **docs·report 동기화** — "구현 예정/미구현" 전량 제거, `ThrottlingFilter→ThrottlingInterceptor` 명칭 통일, 표 61/62 재정합

---

## 2. 설계 (docs·report)

**완료**
- report 4.2 하위파일 분할(4.2.1 / 4.2.2-모듈뷰/*.md / 4.2.3), 그림 45–59·표 57–104 번호 연속, 중복 0
- docs·report 4·5장에서 AS-06 "예정/미구현" 제거. 표 61: AS-06 △(구현·전용IV 미실행)·AS-07 O·IV-05·AS-01 비고 ArchUnit. 표 62: IV-05 행 추가, IV-04→QA-05·IV-05→QA-04
- 4.2.2.5 클래스 다이어그램에 실제 스로틀링 클래스(PeakDetector·ThrottlingInterceptor·ThrottlingConfig) 반영
- 편집한 report 파일 em-dash·수평선 0, mermaid 균형 확인

**남은 것 (수작업/실행 후)**
- **다이어그램 PNG 재렌더**(mermaid는 고쳤으나 이미지 미반영): 그림 45(4.1 개념도), 46(overall-cnc), 48a(auth seq), 49(prewarm seq), 52(module-view), 57(class-config)
  - 특히 `report/drawio/4.1-tobe-architecture.drawio`는 THROT 노드가 **AS-05·Throttling Filter**로 이중 stale → 재렌더 전 소스부터 수정 필요
  - mermaid 변경 시 `memo/image-file-mapping.md` 정합 유지(경로 변경은 없음)
- **5.2 구현·검증 환경 서술 / 5.3 실측 결과** — 5.2는 지금도 작성 가능(코드·compose·k6·stub 실재), 5.3은 실행 후에만(현재 `docs/06-verification/5.2-implementation-and-results.md`는 "(작성 예정)")

---

## 3. 구현 (prototype)

AS-01~09 전부 코드 구현. 상세 상태·근거 파일은 `prototype/verification/prototype-review-status.md` 2절 표.

이번에 신규/배선한 3종(2026-07-13):
- **AS-06 Throttling**: `config/PeakDetector`(시간창 08:30~09:30·12:30~13:30 + setActive), `config/ThrottlingInterceptor`(HandlerInterceptor, 피크·비핵심 한정 Bucket4j 초당 1,000 → 429), `config/ThrottlingConfig`(WebMvcConfigurer), `@ThrottleExempt`(join·conference-token·meeting start 면제). Bucket4j 8.10.1
- **AS-02**: MM 경로(`getConferenceToken`·`startMeeting`) `@Async("externalCallExecutor")`+CompletableFuture, `ContextPropagatingTaskDecorator`(MDC). CB는 어댑터 동기 유지
- **AS-01**: `test/architecture/DomainBoundaryArchTest`(ArchUnit 1.3.0)

**미검증(중요)**: 이 환경엔 JDK17/Gradle/Docker 미가용 → `gradle build` 미실행. Bucket4j·ArchUnit 좌표/API는 가정. 컴파일부터 확인 필요.

---

## 4. 검증 (verification)

- IV-01~05 k6 스크립트·docker-compose·stub-server·모니터링 구성 완비
- **실측 0건**(`prototype/k6/results/`에 `.gitkeep`만) → 5.3 결과 데이터 없음
- **블로커 ①(환경)**: Docker 데몬 미기동(Rancher Desktop 실행 + dockerd(moby) + WSL Integration)
- **블로커 ②(구성 의심)**: docker-compose replica가 공식 mariadb 이미지에 Bitnami 복제변수 사용 → 빈 replica 의심. IV-02·IV-05 read 경로가 여기서 갈림. 스택 기동 즉시 진단(부하 전):
  ```
  docker compose exec mariadb-replica mariadb -uapp -papppass -e "SHOW TABLES IN meetingdb;"
  ```

재개 절차(요약, 상세는 status 파일 5절):
1. Docker 데몬 복구 → `docker ps`
2. jar 빌드(설치 불필요): `docker run --rm -v "$PWD/front-api":/app -w /app gradle:8.10.2-jdk17 gradle clean build -x test` (stub-server도)
3. `docker compose --profile monitoring up -d --build` → 헬스 확인
4. **replica 진단**(부하 전 필수)
5. 스모크 → k6 IV-01~05(IV-03은 워밍 1~2분 후) → `k6/results/` 수집
6. 컴파일 시 신규 구현분 Bucket4j·ArchUnit 좌표 문제 나면 조정

---

## 5. 다음 세션 착수 체크리스트 (구멍 없이)

- [ ] Docker 데몬 복구(블로커 ①)
- [ ] `gradle build`로 **신규 구현분 컴파일 검증** → `DomainBoundaryArchTest` 통과(인프라 불필요) → 컨텍스트 로딩
- [ ] **replica 진단**(블로커 ②). 빈 replica면 구성 수정이 IV-02/05 선행 조건
- [ ] IV-01~05 실측 → `k6/results/` 저장(실측만 기록, 임의 기입 금지)
- [ ] 실측 기반으로 **docs·report 5.2/5.3** 작성(설계·환경은 지금도 가능)
- [ ] 컴파일에서 클래스명/시그니처 바뀌면 **docs·report 모듈뷰(4.2.2.5)·실행뷰 동기**(docs 원본과 report 파생 함께)
- [ ] 변경된 mermaid **PNG 재렌더** + `memo/image-file-mapping.md` 정합(그림 45·46·48a·49·52·57, drawio 4.1 stale 수정)

---

## 6. 이어서 작업할 때 지켜야 할 규칙 (핵심만)

- **docs(원본) ↔ report(파생) 항상 함께 수정.** report-only 수정은 동기화로 유실된다
- **흐름·구조는 다이어그램 최우선 근거.** 구두 설명보다 실제 시퀀스/모듈 다이어그램
- **실측만 기록.** 검증 결과는 실행 산출물로만
- report 파일: em-dash(` — `)·수평선(`---`) 금지, 한국어 삽입구는 괄호/콜론
- 요청 범위 외 임의 수정 금지, 무응답 시 대기

포인터: 상세 프로토타입·검증 노트 `prototype/verification/prototype-review-status.md` / 평가 기준·컨벤션은 사용자 메모리(MEMORY.md) 참조.
