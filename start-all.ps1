param(
    [switch]$OpenBrowser,
    [switch]$RemoteAccess
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$LogDir = "$Root\logs"
$BackendPython = "$Root\backend\.venv\Scripts\python.exe"
$FrontendDist = "$Root\frontend\dist\index.html"
$BackendOut = "$LogDir\backend.out.log"
$BackendErr = "$LogDir\backend.err.log"
$BackendPid = "$LogDir\backend.pid"

function Write-Info($Message) {
    Write-Host "[INFO] $Message"
}

function Write-Ok($Message) {
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Warn($Message) {
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Write-Error($Message) {
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

function Test-PortOpen($Port) {
    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    return $null -ne $connection
}

function Test-HttpOk($Url) {
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
        return ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500)
    }
    catch {
        return $false
    }
}

function Wait-HttpOk($Url, $Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                return $true
            }
        }
        catch {
            Start-Sleep -Milliseconds 500
        }
    }
    return $false
}

function Show-RecentLog($Path, $Title) {
    if (Test-Path -LiteralPath $Path) {
        Write-Warn $Title
        Get-Content -LiteralPath $Path -Tail 20 -ErrorAction SilentlyContinue
    }
}

function Repair-ProcessEnvironment {
    try {
        [Environment]::SetEnvironmentVariable("PATH", $null, "Process")
    }
    catch {
    }
}

function Start-RemoteAccess {
    Write-Info "Starting remote access service..."
    
    $managerScript = "$Root\scripts\remote-access-manager.ps1"
    if (-not (Test-Path -LiteralPath $managerScript)) {
        Write-Warn "Remote access manager script not found: $managerScript"
        return $false
    }
    
    try {
        $tunnelName = "ai-recorder"
        $result = & "$managerScript" -Action start -TunnelName $tunnelName
        
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "Remote access service started successfully"
            return $true
        }
        else {
            Write-Warn "Remote access service failed to start"
            return $false
        }
    }
    catch {
        Write-Warn "Failed to start remote access: $_"
        return $false
    }
}

function Check-RemoteAccessConfig {
    Write-Info "Checking remote access configuration..."
    
    $cloudflaredPath = "$Root\.tools\cloudflared.exe"
    if (-not (Test-Path -LiteralPath $cloudflaredPath)) {
        $cloudflaredPath = "C:\Program Files (x86)\cloudflared\cloudflared.exe"
    }
    
    if (-not (Test-Path -LiteralPath $cloudflaredPath)) {
        Write-Warn "cloudflared binary not found. Remote access may not work."
        return $false
    }
    
    $configPath = Join-Path $env:USERPROFILE ".cloudflared\config.yml"
    if (-not (Test-Path -LiteralPath $configPath)) {
        Write-Warn "Cloudflare config not found at: $configPath"
        return $false
    }
    
    Write-Ok "Remote access configuration is ready"
    return $true
}

Repair-ProcessEnvironment
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

if (!(Test-Path -LiteralPath $BackendPython)) {
    throw "Backend Python is missing: $BackendPython. Run .\setup.ps1 first."
}

if (!(Test-Path -LiteralPath $FrontendDist)) {
    throw "Frontend build is missing: $FrontendDist. Run .\setup.ps1 first."
}

if (Test-PortOpen 8000) {
    if ((Test-HttpOk "http://127.0.0.1:8000/health") -and (Test-HttpOk "http://127.0.0.1:8000/")) {
        Write-Ok "AI Recorder is already running"
        Write-Host "App: http://127.0.0.1:8000"
        if ($OpenBrowser) {
            Start-Process "http://127.0.0.1:8000"
        }
        exit 0
    }
    else {
        Write-Warn "Port 8000 is already in use."
        Write-Host "If AI Recorder is already open, visit: http://127.0.0.1:8000"
        Write-Host "If the page does not open, run .\stop-all.ps1 and then .\start-all.ps1"
        exit 1
    }
}

Set-Content -LiteralPath $BackendOut -Value "" -Encoding UTF8
Set-Content -LiteralPath $BackendErr -Value "" -Encoding UTF8

Write-Info "Starting backend service..."
$Backend = Start-Process -FilePath $BackendPython -WorkingDirectory "$Root\backend" -WindowStyle Hidden -PassThru -RedirectStandardOutput $BackendOut -RedirectStandardError $BackendErr -ArgumentList "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"
$Backend.Id | Set-Content -LiteralPath $BackendPid -Encoding UTF8

if (!(Wait-HttpOk "http://127.0.0.1:8000/health" 40)) {
    Show-RecentLog $BackendErr "Backend did not respond. Recent error log:"
    throw "Backend failed to start. Run .\check.ps1 to inspect the project."
}

if (!(Wait-HttpOk "http://127.0.0.1:8000/" 20)) {
    Show-RecentLog $BackendErr "Frontend shell did not respond. Recent backend error log:"
    throw "Frontend shell failed to load from backend. Run .\check.ps1 to inspect the project."
}

Write-Ok "AI Recorder backend started"
Write-Host "Local App: http://127.0.0.1:8000"

if ($RemoteAccess) {
    if (Check-RemoteAccessConfig) {
        Start-RemoteAccess
    }
}

Write-Host ""
Write-Host "Open this URL in your browser: http://127.0.0.1:8000"

if ($OpenBrowser) {
    Start-Process "http://127.0.0.1:8000"
}