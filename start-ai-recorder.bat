@echo off
cd /d "%~dp0"

echo ========================================
echo AI Recorder Launcher
echo ========================================
echo.
echo Please select startup mode:
echo 1. Local only
echo 2. Local + Remote Access
echo 3. Exit
echo.
set /p choice="Enter option (1-3): "

if "%choice%"=="1" (
    echo.
    echo Starting AI Recorder (Local only)...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1" -OpenBrowser
) else if "%choice%"=="2" (
    echo.
    echo Starting AI Recorder (Local + Remote Access)...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1" -OpenBrowser -RemoteAccess
) else if "%choice%"=="3" (
    echo Exiting launcher.
    exit /b 0
) else (
    echo.
    echo Invalid option! Starting in local mode...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1" -OpenBrowser
)

if errorlevel 1 (
  echo.
  echo Startup failed. Please run check-ai-recorder.bat for details.
  pause
)
