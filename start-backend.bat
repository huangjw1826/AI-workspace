@echo off
REM AI Recorder - Start Backend
REM 启动后端 FastAPI 服务（隐藏窗口模式）
REM
REM 使用 Start-Process -WindowStyle Hidden 运行 uvicorn，
REM 避免后台服务占用终端窗口。日志输出到 logs/ 目录。
REM
REM Python 虚拟环境查找优先级：
REM 1. backend\.venv\Scripts\python.exe（推荐）
REM 2. .venv\Scripts\python.exe（兼容历史配置）

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

REM 启动 uvicorn（隐藏窗口模式）
REM - 使用 WindowStyle Hidden 避免控制台窗口
REM - 绑定 127.0.0.1:8000（仅本地访问）
REM - 工作目录设为 backend/（确保 .env 文件可被正确加载）
powershell -Command "Start-Process -WindowStyle Hidden -FilePath '%PYTHON_EXE%' -ArgumentList '-m uvicorn app.main:app --host 127.0.0.1 --port 8000' -WorkingDirectory '%BACKEND%'"

echo [OK] Backend started (hidden window).
echo      API: http://127.0.0.1:8000

endlocal
