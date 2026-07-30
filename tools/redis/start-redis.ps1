$ErrorActionPreference = 'Stop'

$configWindows = (Resolve-Path (Join-Path $PSScriptRoot 'redis-7.0.conf')).Path
$drive = $configWindows.Substring(0, 1).ToLowerInvariant()
$pathWithoutDrive = $configWindows.Substring(2).Replace('\', '/')
$configLinux = "/mnt/$drive$pathWithoutDrive"

wsl -d Ubuntu -- bash -lc "mkdir -p ~/.local/redis-7.0.15/data ~/.local/redis-7.0.15/logs"
wsl -d Ubuntu -- bash -lc "~/.local/redis-7.0.15/bin/redis-server '$configLinux' --daemonize yes --dir ~/.local/redis-7.0.15/data --pidfile ~/.local/redis-7.0.15/redis.pid --logfile ~/.local/redis-7.0.15/logs/redis.log"
$pong = (& wsl.exe -d Ubuntu -- bash -lc "~/.local/redis-7.0.15/bin/redis-cli -h 127.0.0.1 -p 6379 ping" 2>$null | Out-String).Trim()
if ($pong -ne 'PONG') {
    throw "Redis 启动失败，返回结果：$pong"
}
Write-Host 'Redis 7.0.15 已启动，地址：localhost:6379'
