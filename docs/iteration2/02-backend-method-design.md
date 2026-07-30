# 第二轮迭代：后端方法与数据库详细设计

## 1. 文档目的

本文档将 Account 校验、三阶段失败和网络延迟重试拆解到表、类、方法、参数、返回值、事务及测试。它是后续编码任务的详细设计，不是当前实现。

## 2. 三阶段处理流程

```text
接收并持久化 Payment(CREATED)
  ↓ 表单/业务字段权威校验
  ├─ 失败：CREATED -> FAILED
  └─ 成功：CREATED -> VALIDATED
        ↓ 查询付款方 Account
        ├─ 不存在：VALIDATED -> FAILED
        └─ 存在：VALIDATED -> SENT
              ↓ 模拟外部网络确认
              ├─ 每次随机延迟 0..20 秒
              ├─ 0..10 秒：成功，SENT -> COMPLETED
              └─ 11..20 秒：该次超时；最多重试 3 次
                    ├─ 任一次 <= 10 秒：SENT -> COMPLETED
                    └─ 三次重试均失败：SENT -> FAILED
```

重试定义：`maxRetries = 3` 表示首次请求失败后最多再重试三次，因此总尝试次数最多为四次。若产品语义希望“总共只请求三次”，应把配置改为 `maxAttempts = 3`，但不要混用两个概念。

## 3. Account 表设计

### 3.1 表用途

`accounts` 保存可作为付款方的账户主数据。第二轮只要求通过 `sourceAccount` 判断付款方账号是否存在；不校验收款方是否属于本系统。

### 3.2 字段

| 字段 | MySQL 类型 | 约束 | 用途 |
|---|---|---|---|
| `id` | `VARCHAR(36)` | PK | Account 内部 UUID |
| `account_number` | `VARCHAR(50)` | NOT NULL, UNIQUE | 与 Payment.sourceAccount 对应的业务账号 |
| `account_name` | `VARCHAR(100)` | NOT NULL | 账户名称/户名 |
| `account_type` | `VARCHAR(20)` | NOT NULL | PERSONAL 或 BUSINESS |
| `currency` | `VARCHAR(3)` | NOT NULL | 账户主币种，供后续币种规则使用 |
| `status` | `VARCHAR(20)` | NOT NULL | ACTIVE、BLOCKED 或 CLOSED |
| `created_at` | `TIMESTAMP` | NOT NULL | 创建时间，UTC |
| `updated_at` | `TIMESTAMP` | NOT NULL | 最近更新时间，UTC |
| `version` | `BIGINT` | NOT NULL DEFAULT 0 | 乐观锁版本，为后续余额等更新准备 |

建议约束和索引：

```sql
CONSTRAINT uk_accounts_account_number UNIQUE (account_number)
```

唯一约束已自带查询索引，不必再重复创建普通索引。若未来频繁按状态筛选，可再增加 `idx_accounts_status`。

### 3.3 是否建立 Payment 外键

当前 `payments.source_account` 保存业务账号字符串。第二轮有两种实现：

- 推荐的低风险方案：保持字符串字段不变，通过 Repository 查询 Account；不改变已有 Payment 数据结构；
- 强一致方案：给 `payments` 增加可空 `source_account_id` 外键，迁移历史数据后再改为 NOT NULL。

本轮推荐低风险方案，因为需求仅为存在性校验，且直接给已有 `source_account` 加外键会影响历史数据和测试数据。

## 4. Account 相关类与方法

### 4.1 `Account`

文件：`entity/Account.java`。

映射上述字段，`accountNumber` 创建后不应随意修改。`status`、`accountType` 使用 `@Enumerated(EnumType.STRING)`，`version` 使用 `@Version`。

### 4.2 `AccountRepository`

文件：`repository/AccountRepository.java`。

建议方法：

```java
Optional<Account> findByAccountNumber(String accountNumber);

boolean existsByAccountNumber(String accountNumber);
```

业务流程需要返回 Account 时使用 `findByAccountNumber`，不要先 `exists` 再 `find` 产生两次查询。

### 4.3 `AccountValidationService.validatePayerAccount(payment)`

参数：`Payment payment`。

返回：找到的 `Account`。

逻辑：

1. 确认 Payment 和 sourceAccount 不为空；
2. 以与创建流程相同的规则规范化账号；
3. 调用 `findByAccountNumber`；
4. 不存在时抛出 `AccountNotFoundException` 或返回明确失败结果；
5. 第二轮核心验收只要求存在性；若启用状态规则，只有 ACTIVE 才合法，并使用独立 `ACCOUNT_NOT_ACTIVE` 错误码；
6. 不在此处改变 Payment 状态。

事务：只读事务。

## 5. 表单校验核对与调整

### 5.1 当前已完成

后端 DTO 已包含：

- sourceAccount、destinationAccount 必填且最多 50 字符；
- amount 必填、0.01–1,000,000.00、最多两位小数；
- currency 必填且为三位大写字母；
- reference 最多 255 字符。

前端 `validateForm()` 已检查：

- 两个账户必填；
- 两个账户不能相同；
- 金额范围；
- 浏览器输入限制包含最大长度、小数步长和必填属性。

### 5.2 当前缺口

- `PaymentController` 的 `@Valid` 只在请求进入后端时生效，不等同于前端校验；
- 前端没有统一复用后端账户正则，规则可能漂移；
- `PaymentService` 没有注入现有 `PaymentValidationService`，而是重复实现校验；
- Bean Validation 在 Payment 被创建前返回 HTTP 400，所以默认不会产生 `CREATED → FAILED` 历史。

### 5.3 对 `CREATED → FAILED` 的处理建议

必须区分两个层次：

1. 前端预校验失败：阻止提交，不创建 Payment，不发生状态转换；
2. 后端权威校验失败：若必须审计为 `CREATED → FAILED`，后端应先安全接收并创建 CREATED，再由处理用例校验并标记 FAILED。

推荐让创建 API 只接受满足 Bean Validation 的结构化请求，而把需记录状态的业务规则放到 `processPayment` 的第一阶段。非法 JSON、缺字段等协议错误仍直接返回 400，不创建 Payment；金额上限、支持币种、账户相同等业务错误可以落为 `CREATED → FAILED`。

## 6. `PaymentService.processPayment(paymentId)` 新编排

详细逻辑：

1. 查询 Payment；
2. 只允许 CREATED 开始；
3. 执行 `PaymentValidationService.validatePaymentForProcessing`；
4. 失败则从 CREATED 标记 FAILED，记录原始错误码并返回；
5. 成功则标记 VALIDATED；
6. 调用 `AccountValidationService.validatePayerAccount`；
7. 付款方不存在则从 VALIDATED 标记 FAILED，错误码 `ACCOUNT_NOT_FOUND`；
8. Account 合法则标记 SENT；
9. 调用 `NetworkRetryService.executeConfirmation(payment)`；
10. 成功则从 SENT 标记 COMPLETED；
11. 重试耗尽则从 SENT 标记 FAILED，错误码 `NETWORK_TIMEOUT`；
12. 返回包含失败阶段、尝试次数和最终状态的响应。

事务边界建议：不要用一个长事务包住真实等待。每次状态转换使用短事务；网络等待发生在数据库事务之外，避免长时间占用连接和锁。

## 7. 随机延迟与重试详细设计

### 7.1 配置

```properties
payment.simulation.min-delay-seconds=0
payment.simulation.max-delay-seconds=20
payment.simulation.timeout-seconds=10
payment.simulation.max-retries=3
```

边界定义：0–10 秒均允许，11–20 秒超时。随机数上下界均包含。

### 7.2 单次尝试

`PaymentProcessingSimulator.attemptConfirmation(payment)`：

1. 校验 Payment 当前为 SENT；
2. 生成 `[0, 20]` 的整数；
3. 若随机值 `<= 10`，等待对应秒数后返回成功；
4. 若随机值 `> 10`，最多只等待到 10 秒超时阈值，而不是无意义地阻塞完整 11–20 秒；
5. 返回本次随机延迟、实际等待、是否超时。

如果演示必须肉眼体现完整随机延迟，也可等待生成值，但会使一次失败流程最长达到 80 秒；生产式设计推荐超时即取消。

### 7.3 重试编排

`NetworkRetryService.executeConfirmation(payment)`：

1. 执行首次尝试；
2. 成功立即返回；
3. 超时则递增重试计数；
4. 重试计数不超过 3 时再次生成独立随机延迟；
5. 首次加三次重试都超时时，返回 `NETWORK_TIMEOUT`；
6. 返回总尝试次数和每次延迟，供日志与测试使用。

本轮不建议再叠加指数退避，因为随机延迟本身已经用于演示网络等待；后续连接真实服务时可引入 Spring Retry 或 Resilience4j。

### 7.4 可测试性

- 随机生成器封装为接口或注入 `RandomGenerator`；
- 等待封装为 `DelaySleeper`；
- 单元测试使用无等待实现；
- 固定序列 `[11, 12, 9]` 应在第三次尝试成功；
- 固定序列 `[20, 20, 20, 20]` 应在四次尝试后失败；
- 10 必须成功，11 必须超时。

## 8. 错误码设计

| 失败阶段 | 转换 | 建议错误码 | 含义 |
|---|---|---|---|
| 表单业务字段 | CREATED → FAILED | 现有 INVALID_* / VALIDATION_FAILED | 字段不合法 |
| 付款方账户 | VALIDATED → FAILED | ACCOUNT_NOT_FOUND | sourceAccount 不在 accounts 表 |
| 账户状态（可选） | VALIDATED → FAILED | ACCOUNT_NOT_ACTIVE | 账户存在但不可付款 |
| 网络延迟 | SENT → FAILED | NETWORK_TIMEOUT | 四次尝试均超过 10 秒 |
| 其他网络异常 | SENT → FAILED | NETWORK_ERROR | DNS、连接等异常 |

`payment_status_history.notes` 应记录阶段和尝试次数，但不要保存敏感账户信息或异常堆栈。

## 9. 必须覆盖的测试

1. Account 表迁移、唯一账号和枚举字段；
2. sourceAccount 存在时通过；
3. sourceAccount 不存在时形成 VALIDATED → FAILED；
4. 字段业务错误形成 CREATED → FAILED；
5. 延迟 0 秒与 10 秒均完成；
6. 延迟 11 秒触发重试；
7. 前三次超时、第四次成功时完成；
8. 四次均超时时形成 SENT → FAILED；
9. 每条转换均有连续历史；
10. FAILED 后再次 process 被拒绝；
11. 测试不产生真实秒级等待；
12. 完整 Maven 测试与 Flyway Schema Validation 通过。

