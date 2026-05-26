param(
    [string]$BaseUrl = "http://127.0.0.1:8000",
    [string]$Token = ""
)

$ErrorActionPreference = "Stop"
$base = $BaseUrl.TrimEnd("/")
$uri = [Uri]$base
$isLoopback = @("127.0.0.1", "localhost", "::1") -contains $uri.Host.ToLowerInvariant()

function Invoke-CheckRequest {
    param(
        [string]$Path,
        [hashtable]$Headers = @{}
    )

    Invoke-WebRequest -UseBasicParsing -Uri "$base$Path" -Headers $Headers -TimeoutSec 20
}

Write-Host "Checking AI Recorder remote access at $base"

try {
    $health = Invoke-CheckRequest -Path "/health"
    if ($health.StatusCode -ne 200) {
        throw "Health check returned HTTP $($health.StatusCode)"
    }
    Write-Host "[OK] /health is reachable"
}
catch {
    Write-Error "[FAIL] /health is not reachable: $($_.Exception.Message)"
    exit 1
}

if ([string]::IsNullOrWhiteSpace($Token)) {
    Write-Host "[WARN] No token provided; skipping protected API checks."
    exit 0
}

if ($isLoopback) {
    try {
        $headers = @{ "X-API-Token" = $Token }
        $recordings = Invoke-CheckRequest -Path "/api/recordings" -Headers $headers
        if ($recordings.StatusCode -ne 200) {
            throw "Protected API returned HTTP $($recordings.StatusCode)"
        }
        Write-Host "[OK] Local API is reachable; loopback requests intentionally bypass token enforcement."
        exit 0
    }
    catch {
        Write-Error "[FAIL] Local API check failed: $($_.Exception.Message)"
        exit 1
    }
}

$protectedRejected = $false
try {
    Invoke-CheckRequest -Path "/api/recordings" | Out-Null
}
catch {
    $statusCode = $null
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
        $statusCode = $_.Exception.Response.StatusCode.value__
    }
    if ($statusCode -ne 403) {
        Write-Error "[FAIL] /api/recordings without token returned HTTP $statusCode, expected 403"
        exit 1
    }
    $protectedRejected = $true
}
if (-not $protectedRejected) {
    Write-Error "[FAIL] /api/recordings allowed a request without X-API-Token"
    exit 1
}
Write-Host "[OK] Protected API rejects requests without token"

try {
    $headers = @{ "X-API-Token" = $Token }
    $recordings = Invoke-CheckRequest -Path "/api/recordings" -Headers $headers
    if ($recordings.StatusCode -ne 200) {
        throw "Protected API returned HTTP $($recordings.StatusCode)"
    }
    Write-Host "[OK] Protected API accepts the configured token"
}
catch {
    Write-Error "[FAIL] Protected API token check failed: $($_.Exception.Message)"
    exit 1
}

Write-Host "Remote access checks passed."
