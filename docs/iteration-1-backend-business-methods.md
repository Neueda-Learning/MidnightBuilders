# 第一轮迭代后端业务逻辑方法与四人分工

## 1. 文档目的

本文档根据项目现有的需求、API、架构、数据库及状态流转文档，汇总第一轮迭代需要完成的全部后端业务逻辑方法，并给出四人分工建议。

本文档描述方法职责和协作边界，不包含具体代码实现。

## 2. 第一轮迭代范围

第一轮需要完成以下业务能力：

1. 创建付款，初始状态为 `CREATED`；
2. 使用 Idempotency Key 防止重复付款；
3. 根据 Payment ID 查询付款详情；
4. 查询全部付款，并支持按状态筛选；
5. 模拟付款从 `CREATED` 处理到 `COMPLETED` 或 `FAILED`；
6. 阻止跳级、回退以及终态后的非法状态变化；
7. 保存每次状态变化的审计历史；
8. 在付款失败时保存错误码和错误信息；
9. 返回统一的业务异常和 HTTP 错误响应；
10. 使用 MySQL 持久化付款和状态历史。

第一轮不包含认证、账户余额、真实支付网络、外汇、批量付款、定时付款、通知和报表。

## 3. 核心状态规则

允许的状态变化：

| 当前状态 | 允许的下一状态 |
|---|---|
| `CREATED` | `VALIDATED`、`FAILED` |
| `VALIDATED` | `SENT`、`FAILED` |
| `SENT` | `COMPLETED`、`FAILED` |
| `COMPLETED` | 无 |
| `FAILED` | 无 |

正常流程为：

```text
CREATED → VALIDATED → SENT → COMPLETED
```

失败可以发生在创建后的校验、发送或确认阶段。`COMPLETED` 和 `FAILED` 都是终态。

## 4. 后端业务逻辑方法清单

### 4.1 PaymentService：付款用例编排

#### `createPayment(request, idempotencyKey)`

- 用途：创建付款。
- 输入：来源账户、目标账户、金额、币种、备注和 Idempotency Key。
- 处理：校验请求、检查幂等性、生成付款 ID、保存 `CREATED` 付款、记录首条历史。
- 输出：新付款；相同幂等键且内容一致时返回原付款。
- 异常：`INVALID_AMOUNT`、`INVALID_CURRENCY`、`INVALID_ACCOUNT`、`SAME_SOURCE_AND_DESTINATION`、`DUPLICATE_PAYMENT`。
- 事务：付款记录和首条历史必须同时成功或同时回滚。

#### `getPayment(paymentId)`

- 用途：查询单笔付款完整信息。
- 输出：账户、金额、币种、状态、失败信息、创建时间和更新时间。
- 异常：付款不存在时返回 `PAYMENT_NOT_FOUND`。

#### `listPayments(status)`

- 用途：查询付款列表。
- 输入：可选状态参数。
- 处理：未传状态时查询全部；传入状态时按状态筛选。
- 异常：状态不属于五个规定值时返回参数错误。

#### `processPayment(paymentId)`

- 用途：执行一笔付款的完整模拟处理。
- 处理顺序：查询付款、检查当前状态、业务校验、转为 `VALIDATED`、模拟发送、转为 `SENT`、模拟确认、转为 `COMPLETED`。
- 失败处理：任何处理阶段失败时调用失败逻辑，保存 `FAILED`、错误码、错误信息和历史。
- 异常：付款不存在或当前状态不能再处理。
- 事务：状态与对应历史记录必须保持一致。

#### `getPaymentHistory(paymentId)`

- 用途：查询一笔付款的完整状态历史。
- 处理：先确认付款存在，再按 `changedAt` 正序返回历史。
- 异常：付款不存在时返回 `PAYMENT_NOT_FOUND`。

#### `findPaymentOrThrow(paymentId)`

- 用途：供其他业务方法统一获取付款。
- 规则：找不到时统一抛出 `PAYMENT_NOT_FOUND`，避免各方法重复判断。

#### `markPaymentFailed(payment, errorCode, errorMessage, notes)`

- 用途：统一处理校验、发送或确认失败。
- 处理：验证当前状态可转为 `FAILED`，保存错误信息，更新付款，写入失败历史。

### 4.2 PaymentValidationService：业务校验

#### `validateCreateRequest(request)`

- 用途：创建付款前的完整校验入口。
- 调用账户、金额和币种校验方法。

#### `validatePaymentForProcessing(payment)`

- 用途：处理付款前重新确认业务数据有效。
- 失败时由处理服务将付款转为 `FAILED`。

#### `validateAmount(amount)`

- 金额必须大于 0；
- 金额不得超过 1,000,000；
- 金额最多两位小数；
- 失败错误码为 `INVALID_AMOUNT`。

#### `validateAccounts(sourceAccount, destinationAccount)`

- 两个账户均不能为空；
- 账户格式必须符合第一轮约定；
- 来源账户和目标账户不得相同；
- 对应错误码为 `INVALID_ACCOUNT` 或 `SAME_SOURCE_AND_DESTINATION`。

#### `validateCurrency(currency)`

- 币种不能为空；
- 必须为三位 ISO 4217 格式；
- 必须属于项目首轮支持的币种集合；
- 对应错误码为 `INVALID_CURRENCY`。

#### `normalizeCurrency(currency)`

- 用途：去除首尾空格并转换成大写，避免 `gbp` 和 `GBP` 被视为不同值。

### 4.3 PaymentIdempotencyService：幂等控制

#### `findByIdempotencyKey(idempotencyKey)`

- 用途：查询该幂等键是否已经创建过付款。

#### `buildRequestFingerprint(request)`

- 用途：对影响付款含义的字段生成稳定指纹。
- 字段：来源账户、目标账户、金额、币种和备注。
- 规则：生成指纹前先规范化数据。

#### `resolveDuplicate(existingPayment, requestFingerprint)`

- 指纹相同：认为是同一次请求重放，返回已有付款，不创建新记录；
- 指纹不同：返回 `DUPLICATE_PAYMENT` 和 HTTP 409。

#### `validateIdempotencyKey(idempotencyKey)`

- Idempotency Key 必须存在；
- 长度不能超过数据库字段限制；
- 数据库仍需设置唯一约束，防止并发请求绕过应用层检查。

### 4.4 PaymentStateMachine：状态机

#### `canTransition(fromStatus, toStatus)`

- 用途：判断两个状态之间是否允许转换。
- 输出：布尔值。

#### `validateTransition(fromStatus, toStatus)`

- 用途：所有状态修改前的统一校验入口。
- 异常：非法时返回 `INVALID_STATUS_TRANSITION`。

#### `getAllowedNextStatuses(currentStatus)`

- 用途：返回当前状态允许到达的状态集合，方便测试、排错和后续接口扩展。

#### `isTerminalStatus(status)`

- 用途：判断是否为 `COMPLETED` 或 `FAILED`，终态不能继续处理。

### 4.5 PaymentLifecycleService：单步状态流转

#### `markValidated(payment)`

- 执行 `CREATED → VALIDATED`；
- 更新付款时间；
- 写入“Validation passed”历史。

#### `markSent(payment)`

- 执行 `VALIDATED → SENT`；
- 更新付款时间；
- 写入“Payment sent”历史。

#### `markCompleted(payment)`

- 执行 `SENT → COMPLETED`；
- 清除不应存在的旧错误信息；
- 写入“Payment completed”历史。

#### `markFailed(payment, errorCode, errorMessage, notes)`

- 从 `CREATED`、`VALIDATED` 或 `SENT` 转为 `FAILED`；
- 保存失败详情；
- 写入带错误码的历史。

每个单步方法都必须先调用状态机，不允许直接修改状态。

### 4.6 PaymentProcessingSimulator：内部处理模拟

#### `sendPayment(payment)`

- 用途：模拟向目标处理系统发送付款。
- 输出：成功结果，或包含错误码和错误信息的失败结果。
- 第一轮不连接真实支付网络。

#### `confirmPayment(payment)`

- 用途：模拟目标系统确认付款结果。
- 输出：成功或失败结果。

#### `buildFailureResult(stage, cause)`

- 用途：把模拟器异常转换为稳定的业务失败结果。
- 建议错误码：`PROCESSING_ERROR`；如模拟网络失败，可使用 `NETWORK_ERROR`。

模拟器不负责直接修改数据库，状态更新仍由生命周期服务完成。

### 4.7 PaymentHistoryService：状态历史与审计

#### `recordCreation(payment)`

- 写入 `fromStatus = null`、`toStatus = CREATED`、`triggeredBy = USER` 的首条历史。

#### `recordTransition(payment, fromStatus, toStatus, triggeredBy, notes)`

- 用途：记录成功状态变化。
- 必填：付款、目标状态、触发方和变化时间。

#### `recordFailure(payment, fromStatus, errorCode, errorMessage, notes)`

- 用途：记录进入 `FAILED` 的状态变化和失败原因。

#### `getHistoryByPaymentId(paymentId)`

- 用途：按时间正序查询付款历史。

### 4.8 Repository：持久化查询方法

#### PaymentRepository

- `save(payment)`：新增或更新付款；
- `findById(paymentId)`：按 ID 查询；
- `findAllOrderByCreatedAtDesc()`：查询全部付款；
- `findAllByStatusOrderByCreatedAtDesc(status)`：按状态筛选；
- `findByIdempotencyKey(idempotencyKey)`：检查幂等键；
- `existsByIdempotencyKey(idempotencyKey)`：必要时进行轻量存在性检查。

#### PaymentStatusHistoryRepository

- `save(history)`：保存状态历史；
- `findAllByPaymentIdOrderByChangedAtAsc(paymentId)`：查询完整生命周期；
- `findFirstByPaymentIdOrderByChangedAtDesc(paymentId)`：校验当前状态与最后一条历史是否一致。

Repository 只负责数据访问，不放置业务规则。

### 4.9 ErrorResponseFactory / GlobalExceptionHandler：统一错误处理

#### `handleBusinessException(exception, requestPath)`

- 把业务异常映射为对应 HTTP 状态码和统一错误体。

#### `handleRequestValidationException(exception, requestPath)`

- 处理请求 DTO 的必填、长度和格式错误；
- 返回 HTTP 400 和 `VALIDATION_FAILED`。

#### `handleUnexpectedException(exception, requestPath)`

- 处理未预料的内部异常；
- 返回 HTTP 500 和 `PROCESSING_ERROR`；
- 不向客户端泄露堆栈、SQL 或敏感信息。

统一错误响应字段：

| 字段 | 说明 |
|---|---|
| `timestamp` | 错误发生时间 |
| `status` | HTTP 状态码 |
| `errorCode` | 稳定的业务错误码 |
| `message` | 可读错误说明 |
| `path` | 请求路径 |

## 5. API 与业务方法对应关系

| API | Controller 方法 | 核心业务方法 | 成功状态码 |
|---|---|---|---|
| `POST /api/payments` | `createPayment` | `PaymentService.createPayment` | 首次 201；相同请求重放 200 |
| `GET /api/payments/{id}` | `getPayment` | `PaymentService.getPayment` | 200 |
| `GET /api/payments` | `listPayments` | `PaymentService.listPayments` | 200 |
| `GET /api/payments?status=...` | `listPayments` | `PaymentService.listPayments` | 200 |
| `POST /api/payments/{id}/process` | `processPayment` | `PaymentService.processPayment` | 200 |
| `GET /api/payments/{id}/history` | `getPaymentHistory` | `PaymentService.getPaymentHistory` | 200 |

Controller 只负责参数接收、DTO 转换和 HTTP 响应，不应包含金额校验、幂等判断或状态流转规则。

## 6. 四人分工建议

### 成员 A：API 与付款用例编排

负责范围：

- Payment Controller；
- Payment Service 的五个公开用例方法；
- Request/Response DTO；
- 事务边界；
- API 集成测试和成功主流程。

主要交付：

- `createPayment`；
- `getPayment`；
- `listPayments`；
- `processPayment`；
- `getPaymentHistory`；
- 6 个 REST API。

建议分支：`feature/payment-api-orchestration`。

### 成员 B：校验、幂等与错误规范

负责范围：

- Payment Validation Service；
- Payment Idempotency Service；
- 请求指纹规则；
- 业务异常、错误码和全局异常处理；
- 校验与重复提交测试。

主要交付：

- 金额、账户、币种校验方法；
- 幂等键校验、请求指纹和重复请求判断；
- 400、404、409、500 标准错误响应。

建议分支：`feature/payment-validation-idempotency`。

### 成员 C：状态机、生命周期与模拟处理

负责范围：

- Payment State Machine；
- Payment Lifecycle Service；
- Payment Processing Simulator；
- 成功和失败状态路径测试。

主要交付：

- 合法状态表；
- 状态转换校验；
- `markValidated`、`markSent`、`markCompleted`、`markFailed`；
- 模拟发送和模拟确认。

建议分支：`feature/payment-state-processing`。

### 成员 D：数据模型、Repository 与历史审计

负责范围：

- Payment 和 PaymentStatusHistory 数据模型；
- JPA Repository；
- Payment History Service；
- 数据库约束、索引和持久化测试。

主要交付：

- `payments` 与 `payment_status_history` 映射；
- 幂等键唯一约束；
- 状态和历史查询索引；
- 创建、转换和失败历史记录方法。

建议分支：`feature/payment-persistence-audit`。

## 7. 工作量与依赖关系

| 成员 | 预计工作量 | 依赖 | 可并行部分 |
|---|---:|---|---|
| A | 约 25% | B、C、D 提供的服务接口 | DTO、Controller 契约、Mock 测试 |
| B | 约 25% | Payment 字段定义 | 校验、错误码、幂等规则 |
| C | 约 25% | PaymentStatus 枚举 | 状态机、模拟器、单元测试 |
| D | 约 25% | 数据库设计文档 | 实体、Repository、历史服务 |

建议先由四人共同确认以下公共契约：

1. Payment 和 PaymentStatusHistory 字段；
2. PaymentStatus 枚举；
3. 错误码列表；
4. Service 方法签名；
5. Request/Response DTO 字段。

确认后，B、C、D 可并行开发，A 使用接口或 Mock 先开发编排层，最后进行集成。

## 8. 合并顺序

1. 成员 D：合并实体、枚举和 Repository 基础接口；
2. 成员 B：合并校验、幂等和异常规范；
3. 成员 C：合并状态机、生命周期和模拟器；
4. 成员 A：合并业务编排、Controller 和端到端测试。

若团队希望最大化并行，可先创建只含方法签名的接口 PR，四人基于接口同时工作。

## 9. 第一轮测试清单

### 创建付款

- 合法输入创建成功并写入 `CREATED` 历史；
- 金额为 0、负数、超限或超过两位小数；
- 账户为空、格式错误或两个账户相同；
- 币种为空、格式错误或不受支持。

### 幂等

- 同一幂等键、同一请求返回原 Payment ID；
- 同一幂等键、不同请求返回 409；
- 并发使用同一幂等键时数据库最多产生一笔付款。

### 查询

- 查询存在和不存在的付款；
- 查询全部付款；
- 按五种状态分别筛选；
- 非法状态参数返回 400。

### 状态与处理

- 完整成功路径产生四条历史；
- `CREATED`、`VALIDATED`、`SENT` 均可进入 `FAILED`；
- 禁止跳级、回退、重复处理和终态转换；
- 发送失败与确认失败保存正确错误信息。

### 审计与事务

- 历史按时间正序返回；
- 每次状态变化恰好增加一条历史；
- Payment 当前状态等于最后一条历史的 `toStatus`；
- 更新付款失败时不留下孤立历史，写历史失败时不单独更新付款。

## 10. Definition of Done

- 6 个 API 均可通过 Postman 或自动化测试调用；
- 所有业务方法都有明确职责，Controller 和 Repository 中没有业务规则；
- 成功、失败、幂等和非法转换场景均有测试；
- 付款与状态历史持久化一致；
- 统一错误响应符合需求文档；
- 四名成员的 PR 通过至少一人评审；
- Maven 测试全部通过，服务可连接本地 MySQL 启动。
