# backend-java

Java 交易服务（Spring Boot 3，端口 8080）。

## 能力

- 登录签发 JWT（HS256，与 Python 共用 `JWT_SECRET`）
- 权限规则表确定性校验（`/api/perms/check`），不依赖大模型
- 抢票：令牌桶限流 → 幂等 → Redis+Lua 扣库存 → Stream 异步落库
- 工具目录与 REST 单一来源，启动时校验对齐（`ToolRestValidator`）

## 关键接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/login | 登录，返回 JWT |
| GET  | /api/resources | 资源列表 |
| POST | /api/perms/check | 权限校验（Python/Agent 交易前调用） |
| POST | /api/seckill | 抢票/预约 |
| GET  | /api/orders/{requestId} | 订单查询（异步落库后确认） |
| POST | /api/resources/{id}/preheat | 手工预热库存 |

## 运行

前置：Redis + MySQL 已起（见 `../infra/`）。

```bash
mvn spring-boot:run
```

演示账号（密码均 `123456`）：

| 账号 | 部门 | 职级 | 可验证场景 |
|---|---|---|---|
| wangzong | 综合管理部 | 高管 | 可约总裁班车（EXEC_SHUTTLE） |
| zhanggong | 技术部 | 高级工程师 | 约总裁班车被拒（403） |
| lizhu | 人事部 | 实习生 | 同上；可约员工班车 |
