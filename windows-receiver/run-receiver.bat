@echo off
rem One-click launcher for running the receiver from source.
rem First run creates a private Python environment in .venv and installs vgamepad.
setlocal
cd /d "%~dp0"
where py >nul 2>nul && (set PY=py -3) || (set PY=python)
if not exist ".venv\Scripts\pythonw.exe" (
    echo Setting up the receiver for the first time...
    %PY% -m venv .venv || goto :nopython
    ".venv\Scripts\python.exe" -m pip install --disable-pip-version-check -q -r requirements.txt || goto :pipfail
)
start "" ".venv\Scripts\pythonw.exe" -m f1wheel
exit /b 0

:nopython
echo Python 3.10+ is required. Install it from https://www.python.org/downloads/ (tick "Add to PATH"),
echo or use the packaged F1WheelReceiver.exe from the GitHub release instead.
pause
exit /b 1
:pipfail
echo Could not install dependencies. Check your internet connection and try again.
pause
exit /b 1
