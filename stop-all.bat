@echo off
REM AI Recorder - Stop All Services
REM 停止后端、Cloudflare 隧道和相关进程

setlocal

echo ============================================
echo   AI Recorder - Stop All Services
echo ============================================
echo.

REM 停止 Cloudflare 隧道
echo [1/3] Stopping Cloudflare Tunnel...
taskkill /F /T /IM cloudflared.exe 2>NUL
if errorlevel 1 (
    echo        cloudflared not running.
) else (
    echo        cloudflared stopped.
)
echo.

REM 严格按端口停止 8000 上的进程（避免误杀 80000/80001 等）
echo [2/3] Stopping Backend (port 8000)...
set "FOUND="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr /R /C:":8000 " ^| findstr "LISTENING"') do (
    taskkill /F /PID %%a 2>NUL
    if not errorlevel 1 (
        echo        Stopped PID %%a
        set "FOUND=1"
    )
)
if not defined FOUND (
    echo        No process listening on port 8000.
)
echo.

REM 兜底：根据进程名清理可能的 uvicorn/python 残留
echo [3/3] Cleaning up uvicorn/python processes...
taskkill /F /IM uvicorn.exe /T 2>NUL
taskkill /F /IM cloudflared.exe /T 2>NUL
echo        Done.

echo.
echo ============================================
echo   All services stopped.
echo ============================================
echo.
pause

endlocal
