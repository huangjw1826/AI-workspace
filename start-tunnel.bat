@echo off
taskkill /F /T /IM cloudflared.exe 2>NUL
set "CLOUDFLARED_DIR=%USERPROFILE%\.cloudflared"
if not exist "%CLOUDFLARED_DIR%" (
    echo [WARN] Cloudflare tunnel config directory not found: %CLOUDFLARED_DIR%
    echo        Please run 'cloudflared tunnel login' first to set up tunnel.
    exit /b 0
)
powershell -Command "Start-Process -WindowStyle Hidden -FilePath 'C:\Program Files (x86)\cloudflared\cloudflared.exe' -ArgumentList 'tunnel run' -WorkingDirectory '%CLOUDFLARED_DIR%'"
