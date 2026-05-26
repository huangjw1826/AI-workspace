param(
    [string]$TaskName = "AI Recorder Aliyun IPv6 DDNS",
    [string]$DomainName = "weizziwong.top",
    [string]$RR = "recorder",
    [int]$IntervalMinutes = 10
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$ScriptPath = Join-Path $ProjectRoot "scripts\update-aliyun-ddns.ps1"

if (!(Get-Command aliyun -ErrorAction SilentlyContinue)) {
    throw "Aliyun CLI is missing. Install and configure it before registering the scheduled task."
}

$argument = "-NoProfile -ExecutionPolicy Bypass -File `"$ScriptPath`" -DomainName `"$DomainName`" -RR `"$RR`""
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $argument -WorkingDirectory $ProjectRoot
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes $IntervalMinutes)
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Force | Out-Null
Write-Host "[OK] Registered scheduled task: $TaskName"
Write-Host "Runs every $IntervalMinutes minutes and updates $RR.$DomainName"
