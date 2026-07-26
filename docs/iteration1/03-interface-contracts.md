# 第一轮迭代：前端、后端与数据库接口契约

## 1. 文档目的

本文档定义第一轮迭代中前端与后端、后端内部模块以及后端与数据库之间的接口契约。各成员应以本文档为联调基准，字段或行为变更必须同步更新文档并通知相关成员。

本文档不包含具体实现代码。

## 2. 统一约定

### 2.1 基础地址与数据格式

- API Base URL：`/api/payments`
- 请求和响应格式：`application/json`
- 时间格式：ISO 8601 UTC，例如 `2026-07-25T10:00:00Z`
- 金额格式：JSON Number，数据库使用 `DECIMAL(19,2)`，禁止浮点数据库类型
- 币种格式：三位大写代码，例如 `GBP`、`USD`、`EUR`、`CNY`
- Payment ID：后端生成的 UUID 字符串
- 状态值：`CREATED`、`VALIDATED`、`SENT`、`COMPLETED`、`FAILED`

### 2.2 通用 HTTP 状态码

| 状态码 | 使用场景 |
|---:|---|
| 200 | 查询成功、处理成功、相同幂等请求重放 |
| 201 | 首次创建付款成功 |
| 400 | 参数或业务校验失败、非法状态转换 |
| 404 | Payment ID 不存在 |
| 409 | 同一幂等键对应不同请求内容 |
| 500 | 未预期的内部处理错误 |
| 503 | 可选：模拟或未来网络故障 |

### 2.3 统一错误响应

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `timestamp` | String | 是 | ISO 8601 错误时间 |
| `status` | Integer | 是 | HTTP 状态码 |
| `errorCode` | String | 是 | 稳定业务错误码 |
| `message` | String | 是 | 面向调用方的可读说明 |
| `path` | String | 是 | 发生错误的请求路径 |

示例：

```json
{
  "timestamp": "2026-07-25T10:00:00Z",
  "status": 400,
  "errorCode": "INVALID_AMOUNT",
  "message": "Payment amount must be greater than zero",
  "path": "/api/payments"
}
```

## 3. 前端调用后端的 REST API

### 3.1 创建付款

#### 请求

- Method：`POST`
- Path：`/api/payments`
- Header：`Idempotency-Key`，必填，最大 100 字符；前端每次新的用户提交生成新值，网络重试必须复用原值。

请求字段：

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `sourceAccount` | String | 是 | 最大 50 字符，符合首轮账户格式 |
| `destinationAccount` | String | 是 | 最大 50 字符，不得与来源账户相同 |
| `amount` | Decimal | 是 | 大于 0，不超过 1,000,000，最多两位小数 |
| `currency` | String | 是 | 三位币种代码且属于支持集合 |
| `reference` | String | 否 | 最大 255 字符 |

请求示例：

```json
{
  "sourceAccount": "ACC001",
  "destinationAccount": "ACC002",
  "amount": 1000.00,
  "currency": "CNY",
  "reference": "Invoice Payment"
}
```

#### 成功响应

- 首次创建：`201 Created`
- 相同幂等键和相同请求重放：`200 OK`
- `Location` Header：首次创建时建议返回 `/api/payments/{id}`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | String | Payment ID |
| `sourceAccount` | String | 来源账户 |
| `destinationAccount` | String | 目标账户 |
| `amount` | Decimal | 金额 |
| `currency` | String | 币种 |
| `reference` | String/null | 备注 |
| `status` | String | 固定为 `CREATED` |
| `errorCode` | null | 创建成功时为空 |
| `errorMessage` | null | 创建成功时为空 |
| `createdAt` | String | 创建时间 |
| `updatedAt` | String | 最后更新时间 |

#### 错误响应

- `400 INVALID_AMOUNT`
- `400 INVALID_CURRENCY`
- `400 INVALID_ACCOUNT`
- `400 SAME_SOURCE_AND_DESTINATION`
- `400 VALIDATION_FAILED`
- `409 DUPLICATE_PAYMENT`

### 3.2 查询付款详情

- Method：`GET`
- Path：`/api/payments/{id}`
- Path 参数 `id`：必填，Payment ID。
- 成功：`200 OK`，返回完整 Payment Response。
- 失败：`404 PAYMENT_NOT_FOUND`。

失败付款必须同时返回 `status = FAILED`、`errorCode` 和 `errorMessage`，前端据此展示失败详情。

### 3.3 查询付款列表

- Method：`GET`
- Path：`/api/payments`
- 成功：`200 OK`
- 默认排序：建议按 `createdAt` 倒序，最新付款在前。

列表项字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | String | Payment ID |
| `amount` | Decimal | 金额 |
| `currency` | String | 币种 |
| `status` | String | 当前状态 |
| `createdAt` | String | 创建时间 |
| `errorCode` | String/null | 失败时显示 |

第一轮数据量较小时可以返回数组；若预计数据增长，第二轮再加入分页参数和分页响应。

### 3.4 按状态筛选付款

- Method：`GET`
- Path：`/api/payments?status={status}`
- Query 参数：`status`，可取五种付款状态。
- 成功：`200 OK`，只返回匹配状态的列表；没有匹配项时返回空数组。
- 失败：非法状态返回 `400 VALIDATION_FAILED`。

### 3.5 处理付款

- Method：`POST`
- Path：`/api/payments/{id}/process`
- Request Body：无。
- 前置条件：付款当前状态必须为 `CREATED`。

后端内部按顺序执行校验、发送和确认。成功后最终状态为 `COMPLETED`；任何阶段失败后最终状态为 `FAILED`。

成功或业务处理失败的响应字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | String | Payment ID |
| `previousStatus` | String | 调用前状态，正常为 `CREATED` |
| `currentStatus` | String | `COMPLETED` 或 `FAILED` |
| `message` | String | 处理结果说明 |
| `errorCode` | String/null | 失败错误码 |
| `errorMessage` | String/null | 失败原因 |

接口级错误：

- `404 PAYMENT_NOT_FOUND`
- `400 INVALID_STATUS_TRANSITION`
- `500 PROCESSING_ERROR`，仅用于无法安全转化为付款失败结果的系统异常

前端提交后应禁用处理按钮，等待响应，再刷新详情与历史，避免用户重复点击。

### 3.6 查询状态历史

- Method：`GET`
- Path：`/api/payments/{id}/history`
- 成功：`200 OK`，按 `changedAt` 正序返回。
- 失败：`404 PAYMENT_NOT_FOUND`。

历史字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `fromStatus` | String/null | 创建记录为空 |
| `toStatus` | String | 进入的状态 |
| `triggeredBy` | String | `USER` 或 `SYSTEM` |
| `errorCode` | String/null | 失败转换时填写 |
| `notes` | String/null | 状态变化说明 |
| `changedAt` | String | 变化时间 |

## 4. 前端页面与 API 对应关系

| 前端功能 | 页面动作 | 调用接口 | 成功后的前端行为 |
|---|---|---|---|
| 创建付款 | 提交表单 | `POST /api/payments` | 跳转详情页或展示 Payment ID |
| 查看列表 | 进入列表页 | `GET /api/payments` | 渲染付款列表 |
| 状态筛选 | 修改筛选器 | `GET /api/payments?status=...` | 替换当前列表 |
| 查看详情 | 点击付款 | `GET /api/payments/{id}` | 显示完整字段和失败原因 |
| 处理付款 | 点击 Process | `POST /api/payments/{id}/process` | 刷新详情和历史 |
| 查看历史 | 打开历史区域 | `GET /api/payments/{id}/history` | 按时间轴展示状态变化 |

## 5. 后端内部服务接口

### 5.1 Controller → PaymentService

| 方法 | 输入 | 输出 | 说明 |
|---|---|---|---|
| `createPayment` | CreatePaymentRequest、Idempotency Key | Payment Response 与是否首次创建标志 | 决定 201 或 200 |
| `getPayment` | Payment ID | Payment Response | 查询详情 |
| `listPayments` | 可选状态 | Payment List Item 列表 | 全部或筛选 |
| `processPayment` | Payment ID | Process Payment Response | 完整处理用例 |
| `getPaymentHistory` | Payment ID | History Response 列表 | 查询审计轨迹 |

### 5.2 PaymentService → 子服务

| 子服务 | 输入 | 输出/效果 |
|---|---|---|
| Validation Service | Request 或 Payment | 成功无返回；失败抛业务异常 |
| Idempotency Service | Key 与规范化请求 | 新建、重放或冲突决定 |
| Lifecycle Service | Payment 与目标动作 | 更新状态并记录历史 |
| Processing Simulator | Payment | 成功或失败结果 |
| History Service | Payment ID 或转换信息 | 保存/查询历史 |
| Mapper | Entity 或 DTO | 对应目标对象 |

### 5.3 Service → Repository

| Repository 方法 | 输入 | 输出 |
|---|---|---|
| Payment `save` | Payment Entity | 持久化后的 Payment |
| Payment `findById` | Payment ID | Optional Payment |
| Payment `findByIdempotencyKey` | Idempotency Key | Optional Payment |
| Payment `findAll...` | 无或状态 | Payment 列表 |
| History `save` | History Entity | 持久化后的 History |
| History `findAllByPaymentId...` | Payment ID | 有序历史列表 |

## 6. 后端与数据库接口

### 6.1 `payments` 表契约

| 列名 | 类型 | 约束 | 业务含义 |
|---|---|---|---|
| `id` | VARCHAR(36) | PK, NOT NULL | Payment ID |
| `source_account` | VARCHAR(50) | NOT NULL | 来源账户 |
| `destination_account` | VARCHAR(50) | NOT NULL | 目标账户 |
| `amount` | DECIMAL(19,2) | NOT NULL | 付款金额 |
| `currency` | VARCHAR(3) | NOT NULL | 币种 |
| `reference` | VARCHAR(255) | NULL | 备注 |
| `status` | VARCHAR(20) | NOT NULL, INDEX | 当前状态 |
| `idempotency_key` | VARCHAR(100) | NOT NULL, UNIQUE | 幂等键 |
| `request_fingerprint` | VARCHAR(64) | 建议 NOT NULL | 判断同键请求内容是否一致 |
| `error_code` | VARCHAR(50) | NULL | 失败错误码 |
| `error_message` | VARCHAR(255) | NULL | 失败原因 |
| `created_at` | DATETIME/TIMESTAMP | NOT NULL | 创建时间 |
| `updated_at` | DATETIME/TIMESTAMP | NOT NULL | 更新时间 |

数据库约束：

- `id` 主键；
- `idempotency_key` 唯一；
- `status` 建立普通索引；
- 金额精度由列类型保证，完整金额规则仍由 Service 校验；
- 数据库存储 UTC 时间。

### 6.2 `payment_status_history` 表契约

| 列名 | 类型 | 约束 | 业务含义 |
|---|---|---|---|
| `id` | VARCHAR(36) | PK, NOT NULL | History ID |
| `payment_id` | VARCHAR(36) | FK, NOT NULL, INDEX | 关联 Payment |
| `from_status` | VARCHAR(20) | NULL | 原状态，创建记录为空 |
| `to_status` | VARCHAR(20) | NOT NULL | 新状态 |
| `triggered_by` | VARCHAR(50) | NOT NULL | `USER` 或 `SYSTEM` |
| `error_code` | VARCHAR(50) | NULL | 失败错误码 |
| `notes` | VARCHAR(255) | NULL | 状态变化说明 |
| `changed_at` | DATETIME/TIMESTAMP | NOT NULL, INDEX | 变化时间 |

数据库约束：

- `payment_id` 外键指向 `payments.id`；
- 按 `payment_id, changed_at` 建议建立组合索引；
- 不允许出现没有 Payment 的孤立历史；
- 第一轮不提供删除 Payment 的业务接口，避免审计数据丢失。

### 6.3 事务契约

以下操作必须在同一个数据库事务中：

1. 新增 Payment 与写入首条 `CREATED` 历史；
2. 更新付款状态与写入对应状态历史；
3. 写入 `FAILED`、错误详情与失败历史；
4. 幂等检查和创建应结合数据库唯一约束处理并发冲突。

任何一步失败时，本次操作整体回滚，不能出现 Payment 已更新但没有历史，或只有历史没有 Payment 的情况。

## 7. 错误码契约

| 错误码 | HTTP 状态 | 触发条件 |
|---|---:|---|
| `INVALID_AMOUNT` | 400 | 金额不满足范围或精度规则 |
| `INVALID_CURRENCY` | 400 | 币种格式或支持范围错误 |
| `INVALID_ACCOUNT` | 400 | 账户为空或格式错误 |
| `SAME_SOURCE_AND_DESTINATION` | 400 | 两个账户相同 |
| `VALIDATION_FAILED` | 400 | 通用请求校验失败 |
| `INVALID_STATUS_TRANSITION` | 400 | 跳级、回退、重复处理或终态转换 |
| `PAYMENT_NOT_FOUND` | 404 | Payment ID 不存在 |
| `DUPLICATE_PAYMENT` | 409 | 同一幂等键对应不同内容 |
| `PROCESSING_ERROR` | 500 | 未预期内部处理错误 |
| `NETWORK_ERROR` | 503 | 可选的模拟网络失败 |

## 8. 联调验收场景

1. 前端创建付款，收到 `201` 和 `CREATED`；
2. 使用相同幂等键重试同一请求，收到原 Payment ID，数据库仍只有一笔；
3. 使用相同幂等键提交不同金额，收到 `409 DUPLICATE_PAYMENT`；
4. 列表页能查询全部付款并按五种状态筛选；
5. 点击处理后，详情为 `COMPLETED`，历史依次包含四个状态；
6. 模拟失败后，详情和历史都显示相同错误码；
7. 再次处理终态付款，收到 `400 INVALID_STATUS_TRANSITION`；
8. 查询不存在的 ID，详情和历史接口都返回统一的 404 错误体；
9. 数据库 Payment 当前状态始终等于最后一条历史的目标状态；
10. 所有时间字段格式和时区在前后端展示中保持一致。

## 9. 首轮开始前必须确认的接口决策

现有文档对支持币种的示例并不完全一致：需求文档举例 `GBP/USD/EUR`，API 文档又使用了 `CNY`。开发前团队应让需求方确认第一轮最终支持集合，并将同一集合同步到校验规则、前端下拉框和测试数据。

还需确认：

- 账户格式的最终正则或示例范围；
- 处理失败是返回 200 且 Payment 为 `FAILED`，还是同时返回非 2xx；
- 首轮是否采用数据库迁移工具；
- 列表默认排序是否确定为创建时间倒序。

这些决定一旦确认，应更新本接口文档，避免前后端分别假设。
