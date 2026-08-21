# DD1 Android Launcher

[English](README.md) · **한국어**

본인이 소유한 Windows판 Darkest Dungeon을 Winlator 런타임(기기 위의 Wine + Box64)으로
설치하고 실행하는 안드로이드 런처입니다. Steam에 로그인하면 계정이 보유한 빌드를
내려받고, 버튼 하나로 게임이 시작됩니다.

> **비공식 프로젝트입니다.** Red Hook Studios나 Valve가 만들지 않았고, 승인하지도
> 않았으며, 아무 관련이 없습니다. Darkest Dungeon의 이식판이 아니며 게임의 그 무엇도
> 담고 있지 않습니다 — 게임 파일, DLC, 아트, 음원, 세이브, Steam 계정 데이터 어느 것도
> APK와 이 저장소에 없습니다. 이미 보유한 것 외에는 아무것도 내려받지 않고, 게임
> 바이너리를 고치지 않으며, 무료입니다.

<p align="center">
  <img src="docs/screenshots/01-home.png" width="80%" alt="보유 DLC 목록이 있는 홈 화면">
</p>

## 기능

| | |
|---|---|
| **설치** | QR 또는 비밀번호로 Steam 로그인, 전체 패키지에 걸친 소유 확인, DLC 개별 선택, `files/game`으로의 원자적 설치 |
| **실행** | 버튼 하나. 탭·드래그·홀드를 게임 마우스로 전달하고, 영지 이름은 안드로이드 IME로 입력 |
| **세이브** | 프로필 슬롯별 Steam 클라우드 목록, 내려받기와 올리기, 덮어쓰기 전 자동 스냅샷 |
| **모드** | Workshop 검색·탐색, 구독, 순차 다운로드, 업데이트 감지, 활성/비활성, 로컬 ZIP 가져오기 |

### 모드 관리자

미리보기 이미지와 정렬, 화면에 맞춰 바꾸는 열 수를 갖춘 Workshop 탐색 화면입니다.
PC를 포함해 어디서 구독했든 자동으로 내려받고, 거기서 구독을 해제한 항목은 여기서도
지웁니다.

<p align="center">
  <img src="docs/screenshots/02-mod-hub.png" width="49%" alt="Workshop 탐색">
  <img src="docs/screenshots/03-mod-detail.png" width="49%" alt="갤러리가 있는 모드 상세">
</p>

### 세이브와 설정

프로필 슬롯마다 기기에 있는 것과 Steam 클라우드에 있는 것을 나란히 보여줘서, 전송이
방향이 분명한 의도적 행위가 되도록 했습니다. 온전히 내려받을 수 없는 슬롯은 반쪽만
복원하지 않고 그렇다고 말합니다.

<p align="center">
  <img src="docs/screenshots/04-saves.png" width="49%" alt="세이브 전송 화면">
  <img src="docs/screenshots/05-settings.png" width="49%" alt="설정 화면">
</p>

## 요구사항

- ARM64 안드로이드 기기, Android 8 이상
- Darkest Dungeon(앱 `262060`)을 보유한 Steam 계정
- 여유 공간 약 5GB — 게임이 4GB 남짓이고 staging에도 자리가 필요합니다
- **Wi-Fi.** 이동통신망에서는 Steam이 배정한 콘텐츠 서버가 작은 요청에는 답하고 큰
  요청에는 응답하지 않는 일이 잦아 다운로드가 멈춥니다

## 빌드

Android SDK 34, NDK `24.0.8215888`, CMake 3.22.1이 필요합니다.

```sh
./gradlew assembleDebug testDebugUnitTest    # 모든 변경이 통과해야 하는 것
./gradlew assembleRelease                    # keystore.properties가 있으면 서명됨
```

릴리스 서명은 저장소 루트의 `keystore.properties`를 읽습니다. 파일이 없으면 실패하는
대신 미서명 APK를 만듭니다.

```properties
storeFile=dd1-release.jks
storePassword=...
keyAlias=dd1
keyPassword=...
```

## 테스트

단위 테스트는 호스트에서 돌고, UI 테스트는 Gamescope 창의 Waydroid에서 돕니다.
Waydroid의 x86_64 브리지로는 ARM64 런타임을 실행할 수 없어서, 게임 구동 자체는 항상
실기기에서만 검증됩니다.

```sh
./gradlew connectedDebugAndroidTest          # Waydroid 전용
```

게임이 설치된 폰에는 계측 테스트를 절대 돌리지 마십시오. Gradle이 끝나고 앱을 지우면서
4GB 설치본과 Wine 프리픽스를 함께 가져갑니다.

## 알아둘 것

`applicationId`는 `com.winlator`로 고정입니다. Winlator 루트 파일시스템이
`/data/data/com.winlator` 경로를 box64의 ELF 인터프리터, wineserver, ntdll,
`ld.so.cache`에 고정 길이 필드로 구워 두었기 때문에, 바꾸려면 같은 길이의 id와 추출된
트리 전체를 다시 쓰는 작업이 필요합니다.

`targetSdkVersion`은 28로 둡니다. Android 29부터는 앱 데이터 디렉터리의 파일 실행을
금지하는데, 런타임이 box64와 Wine을 푸는 곳이 정확히 거기입니다. 안드로이드가 띄우는
호환성 경고는 그래서 나오는 것이며 정상입니다.

## 이것이 아닌 것

Darkest Dungeon은 Red Hook Studios의 것이고, EULA는 개인 플레이 목적의 사용을
허락합니다. 이 런처가 하는 일이 정확히 그것입니다 — 직접 구매한 Windows 빌드를 고치지
않고, 본인 소유의 기기에서 실행합니다. 이식판이 아니며 게임을 재배포하지도 않습니다.
게임을 보유한 본인의 Steam 계정이 없으면 이 런처는 실행할 것이 없습니다.

공식 iPad판이 있습니다. 태블릿에서 Darkest Dungeon을 하고 싶고 기기가 맞는다면 그쪽을
구매하십시오. Red Hook이 수익을 얻는 버전입니다.

## 업스트림과 라이선스

Winlator 리비전과 서드파티 고지는 [`NOTICE`](NOTICE)와
[`THIRD_PARTY_NOTICES`](THIRD_PARTY_NOTICES)에 있습니다. 소스는 업스트림의 LGPL-2.1
조건을 따르며([`LICENSE`](LICENSE)), 포함된 구성 요소는 각자의 라이선스를 유지합니다.
