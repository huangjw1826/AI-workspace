param(
    [string]$RulePrefix = "AI Recorder Caddy"
)

$ErrorActionPreference = "Stop"

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Please run this script as Administrator."
}

$rules = @(
    @{ Name = "$RulePrefix HTTP"; Port = 80 },
    @{ Name = "$RulePrefix HTTPS"; Port = 443 }
)

foreach ($rule in $rules) {
    $existing = Get-NetFirewallRule -DisplayName $rule.Name -ErrorAction SilentlyContinue
    if ($existing) {
        Set-NetFirewallRule -DisplayName $rule.Name -Enabled True -Direction Inbound -Action Allow -Profile Any
        Set-NetFirewallPortFilter -AssociatedNetFirewallRule $existing -Protocol TCP -LocalPort $rule.Port
        Write-Host "[OK] Updated firewall rule: $($rule.Name)"
        continue
    }

    New-NetFirewallRule `
        -DisplayName $rule.Name `
        -Direction Inbound `
        -Action Allow `
        -Protocol TCP `
        -LocalPort $rule.Port `
        -Profile Any | Out-Null

    Write-Host "[OK] Created firewall rule: $($rule.Name)"
}

Write-Host "[OK] Windows firewall now allows inbound TCP 80 and 443 for AI Recorder."
