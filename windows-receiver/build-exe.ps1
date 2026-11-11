# Builds dist\F1WheelReceiver\F1WheelReceiver.exe (folder build: starts fast and
# triggers fewer antivirus false positives than a single-file exe).
#   powershell -ExecutionPolicy Bypass -File build-exe.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
python -m pip install --disable-pip-version-check -q -r requirements.txt pyinstaller==6.22.3
python -m unittest discover -s tests
if ($LASTEXITCODE -ne 0) { throw "Unit tests failed" }
python -m PyInstaller --noconfirm --clean --windowed --name F1WheelReceiver `
    --collect-all vgamepad --icon NONE launcher.py
Write-Host "Built: $PSScriptRoot\dist\F1WheelReceiver\F1WheelReceiver.exe"
