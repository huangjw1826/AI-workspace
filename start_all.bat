@echo off
echo ============================================
echo   AI Recorder - Start All Services
echo ============================================
echo.

echo [1/2] Starting Backend...
call "%~dp0start_backend.bat"
timeout /t 3 /nobreak >NUL

echo [2/2] Starting Cloudflare Tunnel...
call "%~dp0start_tunnel.bat"

echo.
echo Opening browser...
timeout /t 2 /nobreak >NUL
start http://localhost:8000

echo ============================================
echo   Services Started!
echo   Backend : http://localhost:8000
echo   Tunnel  : https://weizziwong.top
echo ============================================
pause
