#!/usr/bin/env bash
# #005 2단계 확인 (통합 release APK, 화면을 눌러서 진행 — release 는 시연 인텐트를 받지 않는다).
# 준비: tools/emu_start.ps1 → READY → adb install -r --user 10 apk/chageunchageun.apk → 이 스크립트.
# 결과: screenshots/005/1~4_*.png, screenshots/005/logs/ (crash·events, Virtual Console 값 전후)
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
ROOT="$(cd "$(dirname "$0")/.." && pwd -W)"
OUT="$ROOT/screenshots/005"; LOGS="$OUT/logs"
VC=http://127.0.0.1:8088
PKG=com.chageunchageun.app
export MSYS_NO_PATHCONV=1
mkdir -p "$OUT" "$LOGS"
RESULT=()

start() { "$ADB" shell am start --user 10 -n "$PKG/com.chageunchageun.app.MainActivity" >/dev/null 2>&1; }
front() { "$ADB" shell dumpsys activity activities | grep -m1 "topResumedActivity" | grep -q "$PKG/"; }
ensure_front() { for i in 1 2 3; do front && return 0; start; sleep 2; done; return 1; }
dump() { "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; "$ADB" shell cat /sdcard/ui.xml | tr '>' '\n'; }
has_text() { dump | grep -qE "text=\"[^\"]*($1)[^\"]*\""; }
tap_text() {
  local name=$1 b; b=$(dump | grep "text=\"$name\"" | head -1 | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+')
  set -- $b; [ -n "$4" ] || { echo "  없음: '$name'"; return 1; }
  "$ADB" shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}
vc() { curl -s -m 5 -X PUT "$VC/vehicle/$1" >/dev/null || echo "  WARN: vc $1"; }
shot() { "$ADB" exec-out screencap -p > "$OUT/$1.png"; }
crashed() { "$ADB" logcat -d -b crash > "$LOGS/$1_crash.txt"; "$ADB" logcat -d -b events | grep -E "am_anr|am_crash" | grep "$PKG" > "$LOGS/$1_events.txt"; grep -q "$PKG" "$LOGS/$1_crash.txt" || [ -s "$LOGS/$1_events.txt" ]; }  # 시스템 프로세스 크래시는 세지 않음
judge() { # 이름 기대문구
  local ok=1; crashed "$1" && { echo "  FAIL: 크래시"; ok=0; }; has_text "$2" || { echo "  FAIL: '$2' 없음"; ok=0; }
  shot "$1"; [ $ok = 1 ] && RESULT+=("$1 OK") || RESULT+=("$1 FAIL"); }

"$ADB" forward tcp:8088 tcp:8088 >/dev/null
( while true; do curl -s -m 2 -X POST "$VC/browser/session?client=cli" >/dev/null; sleep 1; done ) & HB=$!
trap 'kill $HB 2>/dev/null' EXIT
sleep 2
vc "gear?gear=Park"; vc "body?door_fl=false"; vc "body?cabin_light=false"
vc "odometer?traveled_distance_km=48620"; vc "odometer?remaining_range_m=380000"
"$ADB" logcat -c -b crash -b events 2>/dev/null
"$ADB" shell am force-stop --user 10 $PKG; start; sleep 25; ensure_front   # 연결·기능 확인 + 출발 브리핑 시간

# 1. 연동 모드로 시작 → 문 열기 → 시동 끄기 → 하차 점검
echo "1 $(date +%H:%M:%S)"
has_text "데모 모드" && echo "  WARN: 데모 모드로 시작됨(연결 실패?)"
vc "body?door_fl=true"; sleep 2
tap_text "시동 끄기"; sleep 6
judge 1_live_exit "문 덜 닫힘"
vc "body?door_fl=false"; sleep 1

# 2. 데모 켜기 → 겨울 아침 → 시동 켜기 → "데모" 배지
echo "2 $(date +%H:%M:%S)"
tap_text "데모" && sleep 2 && tap_text "켜기"; sleep 3
tap_text "겨울 아침"; sleep 2; tap_text "닫기"; sleep 1
tap_text "시동 켜기"; sleep 8
judge 2_demo_winter "겨울 출발 준비 실행"
has_text "^데모$|데모" || echo "  WARN: 데모 배지 확인 필요"

# 3. 데모에서 겨울 출발 준비 → "실제 차량은 제어하지 않았어요", Virtual Console 값 그대로
echo "3 $(date +%H:%M:%S)"
curl -s -m 5 "$VC/vehicle/state" > "$LOGS/3_vc_before.json"
tap_text "겨울 출발 준비 실행"; sleep 4
curl -s -m 5 "$VC/vehicle/state" > "$LOGS/3_vc_after.json"
judge 3_demo_preset "실제 차량은 제어하지 않았어요"
python -I -c "
import json,sys
a=json.load(open(sys.argv[1],encoding='utf-8')); b=json.load(open(sys.argv[2],encoding='utf-8'))
def flat(d,p=''):
    out={}
    if isinstance(d,dict):
        for k,v in d.items(): out.update(flat(v,p+k+'.'))
    else: out[p]=d
    return out
fa,fb=flat(a),flat(b)
keys=[k for k in fa if any(w in k.lower() for w in ('heat','hvac','seat','mirror','steering','temp')) and 'timestamp' not in k]
diff=[k for k in keys if fa.get(k)!=fb.get(k)]
print('  VC 열선·공조 항목', len(keys), '개, 바뀐 것', len(diff), '개', diff[:5])
" "$LOGS/3_vc_before.json" "$LOGS/3_vc_after.json"

# 4. 데모 끄기 → 연동 값(48,620km)으로
echo "4 $(date +%H:%M:%S)"
tap_text "데모"; sleep 15
judge 4_live_again "48,620"

echo "===== 결과 $(date +%H:%M:%S)"; printf '%s\n' "${RESULT[@]}"
