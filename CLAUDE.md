# 아키텍트 양성과정 개인과제

## 과제 주제

**미팅 서비스 요청 집중 구간 대응을 위한 요청 처리 아키텍처 개선 설계**

---

## 에이전트 구성

| 에이전트 | 역할 | 담당 경로 |
|---------|------|---------|
| `architecture-designer` | 아키텍처 설계 문서 작성 | `docs/01~04장` |
| `prototype-developer` | Java/Spring Boot 프로토타입 구현 및 검증 | `src/`, `docs/05장` |
| `report-writer` | 최종 레포트(.docx) 생성 | `scripts/`, `report/` |

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
│   ├── 01_시스템개요/
│   ├── 02_요구사항정의/
│   ├── 03_아키텍처문제분석/
│   ├── 04_아키텍처상세설계/
│   ├── 05_아키텍처구현검증/
│   └── diagrams/
├── material/              # 과제 주요 참고 자료
├── memo/                  # 사용자 개인 메모 — 에이전트 참조 금지
├── src/
├── report/
│   ├── template/
│   └── output/
└── scripts/
    └── generate_report.py
```

---

## 미확정 사항 (추후 사용자 제공)

- 미팅 서비스 상세 내용 (서비스 구조, 트래픽 패턴, 현재 문제 상황)
- 추가 기술 스택
- Word 레포트 템플릿
