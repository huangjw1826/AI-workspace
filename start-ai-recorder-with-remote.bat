@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Starting AI Recorder (with Remote Access)...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1" -OpenBrowser -RemoteAccess
if errorlevel 1 (
  echo.
  echo Startup failed. Please run check-ai-recorder.bat for details.
  pause
)
