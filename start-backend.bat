@echo off
REM AI Recorder - Start Backend
REM 启动后端 FastAPI 服务（隐藏窗口）

setlocal

REM 项目根目录（脚本所在目录）
set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"

REM 优先使用 backend\.venv，回退到根 .venv（兼容历史配置）
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

REM 启动 uvicorn（隐藏窗口）
powershell -Command "Start-Process -WindowStyle Hidden -FilePath '%PYTHON_EXE%' -ArgumentList '-m uvicorn app.main:app --host 127.0.0.1 --port 8000' -WorkingDirectory '%BACKEND%'"

echo [OK] Backend started (hidden window).
echo      API: http://127.0.0.1:8000

endlocal
