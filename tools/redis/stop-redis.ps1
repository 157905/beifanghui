$ErrorActionPreference = 'Stop'

$result = (& wsl.exe -d Ubuntu -- bash -lc "~/.local/redis-7.0.15/bin/redis-cli -h 127.0.0.1 -p 6379 shutdown save" 2>$null | Out-String).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Redis 停止失败：$result"
}
Write-Host 'Redis 已保存数据并停止。'
