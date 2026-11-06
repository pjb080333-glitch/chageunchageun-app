#!/usr/bin/env bash
# #004 B+++ 다시 녹화: 부팅 1번으로 S1~S11 (mock S1~S8 → pleos S9~S11). user 10, demo_clean.
# 준비: tools/emu_start.ps1 → READY → mockDebug·pleosDebug 둘 다 설치 → 이 스크립트.
# 각 샷: 앱을 앞으로 → 녹화 → 동작(누를 버튼이 화면에 있는지 먼저 확인, 없으면 실패)
#        → 끝에 화면 다시 그리기(탭 왕복)로 길이 확보 → 스크린샷 + 기대 문구 + 크래시·ANR 확인
#        → 실패면 같은 부팅 안에서 1번만 다시 찍는다.
# 결과: demo/clips/S*.mp4, screenshots/004/rec/S*.png, screenshots/004/rec/logs/*.txt, 끝에 요약 표.
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
ROOT="$(cd "$(dirname "$0")/.." && pwd -W)"  # adb.exe 는 C:/... 형식만 읽음
CLIPS="$ROOT/demo/clips"
OUT="$ROOT/screenshots/004/rec"
LOGS="$OUT/logs"
VC=http://127.0.0.1:8088
export MSYS_NO_PATHCONV=1
mkdir -p "$CLIPS" "$OUT" "$LOGS"
SUMMARY=()
ONLY="$*"   # 예: bash tools/record_all.sh S3 S6 S7 S9  → 그 샷들만 (S6 처럼 번호만 써도 됨)
want() { [ -z "$ONLY" ] && return 0; local n; for n in $ONLY; do case "$1" in "$n"|"$n"_*) return 0;; esac; done; return 1; }

MOCK=com.chageunchageun.app.mock
PLEOS=com.chageunchageun.app
PKG=$MOCK
act() { echo "$PKG/com.chageunchageun.app.MainActivity"; }

start() { "$ADB" shell am start --user 10 -n "$(act)" --ez demo_clean true "$@" >/dev/null 2>&1; }
front() { "$ADB" shell dumpsys activity activities | grep -m1 "topResumedActivity" | grep -q "$PKG/"; }
ensure_front() { for i in 1 2 3; do front && return 0; start; sleep 2; done; echo "  WARN: app not in front"; return 1; }
dump() { "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; "$ADB" shell cat /sdcard/ui.xml | tr '>' '\n'; }
has_text() { dump | grep -qE "text=\"[^\"]*($1)[^\"]*\""; }
# text 가 정확히 같은 n번째 노드를 누른다. 없으면 실패(1).
tap_text() {
  local n=${2:-1} b
  b=$(dump | grep "text=\"$1\"" | sed -n "${n}p" | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+')
  set -- $b
  if [ -z "$4" ]; then echo "  FAIL: 버튼 없음 '$1'"; return 1; fi
  "$ADB" shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}
vc() { curl -s -m 5 -X PUT "$VC/vehicle/$1" >/dev/null || echo "  WARN: vc $1"; }
# 장면 준비: 시나리오 선택(시동 꺼짐) + 안내 줄 닫기 + 앱을 앞으로
scene() { start --es demo_scenario "$1" --es demo_tab HOME; sleep 2; start --es demo_action DISMISS_MESSAGE; sleep 1; ensure_front; }

rec_start() { "$ADB" shell rm -f "/sdcard/$1.mp4"; "$ADB" shell screenrecord --bit-rate 6000000 --time-limit "$2" "/sdcard/$1.mp4" & RECPID=$!; REC_T0=$(date +%s); sleep 1; }
# 끝 정지 화면이 기록되도록: 목표 길이 1.5초 전까지 기다렸다가 탭 왕복으로 화면을 한 번 다시 그린다.
rec_end() {
  local name=$1 len=$2 left
  left=$(( REC_T0 + len - 2 - $(date +%s) ))
  [ $left -gt 0 ] && sleep $left
  if [ "${3:-}" != "noredraw" ]; then start --es demo_tab HISTORY; sleep 0.3; start --es demo_tab HOME; fi
  wait $RECPID
  "$ADB" pull "/sdcard/$name.mp4" "$CLIPS/$name.mp4" >/dev/null 2>&1 && "$ADB" shell rm "/sdcard/$name.mp4"
}
# 샷 결과 확인: 스크린샷 + 기대 문구 + 크래시·ANR. 로그는 파일로 남긴다.
check() {
  local name=$1 expect=$2 try=$3 ok=1
  "$ADB" exec-out screencap -p > "$OUT/${name}_try$try.png"
  "$ADB" logcat -d -b crash > "$LOGS/${name}_try${try}_crash.txt" 2>&1
  "$ADB" logcat -d -b events | grep -E "am_anr|am_crash|am_proc_died" > "$LOGS/${name}_try${try}_events.txt"
  "$ADB" logcat -d -b main -v time | grep -E "Chageun|AndroidRuntime|ActivityManager: ANR|FATAL|chageunchageun" > "$LOGS/${name}_try${try}_main.txt"
  if [ -s "$LOGS/${name}_try${try}_crash.txt" ] || grep -q "chageunchageun" "$LOGS/${name}_try${try}_events.txt"; then
    echo "  FAIL: 크래시·ANR 기록 ($LOGS/${name}_try${try}_*.txt)"; ok=0
  fi
  if ! front; then echo "  FAIL: 앱이 앞에 없음"; ok=0; fi
  if ! has_text "$expect"; then echo "  FAIL: 기대 문구 없음 '$expect'"; ok=0; fi
  return $(( 1 - ok ))
}
# shot 이름 길이 기대문구 동작함수
shot() {
  local name=$1 len=$2 expect=$3 body=$4 try
  want "$name" || return
  for try in 1 2; do
    echo "$name try$try $(date +%H:%M:%S)"
    "$ADB" logcat -c -b crash -b main -b events 2>/dev/null
    if $body && check "$name" "$expect" "$try"; then
      cp "$OUT/${name}_try$try.png" "$OUT/$name.png"
      SUMMARY+=("$name OK(try$try)"); return
    fi
    wait $RECPID 2>/dev/null
  done
  SUMMARY+=("$name FAIL")
}

# ---------------- mock S1~S8 ----------------
s1() { scene NORMAL; rec_start S1_normal 12; sleep 2; start --es demo_action IGNITION_ON; sleep 1; rec_end S1_normal 12; }
s2() { scene WINTER_MORNING; rec_start S2_winter 20; sleep 2; start --es demo_action IGNITION_ON; sleep 1; rec_end S2_winter 20; }
# S3: 화면의 "겨울 출발 준비 실행" 버튼을 실제로 누른다(카드가 보이는지 확인 겸)
s3() {
  scene WINTER_MORNING; start --es demo_action IGNITION_ON; sleep 4; start --es demo_action DISMISS_MESSAGE; sleep 1; ensure_front
  has_text "겨울 출발 준비 실행" || { echo "  FAIL: 겨울 출발 준비 카드가 화면에 없음"; return 1; }
  rec_start S3_preset 10; sleep 2; tap_text "겨울 출발 준비 실행" || { wait $RECPID; return 1; }; rec_end S3_preset 10
}
s4() { scene WARNING_LIGHTS; rec_start S4_warning 15; sleep 2; start --es demo_action IGNITION_ON; sleep 1; rec_end S4_warning 15; }
s5() {
  scene LOW_RANGE; rec_start S5_range 15; sleep 2; start --es demo_action IGNITION_ON; sleep 5
  tap_text "주유소 경유" || { wait $RECPID; return 1; }; rec_end S5_range 15
}
# S6: 시동 켠 뒤 음성 질문·듣기가 끝날 만큼 기다렸다가(이전 실패 조건 재현) 화면의 "시동 끄기" 버튼으로 끈다
s6() {
  scene EXIT_MISTAKES; start --es demo_action IGNITION_ON; sleep 8; ensure_front
  rec_start S6_exit 15; sleep 3; tap_text "시동 끄기" || { wait $RECPID; return 1; }; rec_end S6_exit 15
}
s7() {
  scene WINTER_MORNING; start --es demo_tab LAMP; sleep 1; ensure_front
  rec_start S7_lamp 20; sleep 2; tap_text "등화 점검 시작" || { wait $RECPID; return 1; }; sleep 3
  tap_text "켜졌어요" || { wait $RECPID; return 1; }; sleep 1.5
  tap_text "켜졌어요" || { wait $RECPID; return 1; }; sleep 1.5
  tap_text "안 켜졌어요" || { wait $RECPID; return 1; }; sleep 1
  local i; for i in 1 2 3 4 5 6; do tap_text "켜졌어요" >/dev/null || break; sleep 0.6; done
  rec_end S7_lamp 20 noredraw
}
s8() {
  scene WINTER_MORNING; start --es demo_tab CHECKLIST; sleep 1; ensure_front
  rec_start S8_checklist 12; sleep 2
  "$ADB" shell input swipe 1700 1150 1700 450 600; sleep 1.5
  tap_text "확인했어요" 1 || { wait $RECPID; return 1; }; sleep 1
  tap_text "확인했어요" 2 || { wait $RECPID; return 1; }; sleep 1
  tap_text "손볼 필요" 3 || { wait $RECPID; return 1; }; sleep 1
  rec_end S8_checklist 12 noredraw
}

shot S1_normal 12 "음성 브리핑" s1
shot S2_winter 20 "겨울 출발 준비 실행" s2
shot S3_preset 10 "켰어요" s3
shot S4_warning 15 "충전 계통 경고" s4
shot S5_range 15 "경유지" s5
shot S6_exit 15 "내리기 전 확인" s6
shot S7_lamp 20 "다시 점검" s7
shot S8_checklist 12 "손볼 필요" s8

# ---------------- pleos S9~S11 + 주행 중 스크린샷 ----------------
if want S9_live || want S10_navi_range || want S11_waypoint || want pleos_driving; then
"$ADB" shell am force-stop --user 10 $MOCK   # 앱은 한 번에 하나만
PKG=$PLEOS
"$ADB" forward tcp:8088 tcp:8088 >/dev/null
( while true; do curl -s -m 2 -X POST "$VC/browser/session?client=cli" >/dev/null; sleep 1; done ) & HB=$!
trap 'kill $HB 2>/dev/null' EXIT
sleep 2
# 다음 촬영을 위해 Virtual Console 항목 목록을 남긴다(누적 주행거리 설정 항목 확인용)
curl -s -m 5 "$VC/vehicle/catalog" > "$LOGS/vc_catalog.json"
vc "gear?gear=Park"; vc "controls?accelerator=0&brake=0&steering=0"; vc "body?door_fl=false"; vc "body?cabin_light=false"
# 누적 주행거리 48,620km (Virtual Console catalog: perf-odometer, field traveled_distance_km, 11-06 확인)
vc "odometer?traveled_distance_km=48620"
# 주행가능거리 넉넉히(380km) — 비워 두면 0km로 읽혀 "0km라 빠듯해요"가 나옴(B++++ 2). S10에서만 20km.
vc "odometer?remaining_range_m=380000"
start --es demo_tab HOME; sleep 3; ensure_front

drive_shot() {
  start --es demo_action IGNITION_ON; sleep 6; ensure_front
  vc "gear?gear=Drive"; vc "controls?accelerator=30&brake=0&steering=0"; sleep 10
  local ok=0; has_text "주행 중에는 음성으로" && ok=1
  "$ADB" exec-out screencap -p > "$OUT/pleos_driving.png"
  vc "controls?accelerator=0&brake=100&steering=0"; sleep 6; vc "gear?gear=Park"; vc "controls?accelerator=0&brake=0&steering=0"; sleep 3
  [ $ok = 1 ] && SUMMARY+=("pleos_driving OK") || SUMMARY+=("pleos_driving FAIL")
}
want pleos_driving && drive_shot

s9() {
  start --es demo_action IGNITION_ON; sleep 8; start --es demo_action DISMISS_MESSAGE; sleep 1; ensure_front
  vc "body?door_fl=false"; vc "body?cabin_light=false"; sleep 1
  rec_start S9_live 15; sleep 2; vc "body?door_fl=true"; sleep 2; vc "body?cabin_light=true"; sleep 2
  tap_text "시동 끄기" || { wait $RECPID; return 1; }; rec_end S9_live 15
}
s10() {
  vc "body?door_fl=false"; vc "body?cabin_light=false"; vc "odometer?remaining_range_m=20000"
  start --es demo_action SEED_CALENDAR; sleep 3; start --es demo_action DISMISS_MESSAGE; sleep 1; ensure_front
  rec_start S10_navi_range 45; sleep 2; start --es demo_action IGNITION_ON; sleep 10
  tap_text "길안내 시작" || { wait $RECPID; return 1; }; sleep 14; ensure_front; rec_end S10_navi_range 45
}
s11() {
  ensure_front; rec_start S11_waypoint 30; sleep 2
  tap_text "전기차 충전소 경유" || tap_text "주유소 경유" || { wait $RECPID; return 1; }
  rec_end S11_waypoint 30
}
shot S9_live 15 "내리기 전 확인" s9
shot S10_navi_range 45 "주행가능거리 부족" s10
shot S11_waypoint 30 "다시 찾기|경유지로" s11
start --es demo_tab HOME
fi

echo "===== 요약 $(date +%H:%M:%S)"
printf '%s\n' "${SUMMARY[@]}"
