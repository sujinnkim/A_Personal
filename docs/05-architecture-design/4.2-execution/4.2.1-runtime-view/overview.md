# 4.2.1. 실행 뷰 (Runtime View)

실행 뷰는 런타임 상태의 컴포넌트 결합과 동작 흐름을 기술한다. 본 절은 4.1.1 개념 아키텍처를 *컴포넌트와 연결자(C&C)* 단위로 구체화한 Overall View를 먼저 제시하고, 요청 집중 구간의 주요 유스케이스를 *동적 시퀀스*로 확대한다. 각 시퀀스에서 어느 설계 전략(AS)이 어느 지점에 작동하는지는 그림이 아니라 `AS 적용 지점 요약` 표로 추적한다.

> **본 절의 범위**: front-api 진입점부터 데이터베이스·외부 서버 경계까지의 서버 측 컴포넌트 결합에 집중한다. 클라이언트 내부 동작과 Meeting Manager 뒤단(cPaaS·server-api·WC/VC/AC 서버) 내부는 본 사업의 관심사가 아니므로 단일 호출 대상으로만 표현한다.

## 하위 절 구성

4.2.1.1 Overall에서 전체 C&C View를 제시한 뒤, 각 유스케이스별 흐름을 후속 절에서 확대한다. 각 시퀀스는 Overall View의 컴포넌트 명칭을 그대로 사용한다.

| 절 | 초점 | 주요 컴포넌트·전략 |
|---|---|---|
| [4.2.1.1 Overall](4.2.1.1-overall.md) | 시스템 C&C View 전체 | Edge·Domain·Integration·Cache·Pool·Data 6계층 결합 |
| [4.2.1.2 회의 입장](4.2.1.2-join.md) | UC-04 피크 집중 입장 | AS-04 · AS-02 · AS-08 |
| [4.2.1.3 권한 갱신](4.2.1.3-auth.md) | UC-01 캐시 hit/miss | AS-03 · AS-02 · AS-09 |
| [4.2.1.4 선제 적재](4.2.1.4-prewarming.md) | AS-05 Pre-warming | AS-05 · AS-06 · AS-03 |
| [4.2.1.5 격벽 격리](4.2.1.5-bulkhead.md) | AS-08 Bulkhead | AS-08 · AS-01 |
| [4.2.1.6 장애 차단](4.2.1.6-resilience.md) | AS-09 Circuit Breaker | AS-09 · AS-03 |

상세 모듈 패키지 구조는 [4.2.2 모듈 뷰](../4.2.2-module-view/overview.md), 컴포넌트의 물리 배치는 [4.2.3 배치 뷰](../4.2.3-deployment-view/overview.md)에서 다룬다.
