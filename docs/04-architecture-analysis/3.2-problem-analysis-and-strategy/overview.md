# 3.2. 아키텍처 문제 분석 및 설계 전략

## 개요

3.1 아키텍처 드라이버(AD)가 *무엇이 요구되는지*를 정리한 것에 대응해, 본 절은
*어떻게 푸는지*를 결정한다. 각 AD에 대해 먼저 충족을 위한 **기본 설계 전략(AS)**
을 결정하고, 그 기본 결정이 제약사항과 결합하여 새로 부각되는 문제에 대해
**파생 설계 전략**을 연쇄적으로 결정한다.

## 대안 평가 원칙

각 AS는 **대척점이 명확한 둘 이상의 대안**을 비교하여 선정한다.

## 설계 전략 목록

| AS | 전략명 | 해결 이슈 | 핵심 드라이버 |
|----|--------|---------|-------------|
| AS-01 | MSA / 도메인 서비스 분리 | ISSUE-04, ISSUE-07, ISSUE-08 | AD-03 |
| AS-02 | 메시지 큐 기반 비동기 처리 | ISSUE-01, ISSUE-05, ISSUE-06 | AD-02, AD-04 |
| AS-03 | 캐시 기반 응답 속도 개선 | ISSUE-02, ISSUE-05, ISSUE-09 | AD-01 |
| AS-04 | 요청 우선순위 큐 | ISSUE-01, ISSUE-03 | AD-02 |
| AS-05 | Eager Response + 비동기 후처리 | ISSUE-01, ISSUE-02, ISSUE-05 | AD-02, AD-04 |
| AS-06 | Time-based Throttling | ISSUE-03, ISSUE-09 | AD-02, AD-04 |
| AS-07 | Predictive Pre-warming | ISSUE-09 | AD-01, AD-02, AD-04 |
| AS-08 | CQRS | ISSUE-07 | AD-03 |
| AS-09 | Bulkhead 격리 | ISSUE-01, ISSUE-04, ISSUE-06 | AD-03, AD-04 |
| AS-10 | Circuit Breaker / Fallback | ISSUE-02, ISSUE-06, ISSUE-08 | AD-04 |
| AS-11 | Anti-Corruption Layer (ACL) | ISSUE-08 | AD-09, AD-10 |

## 기본 전략과 파생 전략의 관계

일부 AS는 다른 AS의 설계 결정이 선행되어야 적용 가능하다. 아래 파생 관계를 기준으로 적용 순서를 결정한다.

```
AS-01 (도메인 모듈 분리)
  ├── AS-09 (Bulkhead)     — AS-01이 설정한 도메인 경계별로 커넥션 풀 격리를 구현
  ├── AS-08 (CQRS)         — AS-01의 도메인 경계 내에서 Command/Query 모델 분리
  └── AS-11 (ACL)          — AS-01의 외부 연계 모듈 분리를 구현하는 패턴

AS-02 (메시지 큐 비동기)
  └── AS-05 (Eager Response) — AS-02의 비동기 처리 기반 위에서 응답 패턴 정의

AS-03 (캐시)
  └── AS-07 (Pre-warming)    — AS-03의 캐시 인프라가 존재해야 선제 적재 가능

AS-07 (Pre-warming)
  └── AS-06 (Throttling)     — Pre-warming 스케줄러가 피크 임박을 감지하면 Throttling 동시 활성화
```

## 기능 드라이버 충족 관계

AD-05(로그인 시 권한 갱신 처리), AD-06(2만 명 동시 회의 입장), AD-07(외부 서버 포함 회의 시작)은 기능 드라이버로, 대응하는 QA 드라이버 전략을 통해 충족된다.

| 기능 드라이버 | 충족하는 QA 전략 |
|-------------|---------------|
| AD-05 로그인 시 권한 갱신 처리 | AS-03 (캐시) + AS-07 (Pre-warming) |
| AD-06 2만 명 동시 회의 입장 | AS-02 (비동기) + AS-04 (우선순위 큐) + AS-09 (Bulkhead) |
| AD-07 외부 서버 포함 회의 시작 | AS-02 (비동기) + AS-10 (Circuit Breaker) |
