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
| AS-06 | Time-based Throttling | ISSUE-03, ISSUE-09 | AD-02 |
| AS-07 | Predictive Pre-warming | ISSUE-09 | AD-04 |
| AS-08 | CQRS | ISSUE-07 | AD-03 |
| AS-09 | Bulkhead 격리 | ISSUE-01, ISSUE-04, ISSUE-06 | AD-03, AD-04 |
| AS-10 | Circuit Breaker / Fallback | ISSUE-02, ISSUE-06, ISSUE-08 | AD-04 |
| AS-11 | Anti-Corruption Layer (ACL) | ISSUE-08 | AD-09 |
