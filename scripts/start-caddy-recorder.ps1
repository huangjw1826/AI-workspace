param(
    [string]$HostName = "recorder.weizziwong.top",
    [string]$Email = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$CaddyExe = Join-Path $ProjectRoot ".tools\caddy.exe"
$RuntimeDir = Join-Path $ProjectRoot ".workbuddy\caddy"
$LogDir = Join-Path $ProjectRoot "logs"
$Caddyfile = Join-Path $RuntimeDir "Caddyfile"
$PidFile = Join-Path $LogDir "caddy.pid"
$OutLog = Join-Path $LogDir "caddy.out.log"
$ErrLog = Join-Path $LogDir "caddy.err.log"

try {
    [Environment]::SetEnvironmentVariable("PATH", $null, "Process")
}
catch {
}

if (!(Test-Path -LiteralPath $CaddyExe)) {
    throw "Caddy is missing at $CaddyExe. Download caddy.exe into .tools first."
}

New-Item -ItemType Directory -Path $RuntimeDir -Force | Out-Null
New-Item -ItemType Directory -Path $LogDir -Force | Out-Null

if (Test-Path -LiteralPath $PidFile) {
    $oldPid = Get-Content -LiteralPath $PidFile -ErrorAction SilentlyContinue
    if ($oldPid -match '^\d+$') {
        Stop-Process -Id ([int]$oldPid) -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
}

$globalBlock = ""
if (-not [string]::IsNullOrWhiteSpace($Email)) {
    $globalBlock = @"
{
    email $Email
}

"@
}

$content = @"
$globalBlock$HostName {
    encode zstd gzip

    header {
        X-Content-Type-Options nosniff
        Referrer-Policy no-referrer
    }

    reverse_proxy 127.0.0.1:8000
}
"@

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($Caddyfile, $content, $utf8NoBom)
[System.IO.File]::WriteAllText($OutLog, "", $utf8NoBom)
[System.IO.File]::WriteAllText($ErrLog, "", $utf8NoBom)

$caddyArgs = "run --config `"$Caddyfile`" --adapter caddyfile"

$process = Start-Process `
    -FilePath $CaddyExe `
    -WorkingDirectory $RuntimeDir `
    -WindowStyle Hidden `
    -PassThru `
    -RedirectStandardOutput $OutLog `
    -RedirectStandardError $ErrLog `
    -ArgumentList $caddyArgs

$process.Id | Set-Content -LiteralPath $PidFile -Encoding UTF8
Write-Host "Caddy started for https://$HostName with PID $($process.Id)"
