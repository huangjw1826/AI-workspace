param(
    [string]$HostName = "recorder.weizziwong.top",
    [string]$ExpectedIPv6 = "",
    [string]$Token = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

if ([string]::IsNullOrWhiteSpace($ExpectedIPv6)) {
    $ExpectedIPv6 = (& "$ProjectRoot\scripts\get-public-ipv6.ps1").Trim()
}

if ([string]::IsNullOrWhiteSpace($Token)) {
    $envPath = Join-Path $ProjectRoot "backend\.env"
    if (Test-Path -LiteralPath $envPath) {
        $tokenLine = Get-Content -LiteralPath $envPath -Encoding UTF8 |
            Where-Object { $_ -match '^API_TOKEN=' } |
            Select-Object -First 1
        $Token = $tokenLine -replace '^API_TOKEN=', ''
    }
}

Write-Host "Checking $HostName"
Write-Host "Expected IPv6: $ExpectedIPv6"

function Resolve-AAAA($Name) {
    $resolved = @()
    try {
        $resolved = @(Resolve-DnsName -Name $Name -Type AAAA -ErrorAction Stop |
            Where-Object { $_.IPAddress } |
            Select-Object -ExpandProperty IPAddress)
    }
    catch {
        $resolved = @()
    }

    if ($resolved.Count -gt 0) {
        return $resolved
    }

    $dohUrl = "https://dns.alidns.com/resolve?name=$Name&type=AAAA"
    $response = Invoke-RestMethod -Uri $dohUrl -TimeoutSec 10
    if ($response.Status -ne 0 -or -not $response.Answer) {
        return @()
    }

    return @($response.Answer |
        Where-Object { $_.type -eq 28 -and $_.data } |
        Select-Object -ExpandProperty data)
}

$aaaa = Resolve-AAAA $HostName

if ($aaaa -contains $ExpectedIPv6) {
    Write-Host "[OK] DNS AAAA contains expected IPv6"
}
else {
    Write-Error "[FAIL] DNS AAAA does not contain expected IPv6. Current: $($aaaa -join ', ')"
    exit 1
}

& "$ProjectRoot\scripts\check-remote-access.ps1" -BaseUrl "https://$HostName" -Token $Token
