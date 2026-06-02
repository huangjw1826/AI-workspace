@echo off
echo ============================================
echo   AI Recorder - Stop All Services
echo ============================================
echo.

echo [1/2] Stopping Cloudflare Tunnel...
taskkill /F /T /IM cloudflared.exe 2>&1
if errorlevel 1 (echo   cloudflared not running.)

echo.
echo [2/2] Stopping Backend (port 8000)...
set "found="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr /C:":8000" ^| findstr /C:"LISTENING" 2^>NUL') do (
    taskkill /F /PID %%a 2>&1
    set "found=1"
)
if not defined found echo   backend not running.

echo.
echo ============================================
echo   All services stopped.
echo ============================================
pause
