$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$NodeRoot = "$Root\.tools\node-v24.14.0-win-x64"
$PortablePython = "$Root\.tools\python\python.exe"
$CodexPython = "C:\Users\13318\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
$env:Path = "$NodeRoot;$env:Path"

function Get-SetupPython {
    if (Test-Path -LiteralPath $PortablePython) {
        return $PortablePython
    }
    if (Test-Path -LiteralPath $CodexPython) {
        return $CodexPython
    }
    return "python"
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
& "$NodeRoot\npm.cmd" install
& "$NodeRoot\npm.cmd" run build
