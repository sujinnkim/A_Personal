# WSL2 설치 가이드 (Windows)

## 사전 조건

- Windows 10 (21H2 이상) 또는 Windows 11
- 관리자 권한

---

## 1. WSL2 설치

PowerShell을 **관리자 권한**으로 실행 후:

```powershell
wsl --install
```

설치 완료 후 **PC를 재시작**한다.

> 기본으로 Ubuntu가 설치된다. 다른 배포판을 원하면:
> ```powershell
> wsl --list --online          # 설치 가능한 배포판 목록
> wsl --install -d Ubuntu-24.04
> ```

---

## 2. Ubuntu 초기 설정

재시작 후 Ubuntu가 자동으로 실행된다. 사용자 이름과 비밀번호를 설정한다.

```
Enter new UNIX username: yourname
Enter new UNIX password: ********
```

---

## 3. 패키지 업데이트

```bash
sudo apt update && sudo apt upgrade -y
```

---

## 4. oh-my-zsh 설치 (Optional)

```bash
sudo apt install zip unzip neovim

sh -c "$(curl -fsSL https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)"
```

## 문제 해결

**가상화 기능이 비활성화된 경우**

BIOS에서 Intel VT-x 또는 AMD-V를 활성화해야 한다.

**WSL1으로 설치된 경우**

```powershell
wsl --set-version Ubuntu 2
wsl --set-default-version 2
```
