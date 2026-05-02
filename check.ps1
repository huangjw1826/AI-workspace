$ErrorActionPreference = "SilentlyContinue"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendPython = "$Root\backend\.venv\Scripts\python.exe"
$LocalNodeExe = "$Root\.tools\node-v24.14.0-win-x64\node.exe"
$LocalNpmCmd = "$Root\.tools\node-v24.14.0-win-x64\npm.cmd"
$LocalFfmpegExe = "$Root\.tools\ffmpeg\ffmpeg.exe"
$FrontendIndex = "$Root\frontend\dist\index.html"
$BackendEnv = "$Root\backend\.env"
$DataDir = "$Root\data"
$BackendVenv = "$Root\backend\.venv"
$FrontendNodeModules = "$Root\frontend\node_modules"
$TranscriptDir = "$Root\data\transcripts"
$SummaryDir = "$Root\data\summaries"

function Write-Status($Level, $Message) {
    $color = "White"
    if ($Level -eq "OK") { $color = "Green" }
    if ($Level -eq "WARN") { $color = "Yellow" }
    if ($Level -eq "FAIL") { $color = "Red" }
    Write-Host ("[{0}] {1}" -f $Level, $Message) -ForegroundColor $color
}

function Get-DirSizeText($Path) {
    if (!(Test-Path -LiteralPath $Path)) {
        return "missing"
    }
    $bytes = (Get-ChildItem -LiteralPath $Path -Recurse -File -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum
    if ($bytes -ge 1GB) {
        return ("{0:N2} GB" -f ($bytes / 1GB))
    }
    return ("{0:N2} MB" -f ($bytes / 1MB))
}

function Read-EnvFile($Path) {
    $values = @{}
    if (!(Test-Path -LiteralPath $Path)) {
        return $values
    }
    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line -and !$line.StartsWith("#") -and $line.Contains("=")) {
            $key, $value = $line.Split("=", 2)
            $values[$key.Trim()] = $value.Trim()
        }
    }
    return $values
}

function Test-PortOpen($Port) {
    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    return $null -ne $connection
}

function Resolve-ToolPath($CommandName, $FallbackPath = "") {
    $tool = Get-Command $CommandName -ErrorAction SilentlyContinue
    if ($tool -and (Test-CommandRuns $tool.Source)) {
        return $tool.Source
    }
    if ($FallbackPath -and (Test-Path -LiteralPath $FallbackPath) -and (Test-CommandRuns $FallbackPath)) {
        return $FallbackPath
    }
    return $null
}

function Test-CommandRuns($Path) {
    try {
        & $Path --version *> $null
        return $LASTEXITCODE -eq 0
    }
    catch {
        return $false
    }
}

Write-Host ""
Write-Host "AI Recorder project check"
Write-Host "Workspace: $Root"
Write-Host ""

if (Test-Path -LiteralPath $BackendPython) {
    $pythonVersion = & $BackendPython --version 2>$null
    Write-Status "OK" "Backend Python is available: $pythonVersion"
}
else {
    Write-Status "FAIL" "Backend Python is missing. Run .\setup.ps1"
}

$NodeExe = Resolve-ToolPath "node" $LocalNodeExe
if ($NodeExe) {
    $nodeVersion = & $NodeExe --version 2>$null
    Write-Status "OK" "Node.js is available: $nodeVersion"
}
else {
    Write-Status "WARN" "Node.js is missing. Install Node.js and make sure node is available in PATH"
}

$NpmCmd = Resolve-ToolPath "npm.cmd" $LocalNpmCmd
if (!$NpmCmd) {
    $NpmCmd = Resolve-ToolPath "npm" $LocalNpmCmd
}
if ($NpmCmd) {
    Write-Status "OK" "npm is available"
}
else {
    Write-Status "WARN" "npm is missing. Install Node.js and make sure npm is available in PATH"
}

$FfmpegExe = Resolve-ToolPath "ffmpeg" $LocalFfmpegExe
if ($FfmpegExe) {
    Write-Status "OK" "ffmpeg is available"
}
else {
    Write-Status "FAIL" "ffmpeg is missing. Install ffmpeg and make sure ffmpeg is available in PATH"
}

if (Test-Path -LiteralPath $FrontendIndex) {
    Write-Status "OK" "Frontend build exists: frontend\dist\index.html"
}
else {
    Write-Status "FAIL" "Frontend build is missing. Run .\setup.ps1"
}

$envValues = Read-EnvFile $BackendEnv
if ($envValues.Count -eq 0) {
    Write-Status "FAIL" "backend\.env is missing or empty. Run .\setup.ps1"
}
else {
    Write-Status "OK" "backend\.env exists"
    $provider = $envValues["LLM_PROVIDER"]
    if (!$provider) { $provider = "deepseek" }
    Write-Status "OK" "Current summary provider: $provider"

    $hasKey = $false
    if ($provider -eq "mimo") {
        $hasKey = [bool]$envValues["MIMO_API_KEY"] -or [bool]$envValues["LLM_API_KEY"]
    }
    else {
        $hasKey = [bool]$envValues["LLM_API_KEY"]
    }

    if ($hasKey) {
        Write-Status "OK" "Summary API key is configured (hidden)"
    }
    else {
        Write-Status "WARN" "Summary API key is not configured; transcription still works"
    }
}

$modelDir = "$Root\models"
if ((Test-Path -LiteralPath $modelDir) -and ((Get-ChildItem -LiteralPath $modelDir -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1))) {
    Write-Status "OK" "Model directory exists. Size: $(Get-DirSizeText $modelDir)"
}
else {
    Write-Status "WARN" "Model directory is empty; first transcription may download models"
}

Write-Status "OK" "Data directory size: $(Get-DirSizeText $DataDir)"
Write-Status "OK" "Transcript directory: $(Get-DirSizeText $TranscriptDir)"
Write-Status "OK" "Summary directory: $(Get-DirSizeText $SummaryDir)"
Write-Status "OK" "Backend dependency size: $(Get-DirSizeText $BackendVenv)"
if (Test-Path -LiteralPath $FrontendNodeModules) {
    Write-Status "OK" "Frontend dependency size: $(Get-DirSizeText $FrontendNodeModules)"
}
else {
    Write-Status "OK" "Frontend dependencies are not included; runtime uses frontend\dist"
}

if (Test-PortOpen 8000) {
    Write-Status "OK" "Backend port 8000 is listening"
}
else {
    Write-Status "WARN" "Backend port 8000 is not listening; service may be stopped"
}

if (Test-PortOpen 5173) {
    Write-Status "OK" "Frontend port 5173 is listening"
}
else {
    Write-Status "WARN" "Frontend port 5173 is not listening; service may be stopped"
}

try {
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:8000/health" -TimeoutSec 3
    Write-Status "OK" "Backend health check passed: status=$($health.status), funasr=$($health.funasr), ffmpeg=$($health.ffmpeg)"
}
catch {
    Write-Status "WARN" "Backend health check is unreachable. Run .\start-all.ps1 when you want to use the app"
}

Write-Host ""
Write-Host "Common commands:"
Write-Host "  .\start-all.ps1   start"
Write-Host "  .\stop-all.ps1    stop"
Write-Host "  .\check.ps1       check"
Write-Host ""
