@echo off
REM AI Recorder - Start All Services
REM One-click start: frontend build + backend + Cloudflare tunnel

echo ============================================
echo   AI Recorder - Start All Services
echo ============================================
echo.

set "ROOT=%~dp0"

REM Check if port 8000 is already in use
echo [CHECK] Verifying port 8000...
set "PORT_BUSY="
for /f "tokens=4,5" %%a in ('netstat -ano ^| findstr /R /C:":8000 " ^| findstr "LISTENING"') do (
    set "PORT_BUSY=%%a"
)
if defined PORT_BUSY (
    echo [WARN] Port 8000 is already in use.
    echo        Try running stop-all.bat first, or:
    echo        Run as administrator: netstat -ano ^| findstr :8000
    echo.
)

REM Step 1: Check if frontend dist exists; build if not
echo [1/4] Checking frontend build...
if not exist "%ROOT%frontend\dist\index.html" (
    echo        Frontend dist not found. Building now...
    pushd "%ROOT%frontend"
    if exist "package.json" (
        call npm.cmd run build
        if errorlevel 1 (
            echo [ERROR] Frontend build failed. Please run setup.ps1 first.
            popd
            pause
            exit /b 1
        )
    ) else (
        echo [ERROR] frontend\package.json not found.
        popd
        pause
        exit /b 1
    )
    popd
    echo        Frontend built successfully.
) else (
    echo        Frontend dist exists, skipping build.
)
echo.

REM Step 2: Start backend
echo [2/4] Starting Backend...
call "%ROOT%start-backend.bat"
echo.

REM Step 3: Wait for backend to be ready
echo [3/4] Waiting for backend to be ready...
set "READY="
for /l %%i in (1, 1, 20) do (
    timeout /t 1 /nobreak >NUL
    powershell -Command "try { (Invoke-WebRequest -Uri 'http://127.0.0.1:8000/health' -UseBasicParsing -TimeoutSec 2 -Method GET).StatusCode } catch { exit 1 }" >NUL 2>&1
    if not errorlevel 1 (
        set "READY=1"
        echo        Backend is ready.
        goto :backend_ready
    )
)
echo [WARN] Backend did not respond in 20s. Continuing anyway...
:backend_ready
echo.

REM Step 4: Start Cloudflare tunnel (optional, if available)
echo [4/4] Starting Cloudflare Tunnel...
if exist "%ROOT%start-tunnel.bat" (
    call "%ROOT%start-tunnel.bat"
) else (
    echo        start-tunnel.bat not found, skipping.
)
echo.

REM Open browser
echo Opening browser...
timeout /t 2 /nobreak >NUL
start "" http://127.0.0.1:8000

echo ============================================
echo   Services Started!
echo   Backend : http://127.0.0.1:8000
echo   Frontend: http://127.0.0.1:8000 (SPA mounted by backend)
echo   Tunnel  : (configured via start-tunnel.bat)
echo ============================================
echo.
echo   Run stop-all.bat to stop all services.
echo.
pause
