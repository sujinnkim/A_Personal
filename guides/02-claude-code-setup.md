# Claude Code 설치 가이드

## 사전 조건

- WSL2 및 Ubuntu 설치 완료 ([01-wsl-setup.md](./01-wsl-setup.md) 참고)

---

## 1. Ubuntu 실행

시작 메뉴에서 Ubuntu를 검색하여 실행하여 WSL 터미널에서 진행한다.

---

## 2. Claude Code 설치

```bash
curl -fsSL https://claude.ai/install.sh | bash
```

설치 확인:
```bash
claude --version
```

---

## 3. 로그인

```bash
claude
```

최초 실행 시 브라우저가 열리며 Anthropic 계정으로 로그인하면 자동으로 인증이 완료된다.
