$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $Root
$PortablePython = "$ProjectRoot\.tools\python\python.exe"
$CodexPython = "C:\Users\13318\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
Set-Location $Root

function Get-SetupPython {
    if (Test-Path -LiteralPath $PortablePython) {
        return $PortablePython
    }
    if (Test-Path -LiteralPath $CodexPython) {
        return $CodexPython
    }
    return "python"
}

if (!(Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
}

if (!(Test-Path ".venv")) {
    & (Get-SetupPython) -m venv .venv
}

.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip setuptools wheel
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000
