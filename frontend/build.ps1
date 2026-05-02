$ErrorActionPreference = "Stop"

$FrontendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $FrontendRoot
$LocalNodeExe = "$ProjectRoot\.tools\node-v24.14.0-win-x64\node.exe"
$TscBin = "$FrontendRoot\node_modules\typescript\bin\tsc"
$ViteBin = "$FrontendRoot\node_modules\vite\bin\vite.js"

function Test-NodeExe($Path) {
    try {
        & $Path --version *> $null
        return $LASTEXITCODE -eq 0
    }
    catch {
        return $false
    }
}

function Get-NodeExe {
    $systemNode = Get-Command node -ErrorAction SilentlyContinue
    if ($systemNode -and (Test-NodeExe $systemNode.Source)) {
        return $systemNode.Source
    }
    if ((Test-Path -LiteralPath $LocalNodeExe) -and (Test-NodeExe $LocalNodeExe)) {
        return $LocalNodeExe
    }
    throw "Node.js is missing. Install Node.js and make sure node is available in PATH."
}

if (!(Test-Path -LiteralPath $TscBin)) {
    throw "TypeScript is missing. Run npm install first."
}

if (!(Test-Path -LiteralPath $ViteBin)) {
    throw "Vite is missing. Run npm install first."
}

$NodeExe = Get-NodeExe
Push-Location $FrontendRoot
try {
    & $NodeExe $TscBin
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }

    & $NodeExe $ViteBin build
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
