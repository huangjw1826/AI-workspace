param(
    [string]$DomainName = "weizziwong.top",
    [string]$RR = "recorder",
    [string]$IPv6 = "",
    [int]$TTL = 600
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

function Get-AliyunCli {
    $cmd = Get-Command aliyun -ErrorAction SilentlyContinue
    if (-not $cmd) {
        throw "Aliyun CLI is missing. Install and configure it first: aliyun configure"
    }
    return $cmd.Source
}

function Get-RecordArray($Response) {
    if (-not $Response.DomainRecords) {
        return @()
    }
    $records = $Response.DomainRecords.Record
    if ($null -eq $records) {
        return @()
    }
    if ($records -is [array]) {
        return $records
    }
    return @($records)
}

if ([string]::IsNullOrWhiteSpace($IPv6)) {
    $IPv6 = (& "$ProjectRoot\scripts\get-public-ipv6.ps1").Trim()
}

if ($IPv6 -notmatch '^[23][0-9a-fA-F]{3}:') {
    throw "The selected address does not look like a public IPv6 address: $IPv6"
}

$aliyun = Get-AliyunCli
$fqdn = "$RR.$DomainName"

Write-Host "Updating AAAA record for $fqdn -> $IPv6"

$describeRaw = & $aliyun alidns DescribeDomainRecords --DomainName $DomainName --RRKeyWord $RR --Type AAAA
if ($LASTEXITCODE -ne 0) {
    throw "Failed to query Aliyun DNS records."
}
$describe = $describeRaw | ConvertFrom-Json
$record = Get-RecordArray $describe |
    Where-Object { $_.RR -eq $RR -and $_.Type -eq "AAAA" } |
    Select-Object -First 1

if ($record) {
    if ($record.Value -eq $IPv6) {
        Write-Host "[OK] DNS record already points to $IPv6"
        exit 0
    }

    & $aliyun alidns UpdateDomainRecord `
        --RecordId $record.RecordId `
        --RR $RR `
        --Type AAAA `
        --Value $IPv6 `
        --TTL $TTL | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to update Aliyun DNS record."
    }
    Write-Host "[OK] Updated existing AAAA record."
}
else {
    & $aliyun alidns AddDomainRecord `
        --DomainName $DomainName `
        --RR $RR `
        --Type AAAA `
        --Value $IPv6 `
        --TTL $TTL | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to add Aliyun DNS record."
    }
    Write-Host "[OK] Added new AAAA record."
}
