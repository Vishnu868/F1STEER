# Runs the Android unit tests and builds the APK.
#   powershell -ExecutionPolicy Bypass -File tools\build-android.ps1
# Release signing: create android-app\keystore.properties (see docs\INSTALL-ANDROID.md);
# without it a debug-signed APK is produced instead.
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$tc = Join-Path $root ".toolchain"
$env:JAVA_HOME = "$tc\jdk21"
$env:GRADLE_USER_HOME = "$tc\gradle-home"
Set-Location (Join-Path $root "android-app")
$task = if (Test-Path "keystore.properties") { "assembleRelease" } else { "assembleDebug" }
& .\gradlew.bat testDebugUnitTest $task --console=plain
if ($LASTEXITCODE -ne 0) { throw "Android build failed" }
Get-ChildItem -Recurse app\build\outputs\apk -Filter *.apk | ForEach-Object { Write-Host "APK: $($_.FullName)" }
