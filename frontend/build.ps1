$ErrorActionPreference = "Stop"

$FrontendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $FrontendRoot
$NodeExe = "$ProjectRoot\.tools\node-v24.14.0-win-x64\node.exe"
$TscBin = "$FrontendRoot\node_modules\typescript\bin\tsc"
$ViteBin = "$FrontendRoot\node_modules\vite\bin\vite.js"

if (!(Test-Path -LiteralPath $NodeExe)) {
    throw "Bundled Node.js is missing: $NodeExe"
}

if (!(Test-Path -LiteralPath $TscBin)) {
    throw "TypeScript is missing. Run npm install first."
}

if (!(Test-Path -LiteralPath $ViteBin)) {
    throw "Vite is missing. Run npm install first."
}

& $NodeExe $TscBin
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& $NodeExe $ViteBin build
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
