# AI Recorder — 停止服务
# 停止端口 8000 上的后端进程

Write-Host "AI Recorder — 停止服务" -ForegroundColor Cyan

# 查找占用端口 8000 的进程
$found = $false
$connections = netstat -ano | Select-String ":8000 " | Select-String "LISTENING"
foreach ($line in $connections) {
    $parts = $line -split '\s+'
    $targetPid = $parts[-1]
    if ($targetPid) {
        Stop-Process -Id $targetPid -Force -ErrorAction SilentlyContinue
        Write-Host "       已停止 PID $targetPid"
        $found = $true
    }
}

if (-not $found) {
    Write-Host "       没有进程在端口 8000 运行"
}

Write-Host "完成" -ForegroundColor Green
