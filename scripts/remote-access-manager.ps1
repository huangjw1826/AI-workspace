param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("start", "stop", "status", "check", "restart")]
    [string]$Action,

    [string]$ConfigPath,
    [string]$TunnelName,
    [string]$TunnelId,
    [string]$Hostname,
    [string]$LogDir = "$PSScriptRoot/../logs",
    [string]$CloudflaredPath = "$PSScriptRoot/../.tools/cloudflared.exe",
    [int]$Timeout = 30
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$LogDir = Resolve-Path (Join-Path $Root $LogDir) -ErrorAction SilentlyContinue
if (-not $LogDir) {
    $LogDir = Join-Path $Root "logs"
    New-Item -ItemType Directory -Path $LogDir -Force | Out-Null
}

$PidFile = Join-Path $LogDir "cloudflared.pid"
$LogFile = Join-Path $LogDir "cloudflared.log"

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logEntry = "[$timestamp] [$Level] $Message"
    Write-Host $logEntry
    Add-Content -Path $LogFile -Value $logEntry -Encoding UTF8
}

function Test-CommandExists {
    param([string]$Command)
    $exists = $null -ne (Get-Command $Command -ErrorAction SilentlyContinue)
    return $exists
}

function Get-CloudflaredPath {
    $candidates = @(
        $CloudflaredPath,
        "$Root/.tools/cloudflared.exe",
        "C:\Program Files (x86)\cloudflared\cloudflared.exe",
        (Get-Command "cloudflared.exe" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)
    )

    foreach ($path in $candidates) {
        if ($path -and (Test-Path -Path $path -PathType Leaf)) {
            return (Resolve-Path $path).Path
        }
    }

    return $null
}

function Get-TunnelStatus {
    $cloudflared = Get-CloudflaredPath
    if (-not $cloudflared) {
        return $null
    }

    try {
        $result = & $cloudflared tunnel info $TunnelName 2>&1
        if ($LASTEXITCODE -eq 0) {
            return $result
        }
    }
    catch {
        Write-Log "Failed to get tunnel info: $_" -Level ERROR
    }

    return $null
}

function Test-Preflight {
    Write-Log "Running preflight checks..."

    $cloudflared = Get-CloudflaredPath
    if (-not $cloudflared) {
        Write-Log "cloudflared binary not found" -Level ERROR
        return $false
    }
    Write-Log "[OK] Cloudflared binary found: $cloudflared"

    if (-not $ConfigPath) {
        $userConfig = Join-Path $env:USERPROFILE ".cloudflared\config.yml"
        if (Test-Path $userConfig) {
            $script:ConfigPath = $userConfig
        }
    }

    if ($ConfigPath -and (Test-Path $ConfigPath)) {
        Write-Log "[OK] Tunnel configuration found: $ConfigPath"
    }
    else {
        Write-Log "[WARN] Tunnel configuration not specified, using defaults"
    }

    if (-not $TunnelName -and -not $TunnelId) {
        Write-Log "[WARN] Neither TunnelName nor TunnelId specified"
    }

    return $true
}

function Start-RemoteAccess {
    Write-Log "Starting remote access service..."

    if (-not (Test-Preflight)) {
        Write-Log "Preflight checks failed" -Level ERROR
        return $false
    }

    $cloudflared = Get-CloudflaredPath

    if (Get-CurrentProcess) {
        Write-Log "Remote access is already running" -Level WARN
        return $true
    }

    $arguments = @("tunnel", "run")

    if ($ConfigPath) {
        $arguments += "--config", "`"$ConfigPath`""
    }

    if ($TunnelName) {
        $arguments += $TunnelName
    }
    elseif ($TunnelId) {
        $arguments += $TunnelId
    }

    Write-Log "Starting cloudflared with arguments: $arguments"

    Set-Content -Path $LogFile -Value "" -Encoding UTF8

    try {
        $process = Start-Process -FilePath $cloudflared -ArgumentList $arguments `
            -WorkingDirectory $Root -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput $LogFile -RedirectStandardError $LogFile

        $process.Id | Set-Content -Path $PidFile -Encoding UTF8
        Write-Log "Cloudflared started with PID: $($process.Id)"

        $deadline = (Get-Date).AddSeconds($Timeout)
        $connected = $false

        while ((Get-Date) -lt $deadline) {
            if (-not (Get-Process -Id $process.Id -ErrorAction SilentlyContinue)) {
                Write-Log "Cloudflared process terminated unexpectedly" -Level ERROR
                Show-RecentLogs
                return $false
            }

            if (Test-LogForConnection) {
                $connected = $true
                break
            }

            Start-Sleep -Milliseconds 500
        }

        if ($connected) {
            Write-Log "Remote access service started successfully"
            Show-ConnectionInfo
            return $true
        }
        else {
            Write-Log "Timeout waiting for tunnel connection" -Level ERROR
            Show-RecentLogs
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            Remove-Item -Path $PidFile -Force -ErrorAction SilentlyContinue
            return $false
        }
    }
    catch {
        Write-Log "Failed to start cloudflared: $_" -Level ERROR
        return $false
    }
}

function Stop-RemoteAccess {
    Write-Log "Stopping remote access service..."

    $savedPid = Get-CurrentPid
    if (-not $savedPid) {
        Write-Log "Remote access is not running" -Level WARN
        return $true
    }

    try {
        $process = Get-Process -Id $savedPid -ErrorAction SilentlyContinue
        if ($process) {
            Write-Log "Terminating cloudflared process (PID: $savedPid)"
            $process.Terminate()

            $deadline = (Get-Date).AddSeconds(10)
            while ((Get-Date) -lt $deadline) {
                if (-not (Get-Process -Id $savedPid -ErrorAction SilentlyContinue)) {
                    break
                }
                Start-Sleep -Milliseconds 200
            }

            if (Get-Process -Id $savedPid -ErrorAction SilentlyContinue) {
                Write-Log "Force killing cloudflared process" -Level WARN
                Stop-Process -Id $savedPid -Force
            }
        }

        Remove-Item -Path $PidFile -Force -ErrorAction SilentlyContinue
        Write-Log "Remote access service stopped successfully"
        return $true
    }
    catch {
        Write-Log "Failed to stop remote access: $_" -Level ERROR
        return $false
    }
}

function Get-CurrentPid {
    if (Test-Path -Path $PidFile) {
        $pidValue = Get-Content -Path $PidFile -Raw -ErrorAction SilentlyContinue
        if ($pidValue -match "^\d+$") {
            $savedPid = [int]$pidValue
            if (Get-Process -Id $savedPid -ErrorAction SilentlyContinue) {
                return $savedPid
            }
        }
    }
    return $null
}

function Get-CurrentProcess {
    $savedPid = Get-CurrentPid
    if ($savedPid) {
        return Get-Process -Id $savedPid -ErrorAction SilentlyContinue
    }
    return $null
}

function Test-LogForConnection {
    if (Test-Path -Path $LogFile) {
        $content = Get-Content -Path $LogFile -Raw -ErrorAction SilentlyContinue
        if ($content -match "Registered tunnel connection") {
            return $true
        }
    }
    return $false
}

function Show-RecentLogs {
    if (Test-Path -Path $LogFile) {
        Write-Log "Recent cloudflared logs:" -Level WARN
        Get-Content -Path $LogFile -Tail 20 | ForEach-Object {
            Write-Host "  $_"
        }
    }
}

function Show-ConnectionInfo {
    Write-Log "Connection information:"
    if ($Hostname) {
        Write-Log "  Hostname: $Hostname"
    }
    if ($TunnelName) {
        Write-Log "  Tunnel: $TunnelName"
    }
    Write-Log "  Log file: $LogFile"
}

function Get-Status {
    $savedPid = Get-CurrentPid

    if ($savedPid) {
        $process = Get-Process -Id $savedPid -ErrorAction SilentlyContinue
        if ($process) {
            Write-Output "Running"
            Write-Output "PID: $savedPid"
            Write-Output "Memory: $($process.WorkingSet64 / 1MB -as [int]) MB"

            $tunnelInfo = Get-TunnelStatus
            if ($tunnelInfo) {
                Write-Output ""
                $tunnelInfo | ForEach-Object { Write-Output $_ }
            }
            return
        }
    }

    Write-Output "Not running"

    if (Test-Path -Path $LogFile) {
        Write-Output ""
        Write-Output "Last log:"
        Get-Content -Path $LogFile -Tail 5
    }
}

function Test-BackendReady {
    Write-Log "Checking backend service..."
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:8000/health" `
            -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -eq 200) {
            Write-Log "[OK] Backend service is ready"
            return $true
        }
        else {
            Write-Log "[FAIL] Backend returned HTTP $($response.StatusCode)" -Level ERROR
            return $false
        }
    }
    catch {
        Write-Log "[FAIL] Backend service not reachable: $_" -Level ERROR
        return $false
    }
}

switch ($Action) {
    "start" {
        $success = Start-RemoteAccess
        if ($success) { exit 0 } else { exit 1 }
    }
    "stop" {
        $success = Stop-RemoteAccess
        if ($success) { exit 0 } else { exit 1 }
    }
    "status" {
        Get-Status
        exit 0
    }
    "check" {
        Write-Log "Running comprehensive remote access check..."
        Test-Preflight
        Test-BackendReady

        $tunnelInfo = Get-TunnelStatus
        if ($tunnelInfo) {
            Write-Output ""
            Write-Output "Tunnel Information:"
            $tunnelInfo
        }
        exit 0
    }
    "restart" {
        Stop-RemoteAccess | Out-Null
        Start-Sleep -Seconds 1
        $success = Start-RemoteAccess
        if ($success) { exit 0 } else { exit 1 }
    }
}
