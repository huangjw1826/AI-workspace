# AI Recorder - Environment Check
# Checks dependencies, ports, configuration and service status.

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Backend = Join-Path $Root 'backend'
$Frontend = Join-Path $Root 'frontend'

$pass = 0
$fail = 0
$warn = 0

function Write-Pass($msg) {
    Write-Host ('  [PASS] ' + $msg) -ForegroundColor Green
    $script:pass++
}

function Write-Fail($msg) {
    Write-Host ('  [FAIL] ' + $msg) -ForegroundColor Red
    $script:fail++
}

function Write-Warn($msg) {
    Write-Host ('  [WARN] ' + $msg) -ForegroundColor Yellow
    $script:warn++
}

function Write-Header($msg) {
    Write-Host ''
    Write-Host ('== ' + $msg + ' ==') -ForegroundColor Cyan
}

# ---------------------------------------------------------------------
Write-Header 'System Tools'
# ---------------------------------------------------------------------

foreach ($tool in @('python', 'node', 'npm', 'git', 'ffmpeg')) {
    $cmd = Get-Command $tool -ErrorAction SilentlyContinue
    if ($cmd) {
        try {
            $version = & $tool --version 2>&1 | Select-Object -First 1
            Write-Pass ("$tool - $version")
        } catch {
            Write-Pass "$tool - $($cmd.Source)"
        }
    } else {
        Write-Fail "$tool not found in PATH"
    }
}

# ---------------------------------------------------------------------
Write-Header 'Python Virtual Environment'
# ---------------------------------------------------------------------

$venvBackend = Join-Path $Backend '.venv'
$venvRoot = Join-Path $Root '.venv'

if (Test-Path -LiteralPath (Join-Path $venvBackend 'Scripts\python.exe')) {
    Write-Pass "backend\.venv exists"
} elseif (Test-Path -LiteralPath (Join-Path $venvRoot 'Scripts\python.exe')) {
    Write-Warn "backend\.venv not found, using root .venv (legacy)"
} else {
    Write-Fail "No Python virtual environment found. Run setup.ps1 first."
}

# ---------------------------------------------------------------------
Write-Header 'Python Dependencies'
# ---------------------------------------------------------------------

$pythonExe = $null
if (Test-Path -LiteralPath (Join-Path $venvBackend 'Scripts\python.exe')) {
    $pythonExe = Join-Path $venvBackend 'Scripts\python.exe'
} elseif (Test-Path -LiteralPath (Join-Path $venvRoot 'Scripts\python.exe')) {
    $pythonExe = Join-Path $venvRoot 'Scripts\python.exe'
}

if ($pythonExe) {
    $required = @('fastapi', 'uvicorn', 'sqlmodel', 'pydantic_settings', 'funasr', 'sse_starlette')
    foreach ($pkg in $required) {
        $check = & $pythonExe -c "import importlib; importlib.import_module('$($pkg -replace '_','_')'); print('ok')" 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Pass "$pkg installed"
        } else {
            Write-Fail "$pkg not installed"
        }
    }
} else {
    Write-Warn 'Skipping Python dependency check (no venv)'
}

# ---------------------------------------------------------------------
Write-Header 'Node.js Dependencies'
# ---------------------------------------------------------------------

if (Test-Path -LiteralPath (Join-Path $Frontend 'node_modules')) {
    Write-Pass "frontend\node_modules exists"
} else {
    Write-Fail "frontend\node_modules missing. Run setup.ps1 first."
}

# ---------------------------------------------------------------------
Write-Header 'Frontend Build'
# ---------------------------------------------------------------------

if (Test-Path -LiteralPath (Join-Path $Frontend 'dist\index.html')) {
    Write-Pass "frontend\dist\index.html exists"
} else {
    Write-Warn "frontend\dist missing. Will be built on first start-all."
}

# ---------------------------------------------------------------------
Write-Header 'Backend Configuration'
# ---------------------------------------------------------------------

if (Test-Path -LiteralPath (Join-Path $Backend '.env')) {
    Write-Pass "backend\.env exists"
} else {
    Write-Warn "backend\.env missing (will use defaults)"
}

if ($pythonExe -and (Test-Path -LiteralPath (Join-Path $Backend '.env'))) {
    $env:PYTHONPATH = $Backend
    $oldCwd = (Get-Location).Path
    Push-Location $Backend -ErrorAction SilentlyContinue
    try {
        $configCheck = & $pythonExe -c "from app.config import Settings; Settings()" 2>&1
    } finally {
        Pop-Location -ErrorAction SilentlyContinue
        Remove-Item Env:PYTHONPATH -ErrorAction SilentlyContinue
    }
    if ($LASTEXITCODE -eq 0) {
        Write-Pass "backend configuration is valid"
    } else {
        Write-Fail "backend configuration has errors:"
        $configCheck | ForEach-Object { Write-Host ('         ' + $_) -ForegroundColor Red }
    }
}

# ---------------------------------------------------------------------
Write-Header 'Port Status'
# ---------------------------------------------------------------------

$ports = @(8000, 5173)
foreach ($port in $ports) {
    $listeners = netstat -ano | Select-String ":$port " | Select-String 'LISTENING'
    if ($listeners) {
        $procId = ($listeners[0] -split '\s+')[-1]
        Write-Warn "Port $port is in use by PID $procId"
    } else {
        Write-Pass "Port $port is free"
    }
}

# ---------------------------------------------------------------------
Write-Header 'Service Status'
# ---------------------------------------------------------------------

try {
    $health = Invoke-WebRequest -Uri 'http://127.0.0.1:8000/health' -UseBasicParsing -TimeoutSec 3 -Method GET
    if ($health.StatusCode -eq 200) {
        Write-Pass "Backend health endpoint returns 200"
    } else {
        Write-Warn "Backend responded with $($health.StatusCode)"
    }
} catch {
    Write-Warn "Backend not running on http://127.0.0.1:8000"
}

# ---------------------------------------------------------------------
Write-Header 'Summary'
# ---------------------------------------------------------------------

Write-Host ''
Write-Host ('  Passed: ' + $pass) -ForegroundColor Green
Write-Host ('  Warnings: ' + $warn) -ForegroundColor Yellow
Write-Host ('  Failed: ' + $fail) -ForegroundColor Red
Write-Host ''

if ($fail -gt 0) {
    Write-Host 'Some checks failed. Run setup.ps1 to fix dependency issues.' -ForegroundColor Red
    exit 1
} elseif ($warn -gt 0) {
    Write-Host 'Environment is ready with minor warnings.' -ForegroundColor Yellow
    exit 0
} else {
    Write-Host 'Environment is fully ready. Run start-all.bat to launch services.' -ForegroundColor Green
    exit 0
}
