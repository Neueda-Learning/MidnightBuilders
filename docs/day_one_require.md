# Payment Processing System 需求文档 V0.1

## 1. 项目背景

本项目是入职培训最终项目中的一个选题，主题是 **Payment Processing System**。项目重点是构建一个围绕“付款生命周期”的系统：一笔付款从创建开始，经过校验、发送、完成，或者在某个阶段失败。系统需要能够记录每一次状态变化，并支持用户查看付款详情、付款状态和历史记录。

在项目选题说明中，Payment Processing 的重点是 **payment lifecycle and status transitions**，典型交付物包括 REST API、持久化层、状态历史 / 审计记录，以及可选的前端 UI。

## 2. 项目目标

本项目的目标是实现一个简化版的付款处理系统，用于模拟金融付款的完整处理流程。

系统需要支持：

1. 创建一笔付款；
2. 查询付款详情；
3. 查看付款当前状态；
4. 查看付款状态变化历史；
5. 按状态搜索或筛选付款；
6. 在付款失败时展示失败原因；
7. 防止重复提交同一笔付款；
8. 阻止非法状态流转；
9. 使用数据库持久化保存付款和状态历史。

项目说明明确要求团队设计一个 Payments Processing REST API，用于创建、查询和追踪付款的完整生命周期；同时前端优先支持创建付款、查看付款状态和详情、查看状态历史、按状态筛选付款以及查看失败详情。

## 3. 系统范围

### 3.1 MVP 范围，也就是第一版必须完成的内容

第一版系统应该尽量小，但必须完整可演示。建议 MVP 包含：

| 模块    | 说明                                               |
| ----- | ------------------------------------------------ |
| 创建付款  | 用户输入付款信息，系统生成一笔付款记录                              |
| 查询付款  | 用户可以通过 payment id 查询付款详情                         |
| 付款列表  | 用户可以查看所有付款                                       |
| 状态筛选  | 用户可以按 CREATED、VALIDATED、SENT、COMPLETED、FAILED 筛选 |
| 状态处理  | 系统可以推动付款从 CREATED 进入后续状态                         |
| 状态历史  | 每次状态变化都保存到历史记录中                                  |
| 失败处理  | 校验失败或处理失败时，付款进入 FAILED，并保存错误码                    |
| 数据持久化 | 付款和历史记录都保存到数据库                                   |
| 基础测试  | 覆盖成功流程、失败流程、重复提交、非法状态变化                          |

### 3.2 暂不包含的内容

第一版不建议做以下功能：

| 暂不做的功能         | 原因                                               |
| -------------- | ------------------------------------------------ |
| 真实银行支付网络集成     | 项目说明明确说不需要接入真实 payment networks/gateways，只需要内部模拟 |
| 用户登录和权限系统      | 项目说明假设无认证、单用户，不需要管理用户或账户归属                       |
| 真实账户余额扣减       | 容易把系统复杂度拉高，不适合作为第一版                              |
| 外汇兑换           | 可以作为后期增强，不适合 MVP                                 |
| 批量付款           | 后期扩展                                             |
| 定时付款 / 循环付款    | 后期扩展                                             |
| 邮件通知 / Webhook | 后期扩展                                             |
| 报表分析           | 后期扩展                                             |

项目说明也强调应先从最小可行实现开始，先让系统保存一笔非常简单的付款，再逐步增强；同时提醒团队不要一开始设计过于复杂的数据模型。

## 4. 核心业务概念

### 4.1 Payment 付款

Payment 是系统中的核心对象，代表一笔付款请求。

第一版 Payment 建议包含以下字段：

| 字段                 | 说明                | 是否必需  |
| ------------------ | ----------------- | ----- |
| id                 | 系统生成的付款唯一 ID      | 是     |
| sourceAccount      | 付款来源账户            | 是     |
| destinationAccount | 收款账户              | 是     |
| amount             | 金额                | 是     |
| currency           | 币种，例如 GBP、USD、EUR | 是     |
| reference          | 付款备注或业务说明         | 否     |
| status             | 当前付款状态            | 是     |
| idempotencyKey     | 幂等键，用于防止重复提交      | 建议必需  |
| errorCode          | 失败错误码             | 失败时必需 |
| errorMessage       | 失败原因描述            | 失败时必需 |
| createdAt          | 创建时间              | 是     |
| updatedAt          | 最后更新时间            | 是     |

### 4.2 Payment Status 付款状态

系统包含五种核心状态：

| 状态        | 含义                    |
| --------- | --------------------- |
| CREATED   | 付款已提交，但尚未完成业务校验       |
| VALIDATED | 付款已通过校验，准备发送          |
| SENT      | 付款已发送到目标处理系统，项目中为模拟发送 |
| COMPLETED | 付款已成功处理               |
| FAILED    | 付款在某个阶段失败，并带有错误码      |

项目说明给出的付款生命周期是：`CREATED → VALIDATED → SENT → COMPLETED`，同时 `FAILED` 可以在处理过程中出现。

## 5. 付款生命周期

系统应该支持以下正常流程：

```text
CREATED → VALIDATED → SENT → COMPLETED
```

也应该支持以下失败流程：

```text
CREATED → FAILED
VALIDATED → FAILED
SENT → FAILED
```

允许的状态变化：

| 当前状态      | 允许变化到             |
| --------- | ----------------- |
| CREATED   | VALIDATED, FAILED |
| VALIDATED | SENT, FAILED      |
| SENT      | COMPLETED, FAILED |
| COMPLETED | 无                 |
| FAILED    | 无                 |

不允许的状态变化示例：

| 非法变化                | 原因            |
| ------------------- | ------------- |
| CREATED → COMPLETED | 跳过了校验和发送      |
| COMPLETED → CREATED | 已完成付款不能回到初始状态 |
| FAILED → SENT       | 已失败付款不能继续发送   |
| SENT → CREATED      | 付款不能倒退        |
| COMPLETED → FAILED  | 已完成付款不应再变成失败  |

状态变化是这个项目最核心的业务逻辑之一。项目说明也要求团队考虑如何处理状态转换，并防止例如 COMPLETED 回到 CREATED 这样的不合理变化。

## 6. 用户角色

第一版系统只假设一个用户，不需要登录。

可以把用户理解为：

> 一个内部操作人员，想要提交付款、查看付款处理结果，并在失败时知道失败原因。

项目说明中也明确写到：不需要 authentication，并且假设 single user，不需要管理用户或账户所有权。

## 7. 用户故事

### 用户故事 1：创建付款

作为用户，我希望输入来源账户、目标账户、金额、币种和备注，创建一笔新的付款。

验收标准：

* 当输入合法时，系统创建付款；
* 新付款的初始状态为 CREATED；
* 系统返回付款 ID；
* 系统记录创建时间；
* 如果金额、币种或账户信息不合法，系统返回明确错误信息。

### 用户故事 2：查看付款详情

作为用户，我希望通过付款 ID 查看一笔付款的完整信息。

验收标准：

* 用户输入 payment id；
* 系统返回付款金额、币种、账户、状态、创建时间、更新时间；
* 如果付款失败，系统返回 errorCode 和 errorMessage；
* 如果 payment id 不存在，返回 PAYMENT_NOT_FOUND。

### 用户故事 3：查看付款列表

作为用户，我希望看到所有已经创建的付款。

验收标准：

* 系统返回付款列表；
* 每条记录展示 payment id、金额、币种、状态、创建时间；
* 列表可以用于前端页面展示。

### 用户故事 4：按状态筛选付款

作为用户，我希望只查看某一种状态的付款，例如 FAILED 或 COMPLETED。

验收标准：

* 用户可以传入状态作为筛选条件；
* 系统只返回对应状态的付款；
* 如果状态值不合法，系统返回错误提示。

### 用户故事 5：处理付款

作为用户，我希望系统可以模拟处理一笔付款，让它从 CREATED 逐步进入 COMPLETED 或 FAILED。

验收标准：

* CREATED 状态的付款可以被处理；
* 系统先校验付款；
* 校验成功后状态变为 VALIDATED；
* 模拟发送成功后状态变为 SENT；
* 模拟完成后状态变为 COMPLETED；
* 如果任何阶段失败，状态变为 FAILED；
* 每一次状态变化都写入状态历史。

### 用户故事 6：查看状态历史

作为用户，我希望查看一笔付款经历过哪些状态变化。

验收标准：

* 用户输入 payment id；
* 系统返回该付款所有状态变化记录；
* 每条历史记录包含 fromStatus、toStatus、changedAt、triggeredBy、notes；
* 如果失败，应显示 errorCode 和失败说明。

项目说明特别强调，每次状态变化都应该记录时间戳，并记录是谁或什么触发了状态变化，这对于 debugging 和 compliance 很重要。

## 8. 业务规则

### 8.1 金额规则

* 金额必须大于 0；
* 金额不能超过系统设置的最大限额，例如 1,000,000；
* 金额最多保留两位小数。

### 8.2 账户规则

* sourceAccount 不能为空；
* destinationAccount 不能为空；
* sourceAccount 和 destinationAccount 不能相同；
* 第一版可以只校验账户格式，不强制连接真实账户系统。

### 8.3 币种规则

* currency 不能为空；
* currency 应符合 ISO 4217 格式，例如 GBP、USD、EUR；
* 第一版可以只支持少数币种，例如 GBP、USD、EUR；
* 不支持的币种返回 INVALID_CURRENCY。

### 8.4 幂等性规则

为避免重复付款，创建付款时建议使用 idempotencyKey。

规则如下：

* 第一次提交某个 idempotencyKey 时，系统创建新付款；
* 同一个 idempotencyKey 再次提交时，系统返回已有付款；
* 如果同一个 idempotencyKey 对应的请求内容不一致，系统返回 DUPLICATE_PAYMENT 或 409 Conflict；
* 数据库中 idempotencyKey 应保持唯一。

项目说明也提示团队应尽早考虑 idempotency，即客户端重复提交同一笔付款时系统应该如何处理。

### 8.5 状态转换规则

所有状态变化必须经过统一的状态转换逻辑，不允许前端或调用方随意修改状态。

系统必须拒绝非法状态变化，例如：

* COMPLETED → CREATED；
* CREATED → COMPLETED；
* FAILED → SENT。

项目说明建议定义合法状态转换，并考虑使用 state machine pattern。

## 9. 错误码设计

第一版建议支持以下错误码：

| 错误码                         | HTTP 状态码 | 说明                        |
| --------------------------- | -------: | ------------------------- |
| INVALID_AMOUNT              |      400 | 金额为 0、负数或格式非法             |
| INVALID_CURRENCY            |      400 | 币种不受支持                    |
| INVALID_ACCOUNT             |      400 | 账户格式非法                    |
| SAME_SOURCE_AND_DESTINATION |      400 | 来源账户和目标账户相同               |
| DUPLICATE_PAYMENT           |      409 | 相同 idempotency key 的付款已存在 |
| INVALID_STATUS_TRANSITION   |      400 | 状态变化不合法                   |
| PAYMENT_NOT_FOUND           |      404 | 付款不存在                     |
| VALIDATION_FAILED           |      400 | 付款未通过业务校验                 |
| PROCESSING_ERROR            |      500 | 内部处理错误                    |

项目文档附录中也提供了类似的错误码示例，包括 INVALID_AMOUNT、INVALID_CURRENCY、DUPLICATE_PAYMENT、INVALID_STATUS_TRANSITION、PAYMENT_NOT_FOUND、PROCESSING_ERROR 等。

统一错误响应格式建议如下：

```json
{
  "timestamp": "2026-07-25T10:00:00Z",
  "status": 400,
  "errorCode": "INVALID_AMOUNT",
  "message": "Payment amount must be greater than zero",
  "path": "/api/payments"
}
```

## 10. API 需求草案

### 10.1 创建付款

```http
POST /api/payments
```

请求示例：

```json
{
  "sourceAccount": "12345678",
  "destinationAccount": "87654321",
  "amount": 100.00,
  "currency": "GBP",
  "reference": "Invoice 1001",
  "idempotencyKey": "request-001"
}
```

成功响应：

```http
201 Created
```

返回：

```json
{
  "id": "pay_001",
  "status": "CREATED",
  "amount": 100.00,
  "currency": "GBP",
  "createdAt": "2026-07-25T10:00:00Z"
}
```

### 10.2 查询付款详情

```http
GET /api/payments/{paymentId}
```

### 10.3 查询付款列表

```http
GET /api/payments
```

### 10.4 按状态筛选付款

```http
GET /api/payments?status=FAILED
```

### 10.5 处理付款

```http
POST /api/payments/{paymentId}/process
```

说明：

* 该接口用于模拟处理付款；
* 系统内部推动状态变化；
* 成功时最终状态为 COMPLETED；
* 失败时最终状态为 FAILED；
* 每次状态变化都写入历史记录。

### 10.6 查询付款历史

```http
GET /api/payments/{paymentId}/history
```

返回示例：

```json
[
  {
    "fromStatus": null,
    "toStatus": "CREATED",
    "changedAt": "2026-07-25T10:00:00Z",
    "triggeredBy": "USER",
    "notes": "Payment created"
  },
  {
    "fromStatus": "CREATED",
    "toStatus": "VALIDATED",
    "changedAt": "2026-07-25T10:01:00Z",
    "triggeredBy": "SYSTEM",
    "notes": "Validation passed"
  }
]
```

## 11. 数据需求

### 11.1 payments 表

| 字段                  | 类型说明          |
| ------------------- | ------------- |
| id                  | UUID / String |
| source_account      | String        |
| destination_account | String        |
| amount              | Decimal       |
| currency            | String        |
| reference           | String        |
| status              | String / Enum |
| idempotency_key     | String        |
| error_code          | String        |
| error_message       | String        |
| created_at          | Timestamp     |
| updated_at          | Timestamp     |

### 11.2 payment_status_history 表

| 字段           | 类型说明          |
| ------------ | ------------- |
| id           | UUID / String |
| payment_id   | UUID / String |
| from_status  | String / Enum |
| to_status    | String / Enum |
| changed_at   | Timestamp     |
| triggered_by | String        |
| error_code   | String        |
| notes        | String        |

## 12. 前端页面需求

第一版前端不需要复杂，但至少可以考虑以下页面。

### 12.1 创建付款页面

用户可以输入：

* Source Account；
* Destination Account；
* Amount；
* Currency；
* Reference。

页面应显示：

* 创建成功提示；
* 创建失败错误信息；
* 新生成的 payment id。

### 12.2 付款列表页面

页面展示：

* Payment ID；
* Amount；
* Currency；
* Status；
* Created Date。

页面支持：

* 按状态筛选；
* 点击一行进入详情页。

### 12.3 付款详情页面

页面展示：

* 付款基本信息；
* 当前状态；
* 失败原因；
* 创建时间和更新时间；
* 状态历史时间线。

### 12.4 状态历史页面或区域

可以在详情页中展示：

* 每次状态变化；
* 变化时间；
* 从哪个状态到哪个状态；
* 是否失败；
* 错误码和备注。

项目说明中的 UI Ideas 也包含创建付款页面、付款详情页面、付款列表页面和状态历史可视化等方向。

## 13. 非功能需求

### 13.1 可维护性

系统应采用分层结构，建议至少分为：

* Controller / API Layer；
* Service / Business Logic Layer；
* Repository / Data Access Layer；
* Database。

项目说明也建议采用类似 Web UI、REST API、Business Logic、Data Access、Database 的分层架构，并强调分离关注点、让业务逻辑可测试。

### 13.2 可测试性

核心业务逻辑应可以单独测试，尤其是：

* 金额校验；
* 币种校验；
* 账户校验；
* 状态转换；
* 幂等性；
* 失败处理；
* 状态历史记录。

### 13.3 可演示性

系统最终需要能完成一条清晰的演示路径：

1. 创建一笔付款；
2. 查看付款初始状态 CREATED；
3. 点击或调用处理接口；
4. 付款经过 VALIDATED、SENT；
5. 最终变为 COMPLETED；
6. 查看完整状态历史；
7. 再创建一笔失败付款；
8. 展示 FAILED 状态和错误原因；
9. 演示重复提交不会创建重复付款；
10. 演示非法状态变化会被拒绝。

## 14. 测试场景

第一版至少应覆盖以下测试：

| 测试场景                   | 预期结果                         |
| ---------------------- | ---------------------------- |
| 创建合法付款                 | 返回 CREATED                   |
| 处理合法付款                 | 最终进入 COMPLETED               |
| 创建金额为负数的付款             | 返回 INVALID_AMOUNT            |
| 使用不支持币种                | 返回 INVALID_CURRENCY          |
| 来源账户和目标账户相同            | 返回错误                         |
| 查询不存在付款                | 返回 PAYMENT_NOT_FOUND         |
| 重复提交同一 idempotencyKey  | 不创建重复付款                      |
| CREATED 直接改为 COMPLETED | 返回 INVALID_STATUS_TRANSITION |
| 查询付款历史                 | 返回完整状态变化记录                   |
| 模拟处理失败                 | 状态变为 FAILED，并记录 errorCode    |

项目说明的 Testing Considerations 也建议覆盖 happy path、validation failures、duplicate detection、invalid state transitions、concurrent updates 和 database failure simulation。

## 15. 四人小组建议分工

| 成员   | 主要负责内容                   |
| ---- | ------------------------ |
| 成员 A | 需求整理、数据模型、数据库表设计         |
| 成员 B | 创建付款、查询付款、付款列表 API       |
| 成员 C | 状态机、付款处理逻辑、状态历史          |
| 成员 D | 测试、Swagger/API 文档、前端页面原型 |

建议不是完全割裂开发，而是：

* 先全组一起确认需求和数据模型；
* 核心状态机至少两个人一起设计；
* 每个人都要理解完整业务流程；
* 使用 Git 分支和 Pull Request；
* 每天进行短会同步进度。

项目说明也建议团队自行决定分工方式、制作任务列表、使用 Trello 等工具管理任务，并保持敏捷，不要一开始把数据模型做得太复杂。

## 15. 四人小组建议分工（基于后端开发）

考虑到你们当前技术栈是 **Java + Spring Boot + MySQL**，并且第一版目标是先把后端 MVP 做通，这里建议按“后端功能模块”来拆分，而不是按“人 + 页面”来拆分。这样每个人都有相对完整的后端职责，同时接口之间也比较容易并行开发。

### 15.1 四人后端分工建议

| 成员   | 负责模块 | 主要功能范围 | 预期交付物 |
| ---- | ---- | ---- | ---- |
| 成员 A | 数据模型与持久化层 | 设计 `Payment`、`PaymentStatusHistory` 数据结构，设计数据库表，配置 JPA / Repository，处理 MySQL 表结构落地 | Entity、Repository、数据库表结构、基础数据访问能力 |
| 成员 B | Payment 基础查询与创建 API | 实现创建付款、查询付款详情、付款列表、按状态筛选；处理创建请求参数校验；处理幂等键基础逻辑 | 创建/查询相关 Controller、Service、DTO、基础校验 |
| 成员 C | Payment 生命周期处理 | 实现付款状态流转规则、`/process` 处理逻辑、失败处理、状态历史记录、非法状态转换校验 | 状态机/流转逻辑、处理接口、历史记录写入 |
| 成员 D | 通用能力与质量保障 | 实现统一异常处理、错误码返回、Swagger/OpenAPI 文档、后端测试（单元测试/集成测试） | 全局异常处理、错误响应结构、接口文档、测试代码 |

### 15.2 每个人更细的工作内容

#### 成员 A：数据模型、数据库、Repository
建议负责偏底层和基础设施的内容，确保其他 3 人都能在统一的数据结构上开发。

主要任务：
- 定义 `Payment` 实体字段；
- 定义 `PaymentStatusHistory` 实体字段；
- 定义 `PaymentStatus` 枚举；
- 设计 `payments` 表和 `payment_status_history` 表；
- 处理 `idempotency_key` 唯一约束；
- 编写 Repository 接口；
- 确保本地 MySQL 可以正常建表和连接；
- 和成员 B、C 一起确认字段是否满足接口和状态流转需求。

建议优先完成：
1. `Payment` 数据模型；
2. `PaymentStatusHistory` 数据模型；
3. MySQL 表结构；
4. Repository；
5. 基础持久化验证。

---

#### 成员 B：创建付款 + 查询类 API
建议负责最先能跑通的“基础 CRUD 风格能力”，因为这部分是整个系统最容易最快出结果的地方。

主要任务：
- 实现 `POST /api/payments` 创建付款；
- 实现 `GET /api/payments/{paymentId}` 查询付款详情；
- 实现 `GET /api/payments` 查询付款列表；
- 实现 `GET /api/payments?status=FAILED` 这类按状态筛选；
- 编写创建付款请求 DTO / 响应 DTO；
- 做基础参数校验，例如金额、币种、账户非空；
- 和成员 A 对齐实体字段，和成员 D 对齐错误返回格式。

建议优先完成：
1. 创建付款接口；
2. 查询单笔付款接口；
3. 付款列表接口；
4. 按状态筛选接口；
5. 创建时幂等键的基础接入。

---

#### 成员 C：状态机、处理流程、状态历史
建议负责项目最核心的业务逻辑，也就是“付款如何从 CREATED 走到 COMPLETED 或 FAILED”。

主要任务：
- 定义允许的状态转换规则；
- 实现 `POST /api/payments/{paymentId}/process`；
- 实现从 `CREATED → VALIDATED → SENT → COMPLETED` 的处理流程；
- 实现失败流转：`CREATED/VALIDATED/SENT → FAILED`；
- 拒绝非法状态变更，例如 `COMPLETED → CREATED`；
- 每次状态变化时写入 `payment_status_history`；
- 实现 `GET /api/payments/{paymentId}/history` 所依赖的历史数据组织逻辑；
- 和成员 A 对齐历史表结构，和成员 D 对齐错误码设计。

建议优先完成：
1. 状态枚举和状态转换规则；
2. 处理付款主流程；
3. 失败场景处理；
4. 状态历史写入；
5. 历史查询支撑逻辑。

---

#### 成员 D：统一异常、文档、测试
建议负责把项目“补完整”，确保这个后端不仅能写出来，而且能稳定演示、能解释、能测。

主要任务：
- 定义统一错误响应格式；
- 实现全局异常处理；
- 维护错误码，例如 `INVALID_AMOUNT`、`PAYMENT_NOT_FOUND`、`INVALID_STATUS_TRANSITION`；
- 编写 Swagger/OpenAPI 文档；
- 编写单元测试和集成测试；
- 覆盖 happy path、校验失败、重复提交、非法状态流转、历史查询等场景；
- 协助检查各成员接口返回格式是否一致。

建议优先完成：
1. 错误码和统一返回结构；
2. 全局异常处理；
3. Swagger/OpenAPI；
4. Payment 创建/查询测试；
5. Payment 处理流程与失败场景测试。

### 15.3 建议的协作方式

虽然按模块分工，但这 4 个人不是完全割裂开发，建议这样协作：

- **A + B** 先对齐创建付款所需字段、DTO 与数据库结构；
- **A + C** 先对齐状态机、历史表、错误字段；
- **B + D** 对齐创建/查询接口的返回格式与错误码；
- **C + D** 对齐处理流程测试、非法状态流转测试、失败场景测试；
- 全组一起先确认：
  - `Payment` 字段最终版；
  - 状态枚举；
  - 错误码集合；
  - API 路径和响应结构。

### 15.4 推荐开发顺序

为了减少互相阻塞，建议后端按以下顺序推进：

1. **成员 A** 先把实体、表结构、Repository 打底；
2. **成员 B** 在此基础上先完成创建付款、查询付款、列表接口；
3. **成员 C** 接着完成处理接口、状态机和历史记录；
4. **成员 D** 全程并行补异常处理、Swagger 和测试；
5. 最后全组联调完整主流程：创建 → 查询 → 处理 → 查历史 → 测失败。

### 15.5 分支建议

为了减少冲突，建议每个人至少使用独立功能分支：

- 成员 A：`feature/payment-domain-and-repository`
- 成员 B：`feature/payment-create-and-query-api`
- 成员 C：`feature/payment-processing-and-history`
- 成员 D：`feature/error-handling-swagger-tests`

### 15.6 这套分工的好处

这种拆法比较适合第一版 MVP，原因是：

- 每个人都在做“后端功能”，没有人被边缘化；
- A 做底层支撑，B/C 做核心业务，D 做质量闭环，职责清晰；
- B 和 C 可以围绕不同接口并行开发；
- D 能尽早统一错误格式和测试标准，避免最后返工；
- 很适合你们当前这个 Payment Processing 项目的核心目标：**先把 REST API + 状态流转 + 历史记录做完整**。

项目说明也建议团队自行决定分工方式、制作任务列表、使用 Trello 等工具管理任务，并保持敏捷，不要一开始把数据模型做得太复杂。

## 16. 后续增强功能

如果 MVP 完成后还有时间，可以考虑：

| 增强功能    | 说明              |
| ------- | --------------- |
| 批量付款    | 一次提交多笔付款        |
| 付款调度    | 设置未来时间付款        |
| 通知      | 付款失败或完成时发送通知    |
| 报表分析    | 成功率、失败率、每日交易量   |
| 并发控制    | 防止两次请求同时修改同一笔付款 |
| 付款取消    | 在付款完成前取消        |
| 付款撤销    | 对已完成付款创建反向付款    |
| 多币种增强   | 支持汇率和币种转换       |
| 更完整审计日志 | 记录更多操作行为        |

这些功能在项目说明中也被列为 Advanced Features，适合在核心系统完成后再做。

## 17. 需要问 Instructor 的问题

在正式设计前，建议你们问 instructor：

1. 创建付款后，是停在 CREATED，还是自动开始处理？
2. 付款处理是一次性自动完成，还是需要分步骤推进？
3. 失败场景应该随机模拟，还是由特定输入触发？
4. 是否需要真实账户数据？
5. 是否必须实现 idempotency？
6. 前端是否必须完成，还是 REST API + Swagger 即可？
7. 数据库是否有指定技术？
8. 最终演示时间是多少分钟？
9. 是否需要展示测试覆盖？
10. 是否允许使用自己熟悉的前端框架？

## 18. 第一版验收标准

当系统满足以下条件时，可以认为 MVP 完成：

* 可以创建付款；
* 可以查询付款；
* 可以查看付款列表；
* 可以按状态筛选付款；
* 可以处理付款并完成状态流转；
* 可以记录并查询状态历史；
* 可以处理失败场景；
* 可以返回明确错误码；
* 可以防止重复提交；
* 可以拒绝非法状态变化；
* 数据保存到数据库；
* 有基础 API 文档；
* 有基础测试；
* 可以完成 5 分钟以内的完整演示。

## 19. 项目一句话总结

本项目不是要做一个真正连接银行网络的支付系统，而是要做一个 **模拟付款生命周期的 REST API 系统**。重点不是“钱真的转走了”，而是系统如何可靠地管理一笔付款从创建、校验、发送、完成或失败的全过程，并且每一步都能被查询、追踪和解释。
