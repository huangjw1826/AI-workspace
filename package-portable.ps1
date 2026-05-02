param(
    [string]$OutputRoot,
    [string]$PackageName = "AI Recorder Portable",
    [switch]$Zip,
    [switch]$ExcludeData,
    [switch]$ExcludeSecrets
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
if (!$OutputRoot) {
    $OutputRoot = Join-Path $Root "release"
}

$Target = Join-Path $OutputRoot $PackageName
$NodeRoot = Join-Path $Root ".tools\node-v24.14.0-win-x64"
$NpmCmd = Join-Path $NodeRoot "npm.cmd"
$VenvConfig = Join-Path $Root "backend\.venv\pyvenv.cfg"

function Write-Step($Message) {
    Write-Host "[PACK] $Message" -ForegroundColor Cyan
}

function Invoke-Robocopy {
    param(
        [string]$Source,
        [string]$Destination,
        [string[]]$CopyArgs
    )

    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    & robocopy $Source $Destination @CopyArgs
    if ($LASTEXITCODE -gt 7) {
        throw "Robocopy failed with exit code $LASTEXITCODE while copying $Source"
    }
}

function Copy-ProjectFile($RelativePath) {
    $source = Join-Path $Root $RelativePath
    if (!(Test-Path -LiteralPath $source)) {
        return
    }
    $destination = Join-Path $Target $RelativePath
    $destinationDir = Split-Path -Parent $destination
    New-Item -ItemType Directory -Path $destinationDir -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $destination -Force
}

function Get-BasePythonPath {
    if (Test-Path -LiteralPath $VenvConfig) {
        $homeLine = Get-Content -LiteralPath $VenvConfig -Encoding UTF8 |
            Where-Object { $_ -match "^home\s*=" } |
            Select-Object -First 1
        if ($homeLine) {
            $pythonHome = ($homeLine -split "=", 2)[1].Trim()
            $python = Join-Path $pythonHome "python.exe"
            if (Test-Path -LiteralPath $python) {
                return $pythonHome
            }
        }
    }

    $known = "C:\Users\13318\.cache\codex-runtimes\codex-primary-runtime\dependencies\python"
    if (Test-Path -LiteralPath (Join-Path $known "python.exe")) {
        return $known
    }

    return $null
}

Write-Step "Building frontend"
if (Test-Path -LiteralPath $NpmCmd) {
    Push-Location (Join-Path $Root "frontend")
    try {
        & $NpmCmd run build
    }
    finally {
        Pop-Location
    }
}
else {
    Write-Host "[WARN] Bundled npm not found, using existing frontend\dist" -ForegroundColor Yellow
}

Write-Step "Preparing target folder: $Target"
if (Test-Path -LiteralPath $Target) {
    Remove-Item -LiteralPath $Target -Recurse -Force
}
New-Item -ItemType Directory -Path $Target -Force | Out-Null

Write-Step "Copying root launchers and documentation"
@(
    ".gitignore",
    "CHANGELOG.md",
    "README.md",
    "deployment-plan.md",
    "check.ps1",
    "setup.ps1",
    "start-all.ps1",
    "stop-all.ps1",
    "package-portable.ps1",
    "start-ai-recorder.bat",
    "stop-ai-recorder.bat",
    "check-ai-recorder.bat",
    "setup-ai-recorder.bat"
) | ForEach-Object { Copy-ProjectFile $_ }

Write-Step "Copying runtime and app folders"
$commonArgs = @("/E", "/R:1", "/W:1", "/NFL", "/NDL", "/NP", "/XF", "*.pyc", "*.pyo", ".DS_Store")

Invoke-Robocopy (Join-Path $Root ".tools") (Join-Path $Target ".tools") $commonArgs
Invoke-Robocopy (Join-Path $Root "backend") (Join-Path $Target "backend") $commonArgs
Invoke-Robocopy (Join-Path $Root "docs") (Join-Path $Target "docs") $commonArgs
Invoke-Robocopy (Join-Path $Root "models") (Join-Path $Target "models") $commonArgs
Invoke-Robocopy (Join-Path $Root "scripts") (Join-Path $Target "scripts") $commonArgs

Invoke-Robocopy (Join-Path $Root "frontend") (Join-Path $Target "frontend") (
    $commonArgs + @("/XD", "node_modules")
)

if ($ExcludeData) {
    New-Item -ItemType Directory -Path (Join-Path $Target "data") -Force | Out-Null
}
else {
    Invoke-Robocopy (Join-Path $Root "data") (Join-Path $Target "data") $commonArgs
}

New-Item -ItemType Directory -Path (Join-Path $Target "logs") -Force | Out-Null

if ($ExcludeSecrets) {
    $envFile = Join-Path $Target "backend\.env"
    $envExample = Join-Path $Target "backend\.env.example"
    if (Test-Path -LiteralPath $envFile) {
        Remove-Item -LiteralPath $envFile -Force
    }
    if (Test-Path -LiteralPath $envExample) {
        Copy-Item -LiteralPath $envExample -Destination $envFile -Force
    }
}

$basePython = Get-BasePythonPath
if ($basePython) {
    Write-Step "Copying portable Python runtime"
    Invoke-Robocopy $basePython (Join-Path $Target ".tools\python") @("/E", "/R:1", "/W:1", "/NFL", "/NDL", "/NP")
}
else {
    Write-Host "[WARN] Base Python runtime was not found. The portable folder can still use an existing system Python." -ForegroundColor Yellow
}

Write-Step "Writing portable readme"
@"
AI Recorder Portable
====================

How to use:
1. Double-click start-ai-recorder.bat
2. The browser should open automatically at http://127.0.0.1:5173
3. Double-click stop-ai-recorder.bat when you are done

Useful files:
- check-ai-recorder.bat: check service and dependency status
- setup-ai-recorder.bat: repair or reinstall dependencies if startup fails
- backend\.env: local settings and API keys
- data\: database, transcripts, summaries, and local app data
- models\: offline transcription models

Notes:
- This package is intended for personal use on Windows.
- If you copy it to another computer, keep the whole folder together.
- If the target computer blocks scripts, right click the folder, choose properties, and unblock downloaded files if Windows shows that option.
- Do not share backend\.env publicly because it may contain API keys.
"@ | Set-Content -LiteralPath (Join-Path $Target "README-PORTABLE.txt") -Encoding UTF8

if ($Zip) {
    $zipPath = Join-Path $OutputRoot "$PackageName.zip"
    Write-Step "Creating zip: $zipPath"
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    Compress-Archive -Path (Join-Path $Target "*") -DestinationPath $zipPath -Force
}

Write-Host ""
Write-Host "[OK] Portable package is ready:" -ForegroundColor Green
Write-Host "     $Target"
if ($Zip) {
    Write-Host "     $(Join-Path $OutputRoot "$PackageName.zip")"
}
