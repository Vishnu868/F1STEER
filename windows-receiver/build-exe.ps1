# Builds dist\F1WheelReceiver\F1WheelReceiver.exe (folder build: starts fast and
# triggers fewer antivirus false positives than a single-file exe).
#   powershell -ExecutionPolicy Bypass -File build-exe.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
if (Get-Process F1WheelReceiver -ErrorAction SilentlyContinue) { throw "Close F1WheelReceiver.exe before building" }
python -m pip install --disable-pip-version-check -q -r requirements.txt pyinstaller==6.22.3
python -m unittest discover -s tests
if ($LASTEXITCODE -ne 0) { throw "Unit tests failed" }
python -m PyInstaller --noconfirm --clean --windowed --name F1WheelReceiver `
    --collect-all vgamepad --icon NONE launcher.py
if ($LASTEXITCODE -ne 0) { throw "PyInstaller failed (is F1WheelReceiver.exe still running?)" }
Write-Host "Built: $PSScriptRoot\dist\F1WheelReceiver\F1WheelReceiver.exe"
