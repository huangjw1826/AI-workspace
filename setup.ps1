$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$LocalNodeRoot = "$Root\.tools\node-v24.14.0-win-x64"
$LocalNpm = "$LocalNodeRoot\npm.cmd"
$LocalFfmpeg = "$Root\.tools\ffmpeg\ffmpeg.exe"

function Get-SetupPython {
    $systemPython = Get-Command python -ErrorAction SilentlyContinue
    if ($systemPython) {
        return $systemPython.Source
    }
    throw "Python is missing. Install Python 3.10-3.12 and make sure python is available in PATH."
}

function Get-NpmCommand {
    $systemNpm = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if ($systemNpm) {
        return $systemNpm.Source
    }
    $systemNpm = Get-Command npm -ErrorAction SilentlyContinue
    if ($systemNpm) {
        return $systemNpm.Source
    }
    if (Test-Path -LiteralPath $LocalNpm) {
        return $LocalNpm
    }
    throw "npm is missing. Install Node.js and make sure npm is available in PATH."
}

function Test-FfmpegAvailable {
    $systemFfmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if ($systemFfmpeg) {
        return $true
    }
    return Test-Path -LiteralPath $LocalFfmpeg
}

if (!(Test-FfmpegAvailable)) {
    throw "ffmpeg is missing. Install ffmpeg and make sure ffmpeg is available in PATH."
}

Write-Host "Setting up backend..."
Set-Location "$Root\backend"
if (!(Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
}
if (!(Test-Path ".venv")) {
    & (Get-SetupPython) -m venv .venv
}
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip setuptools wheel
pip install -r requirements.txt

Write-Host "Setting up frontend..."
Set-Location "$Root\frontend"
$NpmCmd = Get-NpmCommand
& $NpmCmd install
& $NpmCmd run build
