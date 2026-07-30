# Redis 接口限流说明

## 作用

接口限流用于防止短时间内大量重复请求占用数据库、库存、支付和核销资源。当前使用 Redis Lua 脚本原子完成计数与过期时间设置，多后端实例共享同一套限流数据。

## 当前规则

所有规则默认使用一分钟窗口：

| 接口 | 限制 | 计数对象 |
| --- | ---: | --- |
| 模拟登录 | 10 次/分钟 | 客户端 IP |
| 创建订单 | 20 次/分钟 | 登录用户 |
| 模拟支付 | 10 次/分钟 | 登录用户 |
| 模拟退款 | 10 次/分钟 | 登录用户 |
| 管理端/运维端核销 | 30 次/分钟 | 操作人员 |

普通查询接口当前不限制，避免影响正常浏览和联调。

## 响应头

受保护接口会返回：

- `X-RateLimit-Limit`：当前窗口允许的最大请求数。
- `X-RateLimit-Remaining`：当前窗口剩余次数。
- `X-RateLimit-Reset`：距离窗口重置的秒数。
- `Retry-After`：触发限流后建议等待的秒数。

超过限制时返回 HTTP `429`：

```json
{
  "code": "SYSTEM_429_001",
  "message": "请求过于频繁，请在 30 秒后重试",
  "data": null,
  "traceId": "请求追踪编号"
}
```

## 配置

默认配置位于 `application.properties`，部署时可以通过环境变量调整：

```text
RATE_LIMIT_ENABLED=true
RATE_LIMIT_WINDOW=1m
RATE_LIMIT_LOGIN=10
RATE_LIMIT_CREATE_ORDER=20
RATE_LIMIT_PAYMENT=10
RATE_LIMIT_REFUND=10
RATE_LIMIT_VERIFICATION=30
```

如需临时关闭限流：

```text
RATE_LIMIT_ENABLED=false
```

正式环境不建议关闭。

## IDEA 测试

1. 保持 Redis 运行。
2. 在 IDEA 中打开 `RedisRateLimitInterceptorIT.java`。
3. 点击测试类左侧绿色按钮运行。
4. 测试显示绿色表示 Redis 原子计数、过期时间和 429 错误均正常。

运行包括数据库、Redis 会话和限流在内的全部测试：

```powershell
.\mvnw.cmd clean verify -Pintegration-test
```

## Redis 键

限流键使用 `bfh:rate:` 前缀并自动过期，不需要手工清理。例如：

```text
bfh:rate:login:127_0_0_1
bfh:rate:create-order:mock-user-id
```

正式部署经过反向代理时，需要统一配置可信代理和真实客户端 IP 传递规则；当前本地开发直接使用连接来源 IP。
