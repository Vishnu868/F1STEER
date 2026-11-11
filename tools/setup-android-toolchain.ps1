# Downloads everything needed to build the Android app into .toolchain\ (no Android Studio).
#   powershell -ExecutionPolicy Bypass -File tools\setup-android-toolchain.ps1
# Sources: Eclipse Adoptium (JDK 21), Google (Android command-line tools + SDK), Gradle.
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$root = Split-Path $PSScriptRoot -Parent
$tc = Join-Path $root ".toolchain"
New-Item -ItemType Directory -Force "$tc\dl" | Out-Null

if (-not (Test-Path "$tc\jdk21\bin\java.exe")) {
    Write-Host "Downloading JDK 21 (Eclipse Temurin)..."
    Invoke-WebRequest "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile "$tc\dl\jdk21.zip"
    Expand-Archive "$tc\dl\jdk21.zip" "$tc\jdk-tmp" -Force
    Move-Item (Get-ChildItem "$tc\jdk-tmp" | Select-Object -First 1).FullName "$tc\jdk21"
    Remove-Item "$tc\jdk-tmp" -Recurse
}
$sdk = "$tc\android-sdk"
if (-not (Test-Path "$sdk\cmdline-tools\latest\bin\sdkmanager.bat")) {
    Write-Host "Downloading Android command-line tools..."
    Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip" -OutFile "$tc\dl\cmdline.zip"
    New-Item -ItemType Directory -Force "$sdk\cmdline-tools" | Out-Null
    Expand-Archive "$tc\dl\cmdline.zip" "$sdk\cmdline-tools" -Force
    Rename-Item "$sdk\cmdline-tools\cmdline-tools" "latest"
}
$env:JAVA_HOME = "$tc\jdk21"
Write-Host "Installing SDK platform 35 + build tools (you will be asked to accept Google's SDK licenses)..."
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="$sdk" --licenses
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root="$sdk" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
$sdkEsc = $sdk -replace '\', '\' -replace ':', '\:'
"sdk.dir=$sdkEsc" | Set-Content -Encoding ascii (Join-Path $root "android-app\local.properties")
Write-Host "Toolchain ready in $tc"
