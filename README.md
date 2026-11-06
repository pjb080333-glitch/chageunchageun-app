# 차근차근

| 항목 | 내용 |
| --- | --- |
| 앱 이름 | 차근차근 (데모 버전: 차근차근 데모) |
| 한 줄 소개 | 누가 운전하든, 시동을 켜고 끄는 순간 그 자리에 앉은 사람에게 차가 먼저 일정과 차량 상태를 말하고 필요한 조치까지 실행하는 Pleos Connect 차량 관리 비서 |
| 실행 방법 | 아래 [실행 방법](#실행-방법) — 데모 APK는 어느 에뮬레이터에서나, 연동 APK는 Pleos Connect Emulator에서 |
| 테스트 환경 | Pleos Connect Emulator (API 34, 2560×1440), Android Studio, JDK 17 — [테스트 환경](#테스트-환경) |
| 필요한 설정 | 캘린더 권한 허용, (선택) Virtual Console로 차량 상태 바꾸기 — [필요한 설정](#필요한-설정) |
| APK 위치 | `apk/차근차근_release.apk`(연동), `apk/차근차근_demo_release.apk`(데모) — [GitHub Release 최신판](../../releases/latest)에서 내려받기 |
| 사용 SDK 연동 상태 | [표](#사용-sdk와-실제-연동-상태) · [실차에서 동작 / 데모에서만 시연](#실차연동-버전에서-동작--데모-모드에서만-시연) · [알려진 한계](#알려진-한계) |
| 시연 영상 | [링크 기재] |

현대자동차그룹 차량용 앱 개발 공모전(Pleos Playground) 출품작입니다.

## 주요 기능

| 기능 | 언제 | 하는 일 |
| --- | --- | --- |
| 출발 브리핑 | 시동 켤 때 | 목적지(내장 내비 목적지 → 캘린더 일정) 기준 출발 여유·주행가능거리, 공기압·경고 점검, 이상 있을 때만 음성 안내. 시동 후 내비 길안내가 시작되면 주행가능거리를 다시 비교 |
| 하차 점검 | 시동 끌 때 | 실내등·비상등·문·창문·주차 브레이크(기어 P면 참고로만), 한파 예보 시 블랙박스 주차 녹화 안내. 화면이 열려 있으면 문을 닫는 즉시 카드가 바뀜 |
| 정기점검 | 주행거리·기간 도래 | 취급설명서 정기점검표 주기를 주행거리로 자동 추적, 가혹 조건 주기 적용 |
| 계절점검 | 동절기·하절기 전 | 자동 판정 + 육안 확인 점검표, 겨울 출발 준비(열선·공조) 실행 |
| 등화 점검 | 월 1회, 정기검사 전 | 등을 순서대로 켜고 운전자가 "켜졌어요/안 켜졌어요"(버튼 또는 음성)로 응답 → 등 9개 결과 목록(안 켜진 등 먼저) |
| 중점관리 | 상시 | 한 바퀴만 빠지는 공기압(미세 누설), 반복되는 경고 감지 |
| 실행 연결 | 안내와 함께 | 길안내 시작, 주유소·정비소 경유지 추가(실패 시 "다시 찾기"), 겨울 출발 준비 |

**홈 화면**: 위에 요약 줄(조치 필요 건수·시동·다음 일정·누적 주행거리·바깥 기온), 아래 2단 — 왼쪽 "지금 조치할 것"(심각한 순, 지금 실행할 동작), 오른쪽 "음성 브리핑 · 안내 · 다가오는 점검". 스크롤 없이 한 화면에 들어가는 만큼 보여 주고 넘치면 "N건 더 보기". 주행 중에는 음성 브리핑과 큰 버튼만 남습니다.

## 실차(연동 버전)에서 동작 / 데모 모드에서만 시연

| 기능 | 연동 버전(실차·Pleos 에뮬레이터) | 데모 모드에서만 시연 |
| --- | --- | --- |
| 출발 브리핑(일정·출발 여유) | 동작 (기기 캘린더 + NaviHelper 경로) | — |
| 내비 목적지 vs 주행가능거리 | 동작 (NaviHelper). 시동 후 길안내 시작·앱이 화면에 다시 올 때 재비교 | — |
| 하차 점검(문·창문·실내등·전조등·기어 P) | 동작 (Pleos Vehicle·AAOS) | 주차 브레이크 값(에뮬레이터는 항상 해제로 읽힘) |
| 겨울 출발 준비(열선·실내 온도) | 에뮬레이터에서 동작, 실차 미확인(문서상 "준비 중") | — |
| 등화 점검(앱이 등 켜기) | 에뮬레이터에서 동작 | — |
| 정기점검·계절 점검표 | 동작 (주행거리 기준) | — |
| 공기압 추세·미세 누설 | 공기압 API 비공개 → "눈으로 확인" 항목 | ✔ |
| 경고등 풀이·반복 경고 | 공개 Safety 상태만(에뮬레이터 미지원) | ✔ |
| 장기 미운행 | 마지막 운행 기록(주행 중 화면 제한 신호) | ✔ |
| 주변 주유소·정비소 경유 | 공개 지도 서버(장애 잦음) + 다시 찾기 | — |
| 시동 켜기/끄기 자동 동작 | 에뮬레이터는 시동 신호 없음 → 앱 실행 = 시동 | 버튼으로 |

## APK

| 파일 | 빌드 | 앱 이름 | 용도 |
| --- | --- | --- | --- |
| `apk/차근차근_release.apk` | `pleosRelease` | 차근차근 | Pleos Connect(에뮬레이터·실차)용. Pleos SDK 실연동 |
| `apk/차근차근_demo_release.apk` | `mockRelease` | 차근차근 데모 | 데모 시나리오 버전. 차량 SDK 없이 어느 에뮬레이터에서나 실행 |

- 두 앱은 패키지가 달라(`com.chageunchageun.app`, `com.chageunchageun.app.mock`) 함께 설치할 수 있습니다.
- 버전 1.1.0 (versionCode 2). APK 파일은 저장소에 커밋하지 않고 [GitHub Release 최신판](../../releases/latest)에 첨부합니다(로컬 빌드 시 `apk/` 폴더). Release 첨부 파일 이름은 영문: `chageunchageun_release.apk`(연동), `chageunchageun_demo_release.apk`(데모).

## 실행 방법

### 1. 데모 버전 (`mockDebug` / `mockRelease`)
1. Android Studio에서 프로젝트를 엽니다.
2. Build Variants에서 `mockDebug`를 선택합니다. (또는 `adb install apk/차근차근_demo_release.apk`)
3. Pleos Connect Emulator 또는 가로 태블릿 에뮬레이터(API 28 이상)에서 **차근차근 데모**를 실행합니다.
4. 홈 화면 상단에서 데모 시나리오를 고른 뒤 **시동 켜기 / 시동 끄기** 버튼을 누릅니다.

| 시나리오 | 확인할 수 있는 기능 |
| --- | --- |
| 평상시 | 출발 브리핑(KTX 탑승 · 광명역), "이상 없어요" |
| 겨울 아침 | 출발 여유 15분, 겨울 출발 준비, 미세 누설 감지, 엔진오일 임박, 동절기 전 점검표 |
| 경고등 | 경고 풀이, 반복 경고 중점관리 |
| 주행가능거리 부족 | 주유소 경유 제안 |
| 장기 미운행 | 장기 미운행 후 첫 운행 안내 |
| 하차 실수 | 시동 끌 때 하차 점검 |

Pleos Connect Emulator에서는 데모 버전도 Pleos 음성(TextToSpeech)으로 말합니다. 음성 서비스가 없는 일반 에뮬레이터에서는 안드로이드 기본 TTS로 바뀝니다.

**시연 자동화 (debug 빌드 전용, release 에서는 무시)**

```
adb shell am start --user 10 -n com.chageunchageun.app.mock/com.chageunchageun.app.MainActivity --es demo_scenario WINTER_MORNING --es demo_action IGNITION_ON --es demo_tab HOME
```
- `demo_scenario`: NORMAL, WINTER_MORNING, WARNING_LIGHTS, LOW_RANGE, LONG_IDLE, EXIT_MISTAKES
- `demo_action`: IGNITION_ON, IGNITION_OFF, WINTER_PRESET, START_LAMP_CHECK, DISMISS_MESSAGE,
  SEED_CALENDAR(테스트 일정 넣기, 연동 버전용), SEED_CALENDAR_NOGEO, WAYPOINT_FUEL, WAYPOINT_REPAIR
- `demo_tab`: HOME, CHECKLIST, LAMP, HISTORY

### 2. Pleos 연동 버전 (`pleosDebug` / `pleosRelease`)
1. Pleos Playground 개발 문서에 따라 Android Studio에 **Pleos Connect Emulator**(v2.1.0 이상)를 설치합니다.
2. AVD 부팅 옵션을 **Cold boot**로 설정합니다.
3. Build Variants에서 `pleosDebug`를 선택해 실행합니다. (또는 `adb install apk/차근차근_release.apk`)
4. 캘린더 권한을 허용합니다.
5. 앱을 열면(또는 시동이 켜지면) 출발 브리핑을 한 번 하고, 시동 끄기 버튼(또는 시동 OFF)으로 하차 점검을 합니다.

## 필요한 설정

### 권한
| 권한 | 왜 |
| --- | --- |
| 캘린더 읽기 (실행 시 허용) | 다음 일정으로 출발 브리핑 |
| `pleos.car.permission.*` (설치 시 자동) | 차량 상태 읽기·열선/공조/등화 제어, 내비, 음성 |
| `android.car.permission.CAR_POWERTRAIN` | 시동·주차 브레이크·기어 상태 |
| 인터넷 (연동 버전만) | 날씨 예보(Open-Meteo), 주유소·정비소 검색(OpenStreetMap). 키 없는 공개 API, 좌표만 보냄 |

### 테스트 일정 넣기 (연동 버전 debug)
```
adb shell pm grant --user 10 com.chageunchageun.app android.permission.WRITE_CALENDAR
adb shell am start --user 10 -n com.chageunchageun.app/com.chageunchageun.app.MainActivity --es demo_action SEED_CALENDAR
```
Pleos 에뮬레이터에서 앱은 사용자 10으로 실행되므로 `--user 10`을 붙입니다.
로컬 캘린더 "차근차근 테스트"에 KTX 탑승(광명역·서울역), SRT 탑승(수서역, 내일), 대전 출장(대전역) 일정이 들어갑니다.
일정 설명 칸의 `geo:위도,경도`가 있으면 그 좌표를, 없으면 장소 이름으로 좌표를 찾습니다.

### Virtual Console 로 차량 상태 바꿔 보기
에뮬레이터 실행 후 `adb forward tcp:8088 tcp:8088` → 브라우저에서 `http://127.0.0.1:8088/` → Emulation Controls.

| 바꿀 것 | Virtual Console 카드 | 앱에서 확인 |
| --- | --- | --- |
| 운전석 문 열기 | Convenience › Door Position (Front left → Open) | 시동 끄기 → "운전석 문이 덜 닫혔어요". 화면이 열려 있으면 문을 닫는 즉시 카드가 사라짐 |
| 실내등 켜기 | Convenience › Cabin Lights Switch → On | 시동 끄기 → "실내등이 켜져 있어요" |
| 창문 열기 | Convenience › Window Position | 시동 끄기 → 창문 열림 안내 |
| 주차(P) | Motion › Gear Selection P | 점검표·등화 점검 진행 가능 |
| 주행 | Motion › Gear Selection D + 가속 | 주행 중 화면 제한(음성 안내 + 큰 버튼만) |
| 주행가능거리 | Energy › Remaining Range | 일정 목적지까지 빠듯하면 경고 |

브라우저 없이 명령줄로도 바꿀 수 있습니다(브라우저 하트비트를 흉내 낸 뒤 같은 API 호출).
```
# 1초마다 하트비트를 보내는 동안만 쓰기 가능
while true; do curl -s -X POST "http://127.0.0.1:8088/browser/session?client=cli" >/dev/null; sleep 1; done &
curl -X PUT "http://127.0.0.1:8088/vehicle/body?door_fl=true"     # 운전석 문 열기
curl -X PUT "http://127.0.0.1:8088/vehicle/body?cabin_light=true" # 실내등 켜기
curl -X PUT "http://127.0.0.1:8088/vehicle/gear?gear=Park"        # 기어 P
```
※ `adb shell cmd car_service inject-vhal-event` 는 에뮬레이터가 user 빌드라 사용할 수 없습니다.

### 음성 확인
소리와 음성 인식은 [docs/음성_확인_방법.md](docs/음성_확인_방법.md) 순서대로 5분 안에 확인할 수 있습니다.

## 테스트 환경

- Windows 11, Android Studio, JDK 17 (`%ProgramFiles%\Microsoft\jdk-17…`), Gradle Wrapper 9.3.0
- minSdk 28, targetSdk/compileSdk 35, Kotlin, Jetpack Compose
- Pleos Connect Emulator: `system-images/android-34/pleos_car_emulator/x86_64`, AVD 기기 프로필 Pleos_Connect_v2_0, RAM 4GB, Cold boot
- 단위 테스트: `gradlew testMockDebugUnitTest` (점검 엔진 30개 + 홈 화면 배치·규칙 18개)
- 릴리스 서명 키는 저장소 밖(`%USERPROFILE%\.android\`)에 있고, 비밀번호는 git 제외 파일 `keystore.properties`에만 있습니다. 이 파일이 없으면 release 빌드는 서명 없이 만들어집니다.

## 사용 SDK와 실제 연동 상태

| SDK / API | 버전 | 쓰는 곳 | 상태 |
| --- | --- | --- | --- |
| Pleos Vehicle (읽기) | 2.3.2 | 주행거리·주행가능거리·EV 배터리, 엔진오일·브레이크액·변속기 오일·워셔액 상태, 문·창문·실내등·전조등 | 연동 · 에뮬레이터 확인(공개 Safety 상태는 에뮬레이터에서 ERROR_NOT_SUPPORTED) |
| Pleos Vehicle (리스너) | 2.3.2 | 문·창문·실내등·전조등 변화 → 하차 점검 카드 즉시 갱신 | 연동 · 에뮬레이터 확인 |
| Pleos Vehicle (제어) | 2.3.2 | 시트·핸들·사이드미러 열선, 실내 온도, 전조등(하향)·상향등·미등 | 연동 · 에뮬레이터에서 동작(문서상 "준비 중"). 실패하면 "직접 켜 주세요" |
| Pleos NaviHelper | 3.0.5 | 거리·시간(requestTrip), 길안내(requestRoute), 경유지(addWaypoint), 현재 위치, 길안내 중인 목적지(getRouteStateInfo·getDestinationInfo·onRouteStarted) | 연동 · 에뮬레이터 확인 |
| Pleos TextToSpeech | 3.3.1 | 음성 브리핑 (ON_DEVICE) | 연동 · 에뮬레이터 재생 확인. 없으면 안드로이드 TTS |
| Pleos SpeechToText | 3.3.1 | "응/아니", "켜졌어" (ON_DEVICE) | 연동 · 초기화 확인, 실제 인식은 [음성 확인 방법](docs/음성_확인_방법.md)으로 사용자 확인 |
| AAOS Car API | — | 시동(IGNITION_STATE), 주차 브레이크, 기어(GEAR_SELECTION), 주행 중 화면 제한(CarUxRestrictions) | 연동 · 에뮬레이터는 시동 값 미지원(앱 실행 시 브리핑 1회로 대체), 기어 P 확인 |
| 기기 캘린더 | — | 다음 일정 | 연동 |
| Open-Meteo | — | 현재 기온·오늘 밤 최저기온 | 대체(차량 외기온 공개 API 없음) |
| OpenStreetMap (Nominatim·Overpass) | — | 장소 이름 → 좌표, 주변 주유소·정비소·충전소 | 대체(NaviHelper 장소 검색 없음, Geocoder 미동작 대비) |
| Tire.getTirePressure, Safety.getTelltalesInfo | — | 공기압·경고등 | **사용 안 함** — 공식 샘플이 공개 API에서 제외. 데모 버전에서만 시연 |

## 구조

```
app/src/main    점검 엔진(engine), 데이터 계층(data), 화면(ui), 음성(Pleos TTS/STT 공통)
app/src/mock    데모 시나리오용 데이터 공급
app/src/pleos   Pleos Vehicle·NaviHelper·AAOS 연동 구현
app/src/debug   테스트 일정 넣기용 캘린더 쓰기 권한(debug 전용)
app/src/test    점검 엔진 단위 테스트
docs/           구조 설명(발표 질의응답용), 음성 확인 방법
tools/          에뮬레이터 켜기·끄기(휴식 시간 확인), 시연 녹화 스크립트
```

자세한 설명: [docs/구조설명.md](docs/구조설명.md)

차량 데이터는 `VehicleDataSource` 인터페이스로만 접근하며, 차종별로 지원되지 않는 데이터는 자동으로 "눈으로 확인" 항목으로 전환됩니다.

## 안전 설계

- 주행 중(CarUxRestrictions)에는 음성 안내와 큰 버튼만 남기고 점검표·등화 점검·이력 탭을 잠급니다. 점검표·등화 점검은 주차 상태(주차 브레이크 또는 기어 P)에서만 진행합니다.
- 열선·공조·조명 제어는 운전자가 확인한 뒤에만 실행합니다.
- 운전 기록과 점검 이력은 차량 내부에 저장합니다. 외부로는 날씨·장소 검색용 좌표만 보냅니다.
- 홈 글씨는 본문 24sp·제목 30sp, 실행 버튼 56dp 이상(주행 중·등화 점검 답 버튼 112dp).

## 알려진 한계

- Pleos 에뮬레이터는 시동 값을 주지 않아 "앱 실행 = 시동"으로 시연합니다(실차의 시동 신호는 사무국 확인 중).
- 공기압·경고등 실데이터는 비공개 API라 데모 모드에서만 보여 줍니다.
- 일정은 차의 기기 캘린더 기준입니다(휴대폰 일정 동기화 미확인). 내장 내비 목적지를 먼저 써서 보완합니다.
- 장소 검색은 OpenStreetMap 공개 서버를 써서 장애가 잦고 국내 품질에 한계가 있습니다. 실패하면 "내비게이션에서 검색" 안내와 "다시 찾기"를 보여 줍니다.
- 앱이 화면에 없을 때 내비에서 시작한 길안내는 앱이 다시 화면에 올 때 재비교합니다(백그라운드 감시 없음).
- 열선·공조·등화 제어는 문서상 "준비 중"이며 에뮬레이터에서만 동작을 확인했습니다.

## 참고

`CarProfile`의 점검 주기·권장 공기압은 데모용 샘플 값이며, 실제 차종의 취급설명서 값으로 교체해 사용합니다.
날씨는 [Open-Meteo](https://open-meteo.com), 장소 데이터는 © [OpenStreetMap](https://www.openstreetmap.org/copyright) 기여자(ODbL)를 사용합니다.
