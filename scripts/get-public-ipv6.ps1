param(
    [switch]$All
)

$ErrorActionPreference = "Stop"

function Test-GlobalIPv6($Address) {
    return $Address -match '^[23][0-9a-fA-F]{3}:'
}

$addresses = Get-NetIPAddress -AddressFamily IPv6 -ErrorAction SilentlyContinue |
    Where-Object {
        $_.AddressState -eq "Preferred" -and
        (Test-GlobalIPv6 $_.IPAddress) -and
        $_.IPAddress -notmatch '^fe80:' -and
        $_.IPAddress -notmatch '^fd'
    } |
    Sort-Object `
        @{ Expression = { if ($_.SuffixOrigin -eq "Dhcp") { 0 } elseif ($_.SuffixOrigin -eq "Link") { 1 } elseif ($_.SuffixOrigin -eq "Manual") { 2 } else { 3 } } },
        @{ Expression = { if ($_.PrefixOrigin -eq "Dhcp") { 0 } elseif ($_.PrefixOrigin -eq "RouterAdvertisement") { 1 } else { 2 } } },
        IPAddress

if ($All) {
    $addresses | Select-Object IPAddress, InterfaceAlias, PrefixOrigin, SuffixOrigin, AddressState
    exit 0
}

$selected = $addresses | Select-Object -First 1
if (-not $selected) {
    Write-Error "No preferred global IPv6 address was found."
    exit 1
}

Write-Output $selected.IPAddress
