# 第二轮迭代：接口、状态与验收契约

## 1. 文档目的

本文档定义第二轮中前端、后端、Account 数据库、状态历史及网络模拟之间的契约，用于开发、联调和验收。

## 2. 创建付款表单契约

创建接口保持：

```http
POST /api/payments
Content-Type: application/json
Idempotency-Key: <unique-key>
```

请求体保持：

```json
{
  "sourceAccount": "ACC001",
  "destinationAccount": "ACC002",
  "amount": 1000.00,
  "currency": "CNY",
  "reference": "Invoice Payment"
}
```

客户端应即时检查：

| 字段 | 规则 |
|---|---|
| sourceAccount | 必填，最多 50 字符 |
| destinationAccount | 必填，最多 50 字符，不能与 sourceAccount 相同 |
| amount | 必填，0.01–1,000,000.00，最多两位小数 |
| currency | 必填，USD/EUR/GBP/CNY |
| reference | 可空，最多 255 字符 |

后端必须重复执行权威校验，不能信任浏览器提交。

## 3. Account 数据契约

示例账户数据：

```json
{
  "id": "b8150f00-2b3e-4d36-b827-2fd08f501001",
  "accountNumber": "ACC001",
  "accountName": "Midnight Builders Operating Account",
  "accountType": "BUSINESS",
  "currency": "CNY",
  "status": "ACTIVE",
  "version": 0
}
```

账户合法性查找契约：

```text
lookup key = normalized payment.sourceAccount
found      = Account 校验通过（第二轮最低要求）
not found  = ACCOUNT_NOT_FOUND
```

目标账户不要求存在于 accounts 表，因为它可以代表外部收款方。不要误把 source 和 destination 都设为本地外键。

本轮无需开放 Account CRUD HTTP API；账户可由 Flyway 演示数据或数据库管理工具维护。若后续需要账户管理，应单独设计 `/api/accounts` 权限与审计。

## 4. 状态转换契约

| 当前状态 | 成功下一状态 | 失败下一状态 | 判断内容 |
|---|---|---|---|
| CREATED | VALIDATED | FAILED | Payment 业务字段是否合法 |
| VALIDATED | SENT | FAILED | sourceAccount 是否存在 |
| SENT | COMPLETED | FAILED | 网络尝试是否在阈值内成功 |
| COMPLETED | 无 | 无 | 终态 |
| FAILED | 无 | 无 | 终态 |

三类 FAILED 必须通过 `fromStatus`、`errorCode` 和 notes 区分，不能只返回统一的 `PROCESSING_ERROR`。

### 4.1 CREATED → FAILED 示例

```json
{
  "paymentId": "payment-001",
  "previousStatus": "CREATED",
  "currentStatus": "FAILED",
  "message": "Payment validation failed",
  "errorCode": "INVALID_AMOUNT",
  "errorMessage": "amount must be greater than 0"
}
```

说明：如果请求因缺字段或 JSON 格式错误在 Controller 边界直接返回 400，则没有 Payment，也没有状态历史。这不属于状态转换。

### 4.2 VALIDATED → FAILED 示例

```json
{
  "paymentId": "payment-002",
  "previousStatus": "CREATED",
  "currentStatus": "FAILED",
  "message": "Payer account validation failed",
  "errorCode": "ACCOUNT_NOT_FOUND",
  "errorMessage": "Source account does not exist"
}
```

对应历史至少包含：

```text
NULL      -> CREATED
CREATED   -> VALIDATED
VALIDATED -> FAILED (ACCOUNT_NOT_FOUND)
```

### 4.3 SENT → FAILED 示例

```json
{
  "paymentId": "payment-003",
  "previousStatus": "CREATED",
  "currentStatus": "FAILED",
  "message": "Payment confirmation timed out after retries",
  "errorCode": "NETWORK_TIMEOUT",
  "errorMessage": "Network delay exceeded 10 seconds on 4 attempts",
  "attemptCount": 4
}
```

对应历史至少包含：

```text
NULL      -> CREATED
CREATED   -> VALIDATED
VALIDATED -> SENT
SENT      -> FAILED (NETWORK_TIMEOUT)
```

## 5. 网络模拟契约

| 项目 | 契约 |
|---|---|
| 随机范围 | 整数 0–20，含 0 和 20 |
| 成功阈值 | 0–10 秒，10 秒合法 |
| 超时范围 | 11–20 秒 |
| 重试次数 | 首次超时后最多重试 3 次 |
| 最大尝试数 | 4 |
| 每次随机值 | 独立重新生成 |
| 成功行为 | 立即停止重试，SENT → COMPLETED |
| 耗尽行为 | SENT → FAILED，NETWORK_TIMEOUT |

建议日志字段：`paymentId`、`attemptNumber`、`simulatedDelaySeconds`、`timeoutSeconds`、`outcome`。账号和幂等键只能脱敏记录。

## 6. 处理 API 响应兼容性

现有接口保持：

```http
POST /api/payments/{id}/process
```

为展示重试过程，建议向 `ProcessPaymentResponse` 追加可选字段：

| 字段 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| attemptCount | Integer | 否 | 实际网络尝试次数 |
| simulatedDelays | Integer[] | 否 | 每次生成的模拟延迟，仅演示环境返回 |
| failureStage | String | 否 | VALIDATION、ACCOUNT 或 NETWORK |

新增字段对现有前端属于向后兼容。生产环境不建议暴露全部内部重试细节，可只返回 attemptCount。

成功示例：

```json
{
  "paymentId": "payment-004",
  "previousStatus": "CREATED",
  "currentStatus": "COMPLETED",
  "message": "Payment processed successfully",
  "errorCode": null,
  "errorMessage": null,
  "attemptCount": 2,
  "simulatedDelays": [15, 7],
  "failureStage": null
}
```

## 7. HTTP 错误与业务失败的区别

| 场景 | HTTP 建议 | 是否产生 Payment FAILED |
|---|---|---|
| JSON 无法解析、缺必填字段 | 400 | 否 |
| Payment 不存在 | 404 | 否 |
| Payment 已是终态又处理 | 409 或 400 | 否，不改变原状态 |
| 业务字段处理失败 | 200（返回处理结果） | 是，CREATED → FAILED |
| Account 不存在 | 200（返回处理结果） | 是，VALIDATED → FAILED |
| 网络重试耗尽 | 200（模拟业务结果） | 是，SENT → FAILED |
| 系统数据库故障 | 500/503 | 不应伪装为业务 FAILED |

如果团队希望处理失败返回 4xx/5xx，也可以调整，但三类失败必须采用一致策略，并确保前端仍能读取 Payment 最终状态。

## 8. 前端联调要求

- 创建表单继续即时显示字段错误；
- “处理付款”按钮调用一次 process API，不由前端自行循环重试；
- 等待期间按钮禁用并显示处理中；
- 后端可能等待约 10 秒以上，前端 fetch 超时必须大于后端最大单次超时；
- 页面根据 errorCode 显示三类不同文案；
- 状态历史展示真实失败前状态；
- 终态隐藏或禁用再次处理动作。

注意：若四次尝试都真实等待 10 秒，同步 HTTP 请求可能接近 40 秒。更稳健的后续方案是异步处理并轮询状态；第二轮演示可先保留同步实现，但需明确客户端和网关超时。

## 9. 验收场景矩阵

| 编号 | 输入/固定随机序列 | 预期历史终点 | 错误码/结果 |
|---|---|---|---|
| I2-01 | 合法字段、Account 存在、`[0]` | SENT → COMPLETED | 成功，1 次 |
| I2-02 | 合法字段、Account 存在、`[10]` | SENT → COMPLETED | 成功，边界 10 秒 |
| I2-03 | 合法字段、Account 不存在 | VALIDATED → FAILED | ACCOUNT_NOT_FOUND |
| I2-04 | 非法业务字段 | CREATED → FAILED | 对应 INVALID_* |
| I2-05 | Account 存在、`[11, 8]` | SENT → COMPLETED | 第 2 次成功 |
| I2-06 | Account 存在、`[20, 15, 12, 11]` | SENT → FAILED | NETWORK_TIMEOUT，4 次 |
| I2-07 | 已 FAILED 再次处理 | 状态不变 | INVALID_STATUS_TRANSITION |
| I2-08 | 相同幂等键和相同请求 | 不新增 CREATED 历史 | 返回原 Payment |

## 10. 完成定义

第二轮只有同时满足以下条件才算完成：

- Account 表由 Flyway 创建且 JPA schema validate 通过；
- Payment 付款方能由 Account 数据库记录校验；
- 三种 FAILED 的前驱状态和错误码准确；
- 0–20 随机延迟、10 秒边界和三次重试均有确定性测试；
- 网络等待不包在长数据库事务中；
- 前端能区分并展示三类失败；
- 所有现有测试和第二轮新增测试通过；
- API、数据库和状态流程文档同步更新。

