# 시연 녹화 원본 (#004 F2, 2026-11-06)

- 녹화: Pleos Connect Emulator(AVD `Pleos_API34`, 2560×1440, 2코어) 화면을 `adb shell screenrecord --bit-rate 6000000`으로 녹화. 에뮬레이터 창 테두리·PC 바탕화면 없음. 소리 없음.
- 화면 전체(왼쪽 차량 화면 + 오른쪽 앱 영역)가 찍혀 있다. 앱만 쓰려면 편집 때 **x 853~2560, y 90~1300** 을 자른다.
- 장면은 손으로 누르지 않고 debug 빌드 시연 인텐트(`demo_scenario`/`demo_action`/`demo_tab`, `demo_clean`=상태 칩 숨김) + `uiautomator` 탭 + Virtual Console HTTP API로 만들었다. 스크립트: `tools/record_all.sh` (부팅 1회로 S1~S11, 샷마다 스크린샷·크래시 확인).
- `screenrecord`는 화면이 바뀔 때만 프레임을 기록해서, 끝이 정지 화면이면 파일이 그만큼 짧다. 편집 때 마지막 프레임을 늘려 쓴다.
- B+++ 반영 빌드. PC에 영상 도구가 없어 Claude Code는 프레임을 직접 보지 못하고, 샷 끝 스크린샷(`screenshots/004/rec/`)으로 확인한다.
- 이전 녹화(#002·#003)는 `old/`. mp4는 git 제외.

| 샷 | 파일 | 길이 | 빌드 | 결과(11-06 21:30 다시 녹화, B+++) | 특이사항 |
|---|---|---|---|---|---|
| S1 | S1_normal.mp4 | 12.5초 | mock | OK | |
| S2 | S2_winter.mp4 | 19.7초 | mock | OK — 왼쪽 3장 "더 보기" 없이 | |
| S3 | S3_preset.mp4 | 6.8초 | mock | OK (22:25 다시) — 화면의 "겨울 출발 준비 실행" 버튼 → "…켰어요" | 목표 10초보다 짧음(누른 뒤 화면 변화가 짧음). 편집 때 끝 프레임 늘리기 |
| S4 | S4_warning.mp4 | 14.5초 | mock | OK | |
| S5 | S5_range.mp4 | 11.7초 | mock | OK — "주유소 경유" 버튼 | |
| S6 | S6_exit.mp4 | 13.5초 | mock | OK (22:25 다시) — 시동 켜고 8초 뒤 "시동 끄기" 버튼 → 하차 점검. 크래시 없음 | |
| S7 | S7_lamp.mp4 | 17.5초 | mock | OK (22:26 다시) — 큰 등화 점검 화면 → 결과 카드 + 등 9개 목록(미등 ✕ 맨 앞) | |
| S8 | S8_checklist.mp4 | 10.9초 | mock | 영상 정상(확인했어요·손볼 필요). 스크립트 확인 문구 오판으로 "실패" 표시 | try2 영상 |
| S9 | S9_live.mp4 | 10.2초 | pleos | OK (22:27 다시) — 문 열기·실내등 → "시동 끄기" 버튼 → 하차 점검(실내등·문, 주차 브레이크 참고). 누적 주행거리 48,620km·주행가능거리 380km | 목표 15초보다 짧음(끝 정지 화면) |
| S10 | S10_navi_range.mp4 | 43.3초 | pleos | OK | 누적 주행거리 칸 숨김(0km) |
| S11 | S11_waypoint.mp4 | 28.6초 | pleos | OK | |
- 샷별 확인 스크린샷·로그: `screenshots/004/rec/` (git 제외).
