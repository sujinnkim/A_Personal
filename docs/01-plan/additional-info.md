# As-is 미팅 포털 서버 비즈니스 보충 설명

> 이 파일은 아키텍처 설계 문서 작성 시 항상 참조한다.
> 과제 전반의 포커스: **요청 집중 구간 대응** (피크 시간대 트래픽 집중으로 인한 구조적 문제 해결)

---

## 1. 서비스 환경 구성

동일한 단일 형상이 아래 4개 환경에 배포되며, 환경별 설정은 YML 파일로 분리되어 있다.

| 환경 | 대상 |
|---|---|
| Knox | 사내 임직원 |
| Brity 대외 | B2C: Brity Works에 가입한 외부 기업 임직원 |
| Brity 공공 (행망) | 공공기관: 행정망 |
| Brity 공공 (공망) | 공공기관: 공공망 |

환경별 요구사항이 점점 달라지면서 비즈니스 로직 내부에 환경별 분기 처리가 누적되고 있으며, 환경별 릴리즈 일정도 상이하여 단일 형상 관리의 복잡도가 증가하고 있다.

---

## 2. API 구조 및 배포 형태

단일 코드베이스지만 URI prefix에 따라 API 역할이 분리되며, 역할별로 별도 인스턴스로 배포된다.

| URI prefix | API 인스턴스 | 주요 역할 |
|---|---|---|
| `/front` | front-api | 사용자 요청 수신 · 유효성 검사 · 전처리 |
| `/server` | server-api | VC/AC 외부 벤더 연동, 참석자 상태 피드백(Feedback) 처리 |
| `/admin` | admin-api | 관리 기능 |

**모든 API 인스턴스는 동일한 DB를 공유한다.**

### front-api 역할 (단순 게이트웨이 아님)
front-api는 사용자 요청에 대해 아래를 직접 수행한다:
- 미팅 타입 결정
- 권한 확인 (SDC 조회)
- 미팅 유효성 검사
- 참석자 유효성 검사
- 요청 전처리

이후 Meeting Manager에 회의 개설 요청을 전달한다. Meeting Manager는 WC 채널 설정(cPaaS 경유)을 처리하고, VC/AC가 포함된 경우 개설 요청을 server-api에 전달한다. VC/AC 컨퍼런스 생성은 server-api가 각 벤더 서버에 직접 요청하여 처리한다.

### Meeting Manager
front-api로부터 회의 개설 요청을 수신하여 WC 채널 설정(cPaaS/channel-manager/ic-controller 경유)을 처리한다. VC/AC가 포함된 경우 개설 요청을 server-api에 전달하며, VC/AC 컨퍼런스 생성 자체는 server-api가 각 벤더 서버(VC서버·AC서버)에 요청하여 처리한다. Meeting Manager는 VC/AC 생성에 직접 관여하지 않는다.

---

## 3. 미팅 타입 (Meeting Type)

회의 생성 시 요청 파라미터로 전달하거나, 미전달 시 요청자 권한 기반으로 시스템이 결정한다. 타입은 아래 코드의 조합으로 구성된다.

| 코드 | 명칭 | 설명 |
|------|------|------|
| w | WC (Web Conference) | 일반 웹 화상 회의 |
| v | VC (Video Conference) | 화상회의실 장비 초대 가능 |
| a | AC (Audio Conference) | 전화 참석자 초대 가능 |

### 미팅 타입별 권한 처리

- **WC / VC 권한**: SDC 권한으로 관리 → DB 조회로 확인
- **AC 권한**: 로그인 시 AC 서버를 호출하여 확인 → 결과를 DB에 저장. 이후 AC 권한은 DB 조회로 사용

---

## 4. 회의 생성 흐름 (타입별 분기)

### WC 포함 회의 (w, wv, wa, wva 등)

```
User → front-api → Meeting Manager → cPaaS (채널 생성 · confNo-channelID 매핑 · actorID 매핑)
```

Meeting Manager가 cPaaS(channel-manager/ic-controller)를 통해 WC 채널을 생성한다. WC 전용 회의는 server-api 호출 없이 Meeting Manager와 cPaaS 사이에서 완결된다.

### VC 또는 AC 포함 회의 (v, a, va, wva 등)

```
User → front-api → Meeting Manager → server-api → VC서버 / AC서버
```

- Meeting Manager가 server-api에 개설 요청을 전달하고, server-api가 VC서버·AC서버에 각각 개설을 요청한다.
- **외부 연동 실패 또는 정합성 오류 발생 시 → server-api가 에러 반환 → 전체 회의 생성 요청 실패**

---

## 5. 참석자 상태 피드백 흐름 (Feedback Flow)

사용자 API 요청이 아닌 **백엔드 발생 역방향 흐름**이다.

회의 중 참석자 상태 변경(퇴장, 연결 끊김 등)이 발생하면:

```
cPaaS → server-api → DB update
```

- cPaaS가 server-api에 직접 콜백한다 (Meeting Manager를 경유하지 않는다).
- front-api를 거치지 않는다.
- "피드백(feedback)"이라고 부른다.
- server-api가 이 역방향 흐름의 처리 주체다.
- **요청 집중 구간에 사용자 입장(write)과 피드백(write)이 동시에 server-api · DB에 유입된다.**

---

## 6. 인증 및 회원 조회 흐름

- 로그인(`POST /sign-in`) → access-token 발급. 이후 API 호출 시 헤더에 세팅하여 인증에 사용.
- 로그인 후 웹·모바일 플랫폼은 **무조건** 회원 조회 API(`GET /members/{email}`)를 호출한다.
  - `basic`: 이름, 전화번호 등 기본 정보
  - `detail`: AC서버·Copilot 서버를 호출하여 AC 권한, LLM 사용 가능 여부 등 갱신
  - `sdc`: WC/VC 관련 권한 묶음 (녹화 가능 여부, 대화 기록 사용 가능 여부, 모바일 입장 가능 여부 등)

### 권한 갱신 실패 시 Fallback 동작

`detail` 호출 시 외부 서버(AC서버, Copilot Admin 서버) 중 하나 이상이 timeout 또는 오류를 반환하면, **해당 서버의 권한만 DB에 저장된 기존 값으로 대체**하여 반환한다. 전체 요청이 실패로 반환되지 않는다.

- **결과**: 반환된 권한이 현재 실제 권한과 다를 수 있다.
  - 권한이 실제보다 넓게 반환된 경우 → 해당 외부 서버에 실제 요청 시 권한 없음 오류
  - 권한이 실제보다 좁게 반환된 경우 → 사용자가 실제 사용 가능한 기능을 이용하지 못함

---

## 7. 권한(SDC) 구조

- SDC는 회사 계약 단위로 관리자가 설정하는 정책 묶음이다.
- WC/VC 회의 개설 시 사용자에게 매핑된 SDC가 필요하다.
- 회의 예약 페이지 진입 시 SDC 권한을 확인하여 예약 옵션(오픈회의 체크박스, 회의실 선택 창 등)을 구성한다.
- SDC는 변경 빈도가 낮지만 서버사이드 캐시 없이 매 요청마다 조회된다.

---

## 8. 외부 호출 기술 스택 및 타임아웃 설정

포털 서버의 외부 서버 호출은 **Feign** 클라이언트(동기)를 사용하며, 다음 타임아웃이 전체 외부 호출에 일괄 적용된다.

| 항목 | 설정값 |
|------|--------|
| connect timeout | 1,000ms |
| read timeout | 3,000ms |

- 서버별 타임아웃 세분화 없이 단일 값이 모든 외부 호출에 적용된다.
- 외부 서버 장애 또는 응답 지연 시 read timeout 만료(3,000ms)까지 스레드가 점유 상태로 유지된다.
- VC + AC 벤더 서버를 순차 호출하는 경우 최대 **6,000ms(3,000ms × 2)** 스레드가 고정될 수 있다.
- `GET /members/{email}`의 `CompletableFuture.allOf()` 병렬 호출은 가장 느린 서버의 read timeout(최대 3,000ms)에 묶인다.

---

## 9. 회의 입장 흐름 (요청 집중 구간의 핵심 경로)

- **입장 가능 회의 상태**: 예약 상태(scheduled) 또는 진행 중(in-progress) 모두 입장 가능하다.
- **입장 처리 순서**:
  1. DB에서 입장 가능 여부 확인(초대 참석자 여부 또는 오픈 회의 여부 확인).
  2. 오픈회의 셀프 참석의 경우 front-api가 participants 테이블에 INSERT (초대 참석자는 이미 레코드 존재, write 없음).
  3. `/conference-token` API로 인미팅 전용 토큰 발급.
  4. Meeting Manager에 Feign 동기 호출(read timeout 3,000ms)로 참석자 입장 관련 정보 조회 → front-api에서 MM 응답값과 다른 값들을 조합하여 wyzProParam 생성 → front-api가 웹/모바일에 반환 → 런처를 통해 클라이언트 실행 시 wyzProParam 전달.
  5. 클라이언트가 cPaaS에 접속 성공하면 cPaaS → Meeting Manager → server-api (GET /entrance-info) 경로로 입장 정보가 전달되며 server-api가 참석자 상태를 DB에 업데이트한다.
- **DB write 정리**:
  - 입장 API 호출 시: 셀프 참석자만 front-api가 participants INSERT. 초대 참석자는 write 없음.
  - 입장 성공 후 피드백: cPaaS → Meeting Manager → server-api (GET /entrance-info) → 모든 참석자 participants 상태 UPDATE.
  - 퇴장·연결 끊김: cPaaS → server-api → participants 상태 UPDATE.
- **요청 집중 시 우선순위**: 회의 입장 처리(conference-token 발급 + Meeting Manager 조회 + wyzProParam 조립)가 최우선.
- **아웃미팅**: 포털 웹·모바일 환경 (회의 미입장 상태)
- **인미팅**: PC 클라이언트 또는 모바일 클라이언트 환경 (회의 중). 인미팅에서 퇴장 시 cPaaS를 통해 포털 server-api에 역방향으로 퇴장 이벤트가 전달된다.

---

## 10. 오픈회의 및 익명 참석자

- 오픈회의: 초대 없이 회의 번호 또는 URL로 입장 가능한 회의.
  - 로그인 후 입장 시 본인 회원 정보로 입장.
  - 비로그인 입장 시 서버가 랜덤 이메일(`랜덤id@meeting.guest`)을 생성하고 임시 회원으로 DB INSERT하여 익명 참석자로 처리.

---

## 11. 트래픽 집중 패턴 (예측 가능)

- **일별 반복 패턴**: 오전 9시, 오후 1시 등 업무 시작 시간대에 로그인과 회의 입장이 동시에 집중.
- **오후 정시 burst**: 회의 시작이 많이 몰리는 시간에 순간적인 트래픽 급증 발생.
- **이벤트성 burst**: 8만 명 규모 스트리밍 서비스처럼 방송 시작 직전 대규모 동시 입장.
- **예측 가능성**: 예약된 회의 데이터와 참석자 수를 기반으로 트래픽 집중 시점과 규모를 사전에 예측할 수 있다 → Predictive Pre-warming 설계 전략과 연결.

---

## 12. 미팅 실시간 상태 특성 (CQRS 연관)

- 하나의 미팅에 수십~수천 명이 동시 참여 가능 (Zoom 유사 실시간 미팅 서비스).
- 상태 변경(Command)이 매우 빈번: 입장, 퇴장, 대기실 승인, 음소거 상태 변경 등.
  - 사용자 입장 요청(front-api 경유)과 참석자 상태 피드백(cPaaS → server-api 경유) **두 경로에서 동시에** write가 발생한다.
- 조회 요청(Query)도 동시에 다수 발생: 참석자 목록, 대기실 인원, 미팅 상태, 권한 정보 등.
- 현재 Command/Query가 동일 DB·도메인 모델 공유 → 대규모 미팅 시작 시점에 write/read 충돌, lock 경합, 참석자 목록 조회 latency 증가 발생.

---

## 13. 회의 예약 및 자동 시작

- **시간대 중복 허용**: 동일 시간대에 얼마든지 복수의 회의를 예약·개설할 수 있다. 시간대 중복에 따른 제약이 없다.
- **배치 시스템**: 예약 회의의 자동 시작은 배치 시스템이 담당한다. 배치 시스템이 예약 데이터를 읽어 예약 시각에 회의 시작을 자동 트리거한다.
- **즉시 개설**: 예약 없이 사용자가 직접 회의를 즉시 시작할 수도 있다. 예약 기반 자동 시작과 즉시 개설은 동일한 회의 시작 API(`POST /meetings`)를 통해 처리된다.

---

## 14. 참석자 검색 및 초대

- 참석자 검색은 **Knox 임직원 포탈 서버**를 통해 이루어진다. 사용자가 초대할 참석자를 Knox에서 검색하고 선택한다.
- 알림 발송(IN서버/메일/push)은 예약 또는 초대 시 설정한 옵션에 따라 **선택적**으로 수행된다. 회의 시작 시에도, 참석자 초대 시에도 알림 발송은 필수가 아니다.

---

## 15. 외부 연계 서버 목록

포털 서버와 연동하는 주요 외부 서버는 다음과 같다.

| 서버 | 역할 |
|------|------|
| WC서버 (약 10개 지역) | 웹 컨퍼런스 시작·종료, 입장 파라미터 생성 |
| VC서버 (약 3개 지역) | 비디오 컨퍼런스 시작·종료, 회의실 참석 처리 |
| AC서버 | 오디오 컨퍼런스 시작·종료, 전화 참석, AC 권한 갱신 |
| Knox 임직원 포탈 서버 | 참석자 검색 |
| cPaaS | 인미팅 클라이언트 퇴장·연결 끊김 이벤트 역방향 전달 |
| Copilot Admin 서버 | LLM 권한·용어사전 권한 갱신 |
| IN서버 | SMS 알림 발송 |
| 메일 서버 | 이메일 알림 발송 |
| push 서버 | 모바일 push 알림 발송 |
