@echo off
chcp 65001 >nul
cd /d "%~dp0"

:menu
cls
echo ========================================
echo 远程访问管理
echo ========================================
echo.
echo 1. 启动远程访问
echo 2. 停止远程访问
echo 3. 查看状态
echo 4. 运行检查
echo 5. 重启远程访问
echo 0. 退出
echo.
set /p choice="请选择操作 (0-5): "

if "%choice%"=="1" (
    cls
    echo ========================================
    echo 启动远程访问
    echo ========================================
    echo.
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\remote-access-manager.ps1" -Action start -TunnelName ai-recorder
    pause
    goto menu
) else if "%choice%"=="2" (
    cls
    echo ========================================
    echo 停止远程访问
    echo ========================================
    echo.
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\remote-access-manager.ps1" -Action stop
    pause
    goto menu
) else if "%choice%"=="3" (
    cls
    echo ========================================
    echo 远程访问状态
    echo ========================================
    echo.
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\remote-access-manager.ps1" -Action status
    pause
    goto menu
) else if "%choice%"=="4" (
    cls
    echo ========================================
    echo 远程访问检查
    echo ========================================
    echo.
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\remote-access-manager.ps1" -Action check
    pause
    goto menu
) else if "%choice%"=="5" (
    cls
    echo ========================================
    echo 重启远程访问
    echo ========================================
    echo.
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\remote-access-manager.ps1" -Action restart -TunnelName ai-recorder
    pause
    goto menu
) else if "%choice%"=="0" (
    exit /b 0
) else (
    echo 无效选项，请重新选择。
    pause
    goto menu
)
