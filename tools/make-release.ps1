# Builds everything and collects the GitHub release files into release\
#   powershell -ExecutionPolicy Bypass -File tools\make-release.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
& "$PSScriptRoot\build-android.ps1"
& "$root\windows-receiver\build-exe.ps1"
$ver = (Select-String -Path "$root\android-app\app\build.gradle.kts" -Pattern 'versionName = "(.+)"').Matches[0].Groups[1].Value
$out = Join-Path $root "release"
New-Item -ItemType Directory -Force $out | Out-Null
$apk = Get-ChildItem -Recurse "$root\android-app\app\build\outputs\apk" -Filter *.apk | Sort-Object LastWriteTime | Select-Object -Last 1
Copy-Item $apk.FullName "$out\DIYF1Wheel-$ver.apk" -Force
Compress-Archive -Path "$root\windows-receiver\dist\F1WheelReceiver" -DestinationPath "$out\F1WheelReceiver-windows-$ver.zip" -Force
Get-ChildItem $out -Exclude SHA256SUMS.txt | Get-FileHash -Algorithm SHA256 | ForEach-Object { "$($_.Hash.ToLower())  $(Split-Path $_.Path -Leaf)" } |
    Set-Content -Encoding ascii "$out\SHA256SUMS.txt"
Get-ChildItem $out | Format-Table Name, Length
