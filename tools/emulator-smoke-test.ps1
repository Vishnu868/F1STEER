# Smoke test of the real APK in an Android emulator against the real receiver on this PC.
#   powershell -ExecutionPolicy Bypass -File tools\emulator-smoke-test.ps1
# Needs: tools\setup-android-toolchain.ps1, plus the "emulator" and
# "system-images;android-35;google_apis;x86_64" packages, and Windows Hypervisor Platform.
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$tc = Join-Path $root ".toolchain"
$sdk = "$tc\android-sdk"
$env:JAVA_HOME = "$tc\jdk21"
$env:ANDROID_HOME = $sdk
$env:ANDROID_AVD_HOME = "$tc\avd"
$adb = "$sdk\platform-tools\adb.exe"
New-Item -ItemType Directory -Force $env:ANDROID_AVD_HOME | Out-Null

if (-not (Test-Path "$env:ANDROID_AVD_HOME\f1wheel.avd")) {
    "no" | & "$sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n f1wheel `
        -k "system-images;android-35;google_apis;x86_64" -d pixel_6 --force | Out-Null
}
$emu = Start-Process "$sdk\emulator\emulator.exe" -ArgumentList "-avd f1wheel -no-window -no-audio -no-snapshot -gpu swiftshader_indirect -no-boot-anim" -PassThru -WindowStyle Hidden
& $adb wait-for-device
do { Start-Sleep 2; $b = (& $adb shell getprop sys.boot_completed 2>$null) } until ($b -match "1")
Write-Host "Emulator booted"

$apk = Get-ChildItem -Recurse "$root\android-app\app\build\outputs\apk" -Filter *.apk | Sort-Object LastWriteTime | Select-Object -Last 1
& $adb install -r $apk.FullName
Write-Host "Installed $($apk.Name). Emulator PID $($emu.Id) - stop it with: Stop-Process -Id $($emu.Id)"
