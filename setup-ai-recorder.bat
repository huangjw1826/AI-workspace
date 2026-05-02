@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Preparing AI Recorder dependencies...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup.ps1"
pause
