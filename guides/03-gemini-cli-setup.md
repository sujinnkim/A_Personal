# Gemini CLI 설치 가이드

## 사전 조건

- WSL2 및 Ubuntu 설치 완료 ([01-wsl-setup.md](./01-wsl-setup.md) 참고)

---

## 1. Node.js 설치 (nvm 사용)

시작 메뉴에서 Ubuntu를 검색하여 실행하여 WSL 터미널에서 진행한다.

```bash
# nvm 설치
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash
```

```
# 셸 재시작 또는 적용
vi ~/.zshrc

# 해당 문서 하단에 다음 내용 추가
export NVM_DIR="$([ -z "${XDG_CONFIG_HOME-}" ] && printf %s "${HOME}/.nvm" || printf %s "${XDG_CONFIG_HOME}/nvm")"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh" # This loads nvm
```

이후 Ubuntu 터미널 종료 후 다시 실행

```
# Node.js LTS 설치
nvm install --lts
nvm use --lts

# 설치 확인
node -v
npm -v
```

---

## 2. Gemini CLI 설치

```bash
npm install -g @google/gemini-cli
```

설치 확인:
```bash
gemini --version
```

---

## 3. 로그인

```bash
gemini
```

최초 실행 시 Google 계정 로그인 URL이 출력된다. 해당 URL을 Windows 브라우저에서 열어 Google 계정으로 인증한다.
