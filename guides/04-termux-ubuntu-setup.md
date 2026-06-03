# Android Termux + Ubuntu 설치 가이드

## 사전 조건

- Android 7.0 이상

---

## 1. Termux 설치

**Google Play Store가 아닌 github에서 다운로드 받아 설치**해야 한다.
Play Store 버전은 더 이상 유지보수되지 않아 패키지 설치가 정상적으로 되지 않는다.

- Termux GitHub Releases: https://github.com/termux/termux-app/releases

APK 설치 시 **출처를 알 수 없는 앱 설치** 권한을 허용해야 한다.

---

## 2. Termux 초기 설정

Termux 실행 후 패키지를 업데이트한다.

```bash
pkg update && pkg upgrade
```
Termux에서 다음 명령어 실행
```bash
# termux 앱 저장소 디렉토리 내에 폰 저장소 마운트
termux-setup-storage
```

한글 입력 안되는 경우 다음 변수를 ~/.termux/termux.properties 제일 하단에 추가 --> Play스토어에 있는 Termux는 이게 안됨
```
enforce-char-based-input = true
ctrl-space-workaround = true
```

## 3. proot-distro 설치

proot-distro는 Termux에서 Linux 배포판을 실행할 수 있게 해주는 도구다.

Package Update
```bash
pkg install x11-repo
pkg update
pkg upgrade
```

Ubuntu Wrapper 설치
```bash
# proof-distro 통해 ubuntu 설치(ubuntu를 설치하는 건 아니고 root 권한을 가진 사용성을 가지기 위한 wrapper)
pkg install proot-distro
proot-distro install ubuntu
```

Ubuntu 실행 명령어
```bash
# proof-distro ubuntu 로그인. --bind 옵션으로 폰 저장소를 마운트할 수 있음
proot-distro login ubuntu --bind /storage/emulated/0:/root/storage
```

이후 ubuntu root 계정 통해 nvm 설치하여 gemini, clause 설치해서 사용
