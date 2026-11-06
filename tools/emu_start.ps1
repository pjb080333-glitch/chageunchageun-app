# 에뮬레이터는 이 스크립트로만 켠다 (#004 F0, 강제 종료 대책).
# - 옵션 고정: -cores 2, 콜드 부팅(-no-snapshot), 부팅 애니메이션 없음. GPU 는 AVD 설정(host) 그대로.
# - 휴식 확인: 마지막 종료 후 10분, 마지막 빌드 후 5분이 안 됐으면 켜지 않는다.
# - 부팅이 끝나도 2분은 기다린 뒤 "READY" 를 출력한다(설치·녹화 금지 구간).
# 끌 때는 tools/emu_stop.ps1 (종료 시각을 남겨 다음 휴식 계산에 쓴다).
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$sdk\platform-tools\adb.exe"
$stamp = "$PSScriptRoot\.emu_last_stop"

$now = Get-Date
if (Test-Path $stamp) {
    $stop = [datetime](Get-Content $stamp)
    $left = 10 - ($now - $stop).TotalMinutes
    if ($left -gt 0) { Write-Output ("REST: 에뮬레이터 종료 후 10분 휴식 중 — {0:N0}분 뒤에 켤게요 ({1:HH:mm})" -f [math]::Ceiling($left), $stop.AddMinutes(10)); exit 2 }
}
$apk = Get-ChildItem "$root\app\build\outputs\apk\*\*\*.apk" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($apk) {
    $left = 5 - ($now - $apk.LastWriteTime).TotalMinutes
    if ($left -gt 0) { Write-Output ("REST: 빌드 후 5분 휴식 중 — {0:N0}분 뒤에 켤게요 ({1:HH:mm})" -f [math]::Ceiling($left), $apk.LastWriteTime.AddMinutes(5)); exit 2 }
}
# adb 가 처음 켜질 때 stderr 안내(daemon not running…)를 PS 5.1 이 오류로 보고 멈추지 않게 (#004 B++++)
$ErrorActionPreference = 'Continue'
& $adb start-server 2>&1 | Out-Null
if ((& $adb devices) -match 'emulator-\d+\s+device') { Write-Output 'ALREADY: 에뮬레이터가 이미 켜져 있어요(세션당 부팅 1회).'; exit 0 }

Write-Output ("START {0:HH:mm:ss}" -f (Get-Date))
Start-Process -FilePath "$sdk\emulator\emulator.exe" -ArgumentList '-avd', 'Pleos_API34', '-no-snapshot', '-cores', '2', '-no-boot-anim' -WindowStyle Normal
& $adb wait-for-device 2>&1 | Out-Null
do { Start-Sleep 3; $b = (& $adb shell getprop sys.boot_completed 2>&1 | Out-String) } until ($b -match '1')
Write-Output ("BOOTED {0:HH:mm:ss} — 2분 대기" -f (Get-Date))
Start-Sleep 120
Write-Output ("READY {0:HH:mm:ss}" -f (Get-Date))
