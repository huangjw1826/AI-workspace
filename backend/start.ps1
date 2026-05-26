$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

function Get-SetupPython {
    $systemPython = Get-Command python -ErrorAction SilentlyContinue
    if ($systemPython) {
        return $systemPython.Source
    }
    throw "Python is missing. Install Python 3.10-3.12 and make sure python is available in PATH."
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
uvicorn app.main:app --host 0.0.0.0 --port 8000
