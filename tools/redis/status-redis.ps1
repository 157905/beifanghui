$result = (& wsl.exe -d Ubuntu -- bash -lc "~/.local/redis-7.0.15/bin/redis-cli -h 127.0.0.1 -p 6379 ping" 2>$null | Out-String).Trim()
if ($result -eq 'PONG') {
    Write-Host 'Redis 运行正常：PONG'
    exit 0
}
Write-Host "Redis 未运行：$result"
exit 1
