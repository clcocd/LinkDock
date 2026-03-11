# LinkDock

LinkDock는 Z-aN과 SPWN 다운로드를 보다 쉽게 사용할 수 있도록 만든 Streamlink 기반 데스크톱 앱입니다.
Windows와 macOS에서 사용할 수 있으며, 환경 검사, 저장 경로 복원, 다운로드 진행 로그 표시 등의 기능을 제공합니다.

> Z-aN / SPWN 다운로드를 명령어 대신 GUI로 처리할 수 있도록 만든 앱입니다.

## 주요 기능

* Z-aN / SPWN 다운로드 흐름에 맞춘 GUI 제공
* Streamlink 설치 여부 확인
* 다운로드 진행 로그 표시
* 다운로드 상태 메시지 표시
* 같은 파일이 이미 있을 경우 자동으로 건너뜀
* 작업 중 환경 상태 문구 자동 숨김

## 지원 환경

* Windows
* macOS

## 설치 방법

릴리스 페이지에서 운영체제에 맞는 파일을 다운로드해 실행하세요.

### Windows

- [Windows 다운로드](https://github.com/clcocd/LinkDock/releases/latest/download/linkdock.exe)

### macOS

- Apple Silicon(mac-aarch64)용 파일은 [Releases 페이지](../../releases)에서 다운로드하세요.

## 사용 방법

1. 앱을 실행합니다.
2. 다운로드할 서비스를 선택합니다.
3. 서비스 계정의 이메일과 비밀번호를 입력합니다.
4. 다운로드할 페이지 URL을 입력합니다.
    - ZAN: `https://www.zan-live.com/live/play/...`
    - SPWN: `https://spwn.jp/events/...`
5. 저장 경로를 선택합니다.
6. **다운로드 시작**을 눌러 진행합니다.
7. 진행 상태와 로그를 확인합니다.

## 주의 사항

* 앱 실행 시 Streamlink 설치 상태를 자동으로 확인합니다.
* Streamlink가 없거나 상태가 정상적으로 반영되지 않으면 상단의 **환경 및 실행 상태** 영역에서 직접 설치 또는 재확인을 진행해 주세요.
* 홈페이지, 로그인 페이지, 마이페이지 주소는 다운로드 URL로 사용할 수 없습니다.
* Streamlink 플러그인이 지원하는 직접 시청/이벤트 페이지 URL만 입력해야 합니다.
* URL은 브라우저 주소창에 표시되는 전체 주소를 그대로 붙여넣어 주세요.

## 문제 해결

### 다운로드 시작 버튼이 비활성화되어 있을 때

다음 항목을 확인해 주세요.

* 서비스가 선택되어 있는지
* 이메일과 비밀번호가 입력되어 있는지
* 다운로드할 페이지 URL이 입력되어 있는지
* Streamlink가 설치되어 있고 정상적으로 인식되었는지

### 올바른 URL을 넣었는지 모르겠을 때

다음과 같은 주소를 입력해야 합니다.

* ZAN: 공연 시청 페이지 URL  
  예: `https://www.zan-live.com/live/play/...`
* SPWN: 이벤트 페이지 URL  
  예: `https://spwn.jp/events/...`

다음과 같은 주소는 사용할 수 없습니다.

* 홈페이지
* 로그인 페이지
* 마이페이지
* 서비스 메인 화면 주소

### Streamlink가 없다고 표시될 때

상단의 **환경 및 실행 상태** 영역에서 **설치** 또는 **설치/업데이트**를 진행해 주세요.
설치 후에도 바로 반영되지 않으면 **재확인**을 진행하거나 앱을 다시 실행해 주세요.

## 현재 한계

* Windows의 시스템 다운로드 폴더 변경을 완전히 추적하지는 않습니다.
* 일부 예외적인 경로 환경에서는 저장 경로를 수동으로 다시 지정해야 할 수 있습니다.

## 개발

### 요구 사항

* JDK 17 이상
* Gradle
* Kotlin / Compose Desktop 개발 환경

### 실행

#### macOS / Linux

```bash
./gradlew run
```

#### Windows

```bat
gradlew.bat run
```

### 빌드

#### macOS / Linux

```bash
./gradlew packageDistributionForCurrentOS
```

#### Windows

```bat
gradlew.bat packageDistributionForCurrentOS
```

## 프로젝트 구조

```text
composeApp/
 ├─ src/
 │   ├─ commonMain/
 │   └─ jvmMain/
 └─ ...
```

## 라이선스

이 프로젝트는 MIT 라이선스를 따릅니다.
자세한 내용은 [LICENSE](./LICENSE) 파일을 참고하세요.
