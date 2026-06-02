@echo off
REM AI Recorder - Start Backend
REM Start FastAPI backend service (hidden window mode)
REM
REM Uses Start-Process -WindowStyle Hidden to run uvicorn
REM without blocking the terminal. Logs go to logs/ directory.
REM
REM Python venv search order:
REM 1. backend\.venv\Scripts\python.exe (recommended)
REM 2. .venv\Scripts\python.exe (legacy fallback)

setlocal

REM Project root (script directory)
set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"

REM Prefer backend\.venv, fall back to root .venv
set "PYTHON_EXE="
if exist "%BACKEND%\.venv\Scripts\python.exe" (
    set "PYTHON_EXE=%BACKEND%\.venv\Scripts\python.exe"
) else if exist "%ROOT%.venv\Scripts\python.exe" (
    set "PYTHON_EXE=%ROOT%.venv\Scripts\python.exe"
) else (
    echo [ERROR] Python virtual environment not found.
    echo         Expected: %BACKEND%\.venv\Scripts\python.exe
    echo         Or:       %ROOT%.venv\Scripts\python.exe
    echo         Please run setup.ps1 first to install dependencies.
    pause
    exit /b 1
)

REM Start uvicorn (hidden window mode)
REM - WindowStyle Hidden keeps the console window hidden
REM - Binds to 127.0.0.1:8000 (local only)
REM - WorkingDirectory is backend/ (ensures .env is loaded)
powershell -Command "Start-Process -WindowStyle Hidden -FilePath '%PYTHON_EXE%' -ArgumentList '-m uvicorn app.main:app --host 127.0.0.1 --port 8000' -WorkingDirectory '%BACKEND%'"

echo [OK] Backend started (hidden window).
echo      API: http://127.0.0.1:8000

endlocal
