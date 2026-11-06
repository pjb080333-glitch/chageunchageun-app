# 에뮬레이터를 끄고 종료 시각을 남긴다 (#004 F0: 다시 켜기까지 10분 휴식).
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb emu kill 2>$null | Out-Null
Start-Sleep 5
Get-Process qemu-system-x86_64* -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
(Get-Date).ToString('s') | Set-Content "$PSScriptRoot\.emu_last_stop"
Write-Output ("STOPPED {0:HH:mm:ss} — 다시 켜기는 {1:HH:mm} 이후" -f (Get-Date), (Get-Date).AddMinutes(10))
