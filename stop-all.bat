@echo off
REM AI Recorder - Stop All Services
REM Stop backend, Cloudflare tunnel and related processes
REM
REM Stop strategy (by safety level):
REM 1. Stop cloudflared first (remote access tunnel)
REM 2. Stop backend by port 8000 (precise, avoid killing other ports)
REM 3. Clean up any remaining uvicorn/python processes

setlocal

echo ============================================
echo   AI Recorder - Stop All Services
echo ============================================
echo.

REM Step 1: Stop Cloudflare tunnel
echo [1/3] Stopping Cloudflare Tunnel...
taskkill /F /T /IM cloudflared.exe 2>NUL
if errorlevel 1 (
    echo        cloudflared not running.
) else (
    echo        cloudflared stopped.
)
echo.

REM Step 2: Stop backend process by port 8000 (precise regex match)
REM Note: /R /C:":8000 " avoids killing processes on port 80000/80001
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

REM Step 3: Cleanup remaining uvicorn/python processes
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
