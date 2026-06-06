# AI Recorder - Setup Script
# Installs Python/Node dependencies and builds the frontend.

[CmdletBinding()]
param(
    [switch]$SkipFrontend,
    [switch]$SkipBackend,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Backend = Join-Path $Root 'backend'
$Frontend = Join-Path $Root 'frontend'

function Write-Step($msg) {
    Write-Host ''
    Write-Host ('== ' + $msg + ' ==') -ForegroundColor Cyan
}

function Write-Ok($msg) {
    Write-Host ('  [OK] ' + $msg) -ForegroundColor Green
}

function Write-Warn($msg) {
    Write-Host ('  [WARN] ' + $msg) -ForegroundColor Yellow
}

function Write-Err($msg) {
    Write-Host ('  [ERROR] ' + $msg) -ForegroundColor Red
}

# ---------------------------------------------------------------------
Write-Step 'Step 1/5 - Verify prerequisites'
# ---------------------------------------------------------------------

$python = $null
foreach ($candidate in @('python', 'python3', 'py')) {
    $cmd = Get-Command $candidate -ErrorAction SilentlyContinue
    if ($cmd) {
        $python = $candidate
        break
    }
}
if (-not $python) {
    Write-Err 'Python is not installed or not in PATH.'
    Write-Err 'Please install Python 3.10-3.12 from https://www.python.org/downloads/'
    exit 1
}
Write-Ok "Found Python: $python"

$node = Get-Command 'node' -ErrorAction SilentlyContinue
$npm = Get-Command 'npm' -ErrorAction SilentlyContinue
if (-not $node -or -not $npm) {
    Write-Err 'Node.js / npm is not installed or not in PATH.'
    Write-Err 'Please install Node.js 20+ from https://nodejs.org/'
    exit 1
}
Write-Ok "Found Node.js: $($node.Source)"
Write-Ok "Found npm: $($npm.Source)"

# ---------------------------------------------------------------------
Write-Step 'Step 2/5 - Create Python virtual environment'
# ---------------------------------------------------------------------

$venvPath = Join-Path $Backend '.venv'
$venvExe = Join-Path $venvPath 'Scripts\python.exe'

if ((-not $Force) -and (Test-Path -LiteralPath $venvExe)) {
    Write-Ok "Virtual environment already exists at backend\.venv"
} else {
    if (Test-Path -LiteralPath $venvPath) {
        Write-Warn 'Removing existing venv (force mode)...'
        Remove-Item -LiteralPath $venvPath -Recurse -Force
    }
    & $python -m venv $venvPath
    if ($LASTEXITCODE -ne 0) {
        Write-Err 'Failed to create virtual environment.'
        exit 1
    }
    Write-Ok 'Created backend\.venv'
}

# ---------------------------------------------------------------------
Write-Step 'Step 3/5 - Install Python dependencies'
# ---------------------------------------------------------------------

if (-not $SkipBackend) {
    $requirementsFile = Join-Path $Backend 'requirements.txt'
    if (-not (Test-Path -LiteralPath $requirementsFile)) {
        Write-Err "requirements.txt not found at $requirementsFile"
        exit 1
    }
    Push-Location $Backend
    try {
        & $venvExe -m pip install --upgrade pip
        if ($LASTEXITCODE -ne 0) {
            Write-Warn 'Failed to upgrade pip, continuing...'
        }
        Write-Ok 'Upgrading pip... done'

        Write-Host 'Installing dependencies (this may take several minutes)...'
        & $venvExe -m pip install -r $requirementsFile
        if ($LASTEXITCODE -ne 0) {
            Write-Err 'Failed to install Python dependencies.'
            Pop-Location
            exit 1
        }
        Write-Ok 'Python dependencies installed'

        # --- Auto-detect GPU and install matching PyTorch ---
        Write-Host 'Detecting GPU for PyTorch...'
        $torchIndex = $null
        try {
            $nvidiaSmi = Get-Command nvidia-smi -ErrorAction SilentlyContinue
            if ($nvidiaSmi) {
                $gpuName = & nvidia-smi --query-gpu=name --format=csv,noheader 2>$null
                if ($LASTEXITCODE -eq 0 -and $gpuName) {
                    Write-Ok "GPU detected: $($gpuName -join ', ')"
                    # RTX 50xx (Blackwell, CC 12.x) requires CUDA 13.x → nightly build
                    if ($gpuName -match 'RTX 50') {
                        Write-Host 'Blackwell GPU detected — installing PyTorch nightly (CUDA 13.0)...'
                        $torchIndex = 'https://download.pytorch.org/whl/nightly/cu130'
                    } else {
                        Write-Host 'Installing PyTorch stable (CUDA 12.6)...'
                        $torchIndex = 'https://download.pytorch.org/whl/cu126'
                    }
                }
            }
        } catch { }

        if ($torchIndex) {
            & $venvExe -m pip install torch torchaudio --index-url $torchIndex
        } else {
            Write-Ok 'No NVIDIA GPU detected — installing PyTorch CPU version'
            & $venvExe -m pip install torch torchaudio --index-url https://download.pytorch.org/whl/cpu
        }

        if ($LASTEXITCODE -ne 0) {
            Write-Err 'Failed to install PyTorch.'
            Pop-Location
            exit 1
        }
        Write-Ok 'PyTorch installed'
    } finally {
        Pop-Location
    }
} else {
    Write-Warn 'Skipped (per -SkipBackend)'
}

# ---------------------------------------------------------------------
Write-Step 'Step 4/5 - Install Node.js dependencies'
# ---------------------------------------------------------------------

if (-not $SkipFrontend) {
    $packageJson = Join-Path $Frontend 'package.json'
    if (-not (Test-Path -LiteralPath $packageJson)) {
        Write-Err "package.json not found at $packageJson"
        exit 1
    }

    $nodeModules = Join-Path $Frontend 'node_modules'
    if ((-not $Force) -and (Test-Path -LiteralPath $nodeModules)) {
        Write-Ok 'frontend\node_modules already exists'
    } else {
        Push-Location $Frontend
        try {
            Write-Host 'Running npm install (this may take a few minutes)...'
            & npm install
            if ($LASTEXITCODE -ne 0) {
                Write-Err 'Failed to install Node.js dependencies.'
                Pop-Location
                exit 1
            }
            Write-Ok 'Node.js dependencies installed'
        } finally {
            Pop-Location
        }
    }
} else {
    Write-Warn 'Skipped (per -SkipFrontend)'
}

# ---------------------------------------------------------------------
Write-Step 'Step 5/5 - Build frontend'
# ---------------------------------------------------------------------

if (-not $SkipFrontend) {
    $distIndex = Join-Path $Frontend 'dist\index.html'
    if ((-not $Force) -and (Test-Path -LiteralPath $distIndex)) {
        Write-Ok 'frontend\dist already exists (skipping build)'
    } else {
        Push-Location $Frontend
        try {
            & npm run build
            if ($LASTEXITCODE -ne 0) {
                Write-Err 'Frontend build failed.'
                Pop-Location
                exit 1
            }
            Write-Ok 'Frontend built successfully'
        } finally {
            Pop-Location
        }
    }
} else {
    Write-Warn 'Skipped (per -SkipFrontend)'
}

# ---------------------------------------------------------------------
Write-Step 'Setup Complete!'
# ---------------------------------------------------------------------

Write-Host ''
Write-Host '  Next steps:' -ForegroundColor Green
Write-Host '  1. (Optional) Edit backend\.env to configure LLM and storage paths'
Write-Host '  2. Run check.ps1 to verify the environment'
Write-Host '  3. Run start.ps1 to launch the application'
Write-Host '  4. Open http://127.0.0.1:8000 in your browser'
Write-Host ''
Write-Host '  Run with -SkipFrontend or -SkipBackend to skip a section.' -ForegroundColor DarkGray
Write-Host '  Run with -Force to recreate the venv and rebuild from scratch.' -ForegroundColor DarkGray
Write-Host ''
