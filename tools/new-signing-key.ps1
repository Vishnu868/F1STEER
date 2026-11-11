# Creates a private release signing key for the APK (keep keystore\ backed up - Android
# only accepts updates signed with the same key). Never commit keystore\ or keystore.properties.
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$ks = Join-Path $root "keystore\release.jks"
if (Test-Path $ks) { throw "$ks already exists" }
New-Item -ItemType Directory -Force (Split-Path $ks) | Out-Null
$pw = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
& "$root\.toolchain\jdk21\bin\keytool.exe" -genkeypair -keystore $ks -alias diyf1wheel -keyalg RSA -keysize 3072 `
    -validity 10000 -storepass $pw -keypass $pw -dname "CN=DIY F1 Wheel, O=DIY F1 Wheel"
"storeFile=../keystore/release.jks`nstorePassword=$pw`nkeyAlias=diyf1wheel`nkeyPassword=$pw" |
    Set-Content -Encoding ascii (Join-Path $root "android-app\keystore.properties")
Write-Host "Created $ks and android-app\keystore.properties. Back both up somewhere private."
