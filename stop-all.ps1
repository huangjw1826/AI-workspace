$ErrorActionPreference = "SilentlyContinue"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$LogDir = "$Root\logs"
$PidFiles = @(
    "$LogDir\backend.pid",
    "$LogDir\frontend.pid",
    "$LogDir\model-download.pid",
    "$LogDir\rerun-transcribe-task.pid"
)

function Stop-PidFile($Path) {
    if (!(Test-Path -LiteralPath $Path)) {
        return
    }

    $pidValue = Get-Content -LiteralPath $Path -ErrorAction SilentlyContinue
    if ($pidValue -match "^\d+$") {
        $process = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
        if ($process) {
            Write-Host "[INFO] Stopping PID $pidValue ($($process.ProcessName))"
            Stop-Process -Id ([int]$pidValue) -Force -ErrorAction SilentlyContinue
        }
    }

    Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
}

foreach ($file in $PidFiles) {
    Stop-PidFile $file
}

foreach ($port in @(8000, 5173)) {
    $owners = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique |
        Where-Object { $_ -and $_ -ne 0 }

    foreach ($owner in $owners) {
        $process = Get-Process -Id $owner -ErrorAction SilentlyContinue
        if ($process) {
            Write-Host "[INFO] Releasing port $port from PID $owner ($($process.ProcessName))"
            Stop-Process -Id $owner -Force -ErrorAction SilentlyContinue
        }
    }
}

Write-Host "[OK] AI Recorder services stopped."
