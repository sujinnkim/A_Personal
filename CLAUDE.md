# 아키텍트 양성과정 개인과제

> **[이어서 작업 시작점]** 새 세션에서 이어받을 때는 먼저 리포지토리 루트의 **`HANDOFF.md`**를 읽는다.
> 설계·구현·검증 3축의 현재 상태와 "다음 세션 착수 체크리스트"가 거기에 있다.
> 프로토타입·검증 딥다이브는 `prototype/verification/prototype-review-status.md`.

## 과제 주제

**미팅 포털 서버의 트래픽 집중 구간 병목 해소를 위한 아키텍처 개선 설계**

---

## 에이전트 구성

| 에이전트 | 역할 | 담당 경로 |
|---------|------|---------|
| `docs-agent` | 아키텍처 설계 문서 작성 | `docs/` |
| `prototype-agent` | PoC 프로토타입 구현 및 검증 환경 구성 | `prototype/` |
| `report-agent` | 레포트 분할 파일 관리 (docs → report 동기화) | `report/` |

---

## 디렉토리 구조

```
A_Personal/
├── .claude/
│   └── agents/
│       ├── architecture-designer.md
│       ├── prototype-developer.md
│       └── report-writer.md
├── docs/
│   ├── 01-plan/
│   ├── 02-system-overview/
│   │   └── 1.2-issues/
│   ├── 03-requirements/
│   │   ├── 2.1-functional/
│   │   └── 2.2-quality/
│   ├── 04-architecture-analysis/
│   │   └── 3.2-problem-analysis-and-strategy/
│   ├── 05-architecture-design/
│   │   ├── 4.1-conceptual/
│   │   ├── 4.2-execution/
│   │   │   ├── 4.2.1-runtime-view/
│   │   │   ├── 4.2.2-module-view/
│   │   │   └── 4.2.3-deployment-view/
│   │   └── 4.3-evaluation/
│   └── 06-verification/
├── guides/                # 환경 설정 가이드
├── material/              # 과제 주요 참고 자료
├── memo/                  # 사용자 개인 메모 — 에이전트 참조 금지
├── prototype/
├── report/
│   ├── 1/ ~ 5/            # 챕터별 최종 문서
│   ├── drawio/            # 다이어그램 소스
│   ├── images/            # 다이어그램 이미지
│   └── googledocs-scripts/
└── scripts/
    └── generate_report.py
```

---

## 미확정 사항 (추후 사용자 제공)

- 미팅 서비스 상세 내용 (서비스 구조, 트래픽 패턴, 현재 문제 상황)
- 추가 기술 스택
- Word 레포트 템플릿
