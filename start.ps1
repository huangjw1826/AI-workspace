# AI Recorder — 启动服务
# 启动 FastAPI 后端和可选的 Cloudflare Tunnel

$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path
$BACKEND = Join-Path $ROOT "backend"

# 查找 Python
$PYTHON = $null
if (Test-Path "$BACKEND\.venv\Scripts\python.exe") {
    $PYTHON = "$BACKEND\.venv\Scripts\python.exe"
} elseif (Test-Path "$ROOT\.venv\Scripts\python.exe") {
    $PYTHON = "$ROOT\.venv\Scripts\python.exe"
}

if (-not $PYTHON) {
    Write-Host "[错误] 未找到 Python 虚拟环境，请先运行 setup.ps1" -ForegroundColor Red
    exit 1
}

Write-Host "AI Recorder — 启动服务" -ForegroundColor Cyan
Write-Host ""

# 启动后端
Write-Host "[1/2] 启动后端..."
Start-Process -FilePath $PYTHON `
    -ArgumentList "-m uvicorn app.main:app --host 127.0.0.1 --port 8000" `
    -WorkingDirectory $BACKEND

# 等待后端就绪
Write-Host "[2/2] 等待后端就绪..."
for ($i = 1; $i -le 20; $i++) {
    try {
        $null = Invoke-WebRequest -Uri "http://127.0.0.1:8000/health" -UseBasicParsing -TimeoutSec 2
        Write-Host "       后端已就绪: http://127.0.0.1:8000" -ForegroundColor Green
        Start-Process "http://127.0.0.1:8000"
        exit 0
    } catch {
        Start-Sleep 1
    }
}

Write-Host "       后端未在 20 秒内响应，请检查 logs/ 目录" -ForegroundColor Yellow
Write-Host "       手动访问: http://127.0.0.1:8000"
