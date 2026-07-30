# Redis 本地开发说明

## 当前安装信息

- Redis 版本：7.0.15
- 运行环境：Windows WSL2 / Ubuntu
- 安装目录：`/home/lenovo/.local/redis-7.0.15`
- 服务地址：`localhost:6379`
- 数据持久化：RDB + AOF
- 网络限制：仅监听本机地址，已开启保护模式

Redis 二进制文件和运行数据位于 WSL 用户目录，不会提交到 GitHub。

## 启动 Redis

在 IDEA Terminal 中进入后端项目目录，执行：

```powershell
.\tools\redis\start-redis.ps1
```

每次 Windows 重启后，在启动后端项目前执行一次。

## 检查 Redis 状态

```powershell
.\tools\redis\status-redis.ps1
```

显示 `PONG` 表示服务正常。

## 停止 Redis

```powershell
.\tools\redis\stop-redis.ps1
```

停止脚本会先保存数据，再关闭 Redis。

## 后端配置

默认使用 Redis 保存登录令牌：

```properties
app.auth.token-store=redis
app.auth.token-ttl=8h
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

令牌以 Redis Hash 保存，键前缀为 `bfh:auth:token:`，并自动设置有效期。后端重启后，未过期令牌仍然有效。

如临时不使用 Redis，可在 IDEA 启动配置中添加环境变量：

```text
AUTH_TOKEN_STORE=memory
```

内存模式仅适合临时调试，后端重启后令牌会全部失效，也不支持多实例共享会话。

## 测试

运行全部单元测试、Redis 集成测试和数据库闭环测试：

```powershell
.\mvnw.cmd clean verify -Pintegration-test
```

Redis 集成测试会验证：

1. 签发令牌并写入 Redis。
2. 另一个服务实例读取相同令牌。
3. 撤销令牌后立即失效。

## 安全说明

- 当前 Redis 仅用于本机开发，未设置访问密码，禁止修改为对局域网或公网监听。
- 正式环境必须配置 Redis ACL、强密码、TLS 或受控私有网络。
- Redis 7.0.15 已是 7.0 系列最后一个公开发布包；正式上线前应评估升级到仍受安全维护的版本。
