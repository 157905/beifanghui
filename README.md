# 北方汇生活后端

北重文旅产业服务平台的 REST API 工程。项目服务于微信小程序和统一管理后台，覆盖住宿、餐饮、会议、车辆、场馆、景区、商城及运营管理。

## 当前阶段

已完成需求拆解、总体架构及核心数据模型初稿；正在搭建统一用户、资源、订单、支付和核销底座。

已提供第一批可调用的平台底座接口：统一响应、全局异常、`traceId`、健康检查和仅限本地环境使用的模拟登录。

## 在 IDEA 中启动

1. 使用 IDEA 打开本目录，等待 Maven 加载完成，项目 SDK 选择 JDK 17。
2. 在 MySQL 8 中创建 `beifanghui` 数据库，并确认 Redis 已启动。
3. 打开 `BeifanghuiBackendApplication` 的 Run Configuration，添加环境变量：

```text
DB_URL=jdbc:mysql://localhost:3306/beifanghui?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
DB_USERNAME=root
DB_PASSWORD=你的本地密码
REDIS_HOST=localhost
REDIS_PORT=6379
SPRING_PROFILES_ACTIVE=local
```

4. 运行 `BeifanghuiBackendApplication`。默认端口为 `8080`。

健康检查：

```http
GET http://localhost:8080/api/v1/app/system/health
```

开发阶段模拟登录：

```http
POST http://localhost:8080/api/v1/app/auth/mock-login
Content-Type: application/json

{
  "displayName": "殷子聪",
  "accountType": "USER"
}
```

`accountType` 可使用 `USER`、`ADMIN`、`OPS`，用于后续测试三端权限。返回的 `mock_` 令牌当前仅用于联调占位，下一步将由正式认证过滤器读取，并最终替换为微信登录和后台账号登录。接入正式认证后必须删除或关闭模拟登录接口。

模拟登录后，将返回的 `accessToken` 放入请求头：

```http
Authorization: Bearer mock_xxxxxxxxxxxxxxxx
```

三端身份验证接口：

```text
GET /api/v1/app/access/me    仅允许 USER
GET /api/v1/admin/access/me  仅允许 ADMIN
GET /api/v1/ops/access/me    仅允许 OPS
```

缺少或无效令牌返回 `AUTH_401_001`；账号跨端访问返回 `AUTH_403_001`。可直接使用 `http/local-api.http` 中第 6—9 个请求验证。

## 资源与库存查询

第一版真实数据库查询接口：

```text
GET /api/v1/app/resources
GET /api/v1/app/resources/{resourceId}
GET /api/v1/app/resource-skus/{skuId}/availability?date=2026-07-30&timeSlot=
```

接口均需要USER令牌。Flyway V4会加入北方宾馆标准间和中型会议室的第一批联调数据。先从资源列表复制真实 `resourceId`，再从资源详情复制真实 `skuId` 查询库存，不要假定ID一定为1。

注意：`resourceId` 是资源列表响应 `data.items[].id` 中的数字；`skuId` 是资源详情响应 `data.skus[].id` 中的数字。`traceId` 仅用于日志追踪，不能作为资源或SKU标识。

Flyway V5会额外写入5组完整联调数据：商务大床房、小型会议室、五座商务轿车、羽毛球场和景区成人票；每组均包含资源、SKU和2026-07-30库存。

## 订单闭环

```text
POST /api/v1/app/orders
GET  /api/v1/app/orders
GET  /api/v1/app/orders/{orderId}
POST /api/v1/app/orders/{orderId}/cancel
```

创建订单必须携带 `Idempotency-Key`。服务端在事务中锁定库存、以后端库存价格计算金额、扣减库存、记录库存流水，并创建15分钟有效的 `PENDING_PAYMENT` 订单。重复幂等键返回第一次订单；取消待支付订单会恢复库存。IDEA调用示例见 `http/local-api.http` 第14—17项。

订单类型由服务端根据SKU关联资源的 `resource_type` 推导，不信任客户端传入值；同一订单全部明细类型一致时使用该类型，跨类型订单使用 `MIXED`。创建新订单必须使用新的 `Idempotency-Key`，旧键永远返回原订单，包括已取消的原订单。

详细设计见 [docs](docs/)。数据库初始化脚本见 [V1__init_core_schema.sql](src/main/resources/db/migration/V1__init_core_schema.sql)。

## 本地启动前提

- JDK 17
- MySQL 8.0+
- Redis 7+

在启动前通过环境变量配置数据库连接，示例：

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/beifanghui?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='请替换为本地密码'
./mvnw.cmd spring-boot:run
```

> 生产环境不得将数据库、微信支付或短信等密钥提交到仓库。

## 模拟支付

```text
POST /api/v1/app/orders/{orderId}/mock-pay
```

仅允许支付本人未过期的 `PENDING_PAYMENT` 订单。接口写入 `bf_payment` 成功流水、更新订单为 `PAID` 并记录审计日志；同一订单重复调用返回原支付结果。IDEA示例见 `http/local-api.http` 第18项。正式接入微信支付V3后必须删除或关闭模拟支付接口。
