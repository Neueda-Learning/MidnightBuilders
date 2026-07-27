# 第一轮迭代：后端方法详细设计

## 1. 文档目的

本文档把第一轮后端需求拆解到具体目录、文件、类和方法。每个方法均说明职责、调用方、参数、返回值、主要逻辑、异常、事务要求和协作对象，可直接用于创建开发任务与代码评审清单。

本文档是设计说明，不提供具体实现代码。下文的方法名属于建议契约，开发过程中若调整命名，应同步修改接口文档和相关测试。

## 2. 总体调用流程

创建付款：

```text
PaymentController
  → PaymentService.createPayment
  → PaymentValidationService.validateCreateRequest
  → PaymentIdempotencyService.check
  → PaymentRepository.save
  → PaymentHistoryService.recordCreation
  → PaymentMapper.toPaymentResponse
```

处理付款：

```text
PaymentController
  → PaymentService.processPayment
  → PaymentValidationService.validatePaymentForProcessing
  → PaymentLifecycleService.markValidated
  → PaymentProcessingSimulator.sendPayment
  → PaymentLifecycleService.markSent
  → PaymentProcessingSimulator.confirmPayment
  → PaymentLifecycleService.markCompleted / markFailed
```

## 3. Controller 层

### 3.1 文件与类

- 目录：`demo1/src/main/java/com/example/demo/controller/`
- 文件：`PaymentController.java`
- 类：`PaymentController`
- 职责：定义 `/api/payments` REST API，完成 HTTP 参数到 DTO 的转换并调用 `PaymentService`。

### 3.2 `createPayment(request, idempotencyKey)`

调用方：前端创建付款页面、Postman 或其他 API Client。

参数：

| 参数 | 来源 | 类型 | 说明 |
|---|---|---|---|
| `request` | Request Body | `CreatePaymentRequest` | 付款业务字段 |
| `idempotencyKey` | Header | String | 客户端生成的幂等键 |

返回：`PaymentResponse` 包装的 HTTP Response。

方法逻辑：

1. 由框架完成 JSON 反序列化和 DTO 基础约束检查；
2. 读取必填的 `Idempotency-Key` 请求头；
3. 调用 `PaymentService.createPayment`；
4. 如果结果表示首次创建，返回 201，并建议设置 Location Header；
5. 如果结果表示相同请求重放，返回 200；
6. 不在 Controller 内判断金额、币种或重复付款。

### 3.3 `getPayment(id)`

参数：路径参数 `id`，类型 String。

返回：`PaymentResponse`，HTTP 200。

方法逻辑：把 ID 交给 `PaymentService.getPayment`。不存在异常由全局异常处理器转换成 404。

### 3.4 `listPayments(status)`

参数：可选 Query 参数 `status`，类型 String。

返回：`PaymentListItemResponse` 列表。

方法逻辑：不传状态时查询全部；传入状态时由 Service 解析和校验。Controller 不直接调用不同 Repository 方法。

### 3.5 `processPayment(id)`

参数：路径参数 `id`。

返回：`ProcessPaymentResponse`。

方法逻辑：调用 `PaymentService.processPayment`，不允许请求 Body 指定目标状态，防止前端绕过生命周期。

### 3.6 `getPaymentHistory(id)`

参数：路径参数 `id`。

返回：按时间正序的 `PaymentHistoryResponse` 列表。

方法逻辑：调用 `PaymentService.getPaymentHistory`。Controller 不直接查询历史 Repository。

## 4. Request DTO

### 4.1 文件与类

- 目录：`demo1/src/main/java/com/example/demo/dto/request/`
- 文件：`CreatePaymentRequest.java`
- 类：`CreatePaymentRequest`

字段：`sourceAccount`、`destinationAccount`、`amount`、`currency`、`reference`。

用途：只描述客户端允许提交的数据。不得包含 `id`、`status`、`errorCode`、`createdAt` 等由后端维护的字段。

基础校验：

- 字符串必填和最大长度；
- amount 必填；
- currency 基础三位格式；
- reference 最大 255 字符。

跨字段规则、支持币种和金额业务上限仍由 Validation Service 负责。

## 5. Response DTO

### 5.1 `PaymentResponse`

- 文件：`dto/response/PaymentResponse.java`
- 用途：付款详情和创建付款响应。
- 字段：全部付款业务字段，但不返回 `idempotencyKey` 和请求指纹，避免泄露内部幂等信息。

### 5.2 `PaymentListItemResponse`

- 文件：`dto/response/PaymentListItemResponse.java`
- 用途：付款列表。
- 字段：`id`、`amount`、`currency`、`status`、`createdAt`、可选 `errorCode`。
- 原因：列表不需要返回完整账户和长错误信息，减少耦合与数据量。

### 5.3 `ProcessPaymentResponse`

- 文件：`dto/response/ProcessPaymentResponse.java`
- 用途：处理付款结果。
- 字段：ID、调用前状态、当前状态、结果说明、错误码和错误信息。

### 5.4 `PaymentHistoryResponse`

- 文件：`dto/response/PaymentHistoryResponse.java`
- 用途：单条审计历史展示。
- 字段：原状态、目标状态、触发者、错误码、备注和变化时间。

### 5.5 `ErrorResponse`

- 文件：`dto/response/ErrorResponse.java`
- 用途：所有非成功 HTTP 响应的统一结构。
- 字段：时间、HTTP 状态、错误码、信息和路径。

## 6. PaymentService：用例编排

### 6.1 文件与类

- 目录：`demo1/src/main/java/com/example/demo/service/`
- 文件：`PaymentService.java`
- 类：`PaymentService`
- 依赖：PaymentRepository、Validation Service、Idempotency Service、Lifecycle Service、History Service、Simulator 和 Mapper。

### 6.2 `createPayment(request, idempotencyKey)`

功能：完成创建付款的整个业务用例。

参数：

- `request`：经过基础格式校验的 CreatePaymentRequest；
- `idempotencyKey`：本次业务请求的唯一键。

返回：建议使用内部结果对象，包含 PaymentResponse 和 `created` 标记，供 Controller 区分 201 与 200。

详细逻辑：

1. 校验 Idempotency Key 是否存在且长度合法；
2. 调用 `validateCreateRequest` 检查完整业务规则；
3. 规范化账户、金额、币种和备注；
4. 生成请求指纹；
5. 查询同一幂等键；
6. 若存在且指纹相同，直接映射并返回已有付款；
7. 若存在但指纹不同，抛 `DUPLICATE_PAYMENT`；
8. 若不存在，生成 Payment ID 和当前 UTC 时间；
9. 构造初始状态为 `CREATED` 的 Payment；
10. 保存 Payment；
11. 写入首条创建历史；
12. 映射并返回首次创建结果。

事务：步骤 8–11 必须属于同一事务。并发重复提交还需捕获数据库唯一约束冲突，再查询已有记录作一致性判断。

### 6.3 `getPayment(paymentId)`

功能：查询单笔付款详情。

逻辑：调用 `findPaymentOrThrow`，再通过 Mapper 转换为 PaymentResponse。

只读事务：是。

### 6.4 `listPayments(statusText)`

功能：查询全部付款或按状态筛选。

参数：可为空的状态文本。

逻辑：

1. 参数为空时调用全部查询；
2. 不为空时先去空格、转大写并解析 PaymentStatus；
3. 解析失败时抛 `VALIDATION_FAILED`；
4. 调用按状态查询；
5. 将实体列表映射为列表响应。

只读事务：是。

### 6.5 `processPayment(paymentId)`

功能：编排完整付款生命周期，是第一轮最核心的方法。

详细逻辑：

1. 查询付款，不存在则 404；
2. 确认当前状态为 `CREATED`，否则抛非法状态转换；
3. 记录调用前状态；
4. 重新执行处理前业务校验；
5. 校验失败时调用 `markFailed`，返回 FAILED 结果；
6. 校验通过后调用 `markValidated`；
7. 调用 Simulator 的 `sendPayment`；
8. 发送失败时调用 `markFailed` 并结束；
9. 发送成功时调用 `markSent`；
10. 调用 Simulator 的 `confirmPayment`；
11. 确认失败时调用 `markFailed` 并结束；
12. 确认成功时调用 `markCompleted`；
13. 返回最终状态和处理说明。

关键约束：每次状态变化必须同步记录历史；任何异常都不能使 Payment 状态与历史不一致；Simulator 不直接更新状态。

### 6.6 `getPaymentHistory(paymentId)`

功能：查询状态历史。

逻辑：先调用 `findPaymentOrThrow`，确保“不存在付款”和“存在但无历史”语义不同；再调用 History Service 查询并映射。

### 6.7 `findPaymentOrThrow(paymentId)`

功能：统一 Payment 查找和 404 处理。

参数：Payment ID。

返回：Payment Entity。

异常：找不到时抛 PaymentNotFoundException 或带 `PAYMENT_NOT_FOUND` 的 BusinessException。

可见性：建议为 Service 内部私有方法；若多个 Service 需要它，可提取查询服务，但第一轮不必过度拆分。

## 7. PaymentValidationService：业务校验

### 7.1 文件与类

- 文件：`service/PaymentValidationService.java`
- 类：`PaymentValidationService`
- 配置依赖：最大金额、支持币种、账户格式。

### 7.2 `validateCreateRequest(request)`

功能：创建前完整校验入口。

逻辑：依次调用账户、金额、币种和备注校验。采用失败即停止策略，返回第一个清晰错误；若团队希望一次展示全部表单错误，可在后续扩展聚合错误列表。

### 7.3 `validatePaymentForProcessing(payment)`

功能：处理前校验已持久化付款。

用处：即使创建时已校验，也保留处理校验阶段以符合 `CREATED → VALIDATED` 的业务含义，并为未来账户状态、余额或风控规则预留入口。

### 7.4 `validateAmount(amount)`

参数：Decimal 金额。

逻辑：检查非空、大于 0、不超过配置上限、最多两位小数。失败抛 `INVALID_AMOUNT`。

注意：使用十进制金额类型进行比较，不能使用 double/float。

### 7.5 `validateAccounts(source, destination)`

参数：来源和目标账户字符串。

逻辑：

1. 检查非空和去空格后非空；
2. 检查长度；
3. 检查约定格式；
4. 比较规范化后的值，禁止相同账户；
5. 格式错误使用 `INVALID_ACCOUNT`，相同账户使用 `SAME_SOURCE_AND_DESTINATION`。

### 7.6 `validateCurrency(currency)`

逻辑：规范化为大写，检查三位格式，检查是否属于支持集合。失败使用 `INVALID_CURRENCY`。

### 7.7 `normalizeCurrency(currency)`

返回：去空格后的大写币种。

用途：校验、保存和请求指纹必须使用同一规范化结果。

## 8. PaymentIdempotencyService：幂等控制

### 8.1 文件与类

- 文件：`service/PaymentIdempotencyService.java`
- 类：`PaymentIdempotencyService`
- 依赖：PaymentRepository、RequestFingerprintGenerator。

### 8.2 `validateKey(idempotencyKey)`

检查请求头存在、去空格后非空以及不超过 100 字符。失败返回 `VALIDATION_FAILED`。

### 8.3 `check(idempotencyKey, request)`

功能：给 PaymentService 返回三种明确决定：可以创建、返回已有付款、发生冲突。

逻辑：

1. 规范化 key；
2. 生成当前请求指纹；
3. 按 key 查询付款；
4. 不存在时返回“允许创建”和指纹；
5. 存在且指纹相同时返回“重放”以及已有付款；
6. 存在但指纹不同时抛 DuplicatePaymentException。

### 8.4 `handleConcurrentDuplicate(idempotencyKey, fingerprint, databaseException)`

功能：处理两个请求同时通过“未找到”检查后竞争唯一键的情况。

逻辑：确认异常确实来自幂等键唯一约束，重新查询已有付款，再按指纹判断重放或冲突。其他数据库异常继续向上抛出，不能全部伪装成重复付款。

## 9. RequestFingerprintGenerator：请求指纹

### 9.1 文件与类

- 文件：`util/RequestFingerprintGenerator.java`
- 类：`RequestFingerprintGenerator`

### 9.2 `generate(request)`

功能：为请求内容生成稳定摘要。

参数：CreatePaymentRequest。

逻辑：

1. 对账户和备注去除首尾空格；
2. 币种转大写；
3. 金额转换为一致的十进制文本，使 `100.0` 与 `100.00` 等价；
4. 按固定字段顺序组合；
5. 使用稳定摘要算法生成固定长度字符串。

安全和一致性：不使用对象默认字符串或不稳定 JSON 字段顺序；指纹只用于等价判断，不替代认证或签名。

## 10. PaymentStateMachine：状态规则

### 10.1 文件与类

- 目录：`statemachine/`
- 文件：`PaymentStateMachine.java`
- 类：`PaymentStateMachine`

### 10.2 `canTransition(fromStatus, toStatus)`

返回布尔值。基于集中定义的状态转换表判断，不访问数据库。

### 10.3 `validateTransition(fromStatus, toStatus)`

功能：任何状态修改前必须调用。

逻辑：调用 `canTransition`；返回 false 时抛 `INVALID_STATUS_TRANSITION`，错误信息包含原状态和目标状态。

### 10.4 `getAllowedNextStatuses(status)`

返回当前状态允许到达的只读集合。用于参数化测试和问题排查，调用方不能修改内部转换表。

### 10.5 `isTerminalStatus(status)`

当状态为 `COMPLETED` 或 `FAILED` 时返回 true。用于在处理开始时快速拒绝终态付款。

## 11. PaymentLifecycleService：状态变更

### 11.1 文件与类

- 文件：`service/PaymentLifecycleService.java`
- 类：`PaymentLifecycleService`
- 依赖：StateMachine、PaymentRepository、History Service、Clock。

### 11.2 `markValidated(payment)`

逻辑：保存原状态，校验 `CREATED → VALIDATED`，取得统一当前时间，更新状态与更新时间，保存 Payment，记录 SYSTEM 触发的验证成功历史。

### 11.3 `markSent(payment)`

逻辑：校验 `VALIDATED → SENT`，更新付款，记录模拟发送成功历史。

### 11.4 `markCompleted(payment)`

逻辑：校验 `SENT → COMPLETED`，更新付款，确保成功付款没有错误字段，记录完成历史。

### 11.5 `markFailed(payment, errorCode, errorMessage, notes)`

参数：Payment、稳定错误码、可读错误信息、审计备注。

逻辑：

1. 保存失败前状态；
2. 校验可从当前状态进入 FAILED；
3. 校验失败码和信息非空；
4. 更新状态、错误字段和更新时间；
5. 保存 Payment；
6. 写入包含原状态、FAILED、SYSTEM、错误码和备注的历史。

事务：每个 `mark...` 方法的付款更新与历史新增必须原子化。需要特别注意同类内部方法调用时 Spring 事务代理可能不生效，事务边界应由架构统一决定并测试。

## 12. PaymentProcessingSimulator：处理模拟

### 12.1 文件与类

- 文件：`service/PaymentProcessingSimulator.java`
- 类：`PaymentProcessingSimulator`

### 12.2 `sendPayment(payment)`

功能：模拟把付款发送到外部目标系统。

参数：只读使用的 Payment。

返回：ProcessingResult，包括成功标记、可选错误码和错误信息。

第一轮策略：默认成功；为测试失败流程，可以使用可配置或可替换的 Simulator，而不是在 PaymentService 写特殊测试条件。

### 12.3 `confirmPayment(payment)`

功能：模拟目标系统最终确认付款。

返回：ProcessingResult。成功后由 Lifecycle Service 进入 COMPLETED；失败后由 Lifecycle Service 进入 FAILED。

### 12.4 `toFailureResult(stage, cause)`

功能：把模拟处理异常转换为一致结果。

规则：网络类故障可映射 `NETWORK_ERROR`，其他模拟处理故障映射 `PROCESSING_ERROR`；日志保留技术原因，对外错误信息不泄露内部堆栈。

## 13. PaymentHistoryService：审计历史

### 13.1 文件与类

- 文件：`service/PaymentHistoryService.java`
- 类：`PaymentHistoryService`
- 依赖：History Repository、Mapper、Clock 或调用方提供的统一变化时间。

### 13.2 `recordCreation(payment, changedAt)`

构造首条历史：原状态为空，目标状态 CREATED，触发者 USER，备注为付款已创建，错误码为空。

### 13.3 `recordTransition(payment, fromStatus, toStatus, triggeredBy, notes, changedAt)`

功能：保存普通状态变化。

校验：Payment、目标状态、触发者和时间不能为空；原状态仅创建记录允许为空。

### 13.4 `recordFailure(payment, fromStatus, errorCode, errorMessage, notes, changedAt)`

功能：保存失败审计。目标状态固定为 FAILED，错误码必填，备注应包含足够的问题定位信息但不能含敏感数据。

### 13.5 `getHistory(paymentId)`

调用按付款 ID 和时间正序查询，并映射为响应列表。只读事务。

## 14. PaymentMapper：对象映射

### 14.1 文件与类

- 文件：`mapper/PaymentMapper.java`
- 类：`PaymentMapper`

### 14.2 `toEntity(request, idempotencyKey, fingerprint, now)`

功能：创建新的 Payment Entity。

规则：生成 ID，初始状态强制为 CREATED，设置规范化字段和相同的创建/更新时间；不能接受客户端状态。

### 14.3 `toPaymentResponse(payment)`

把 Payment 映射成详情响应，不返回幂等键、指纹及 JPA 内部信息。

### 14.4 `toListItemResponse(payment)`

只映射列表展示需要的字段。

### 14.5 `toHistoryResponse(history)`

把历史实体映射成前端时间轴条目，不返回数据库主键和实体关联对象。

## 15. Repository 方法

### 15.1 `PaymentRepository.java`

目录：`repository/`。接口管理 Payment Entity。

方法设计：

- `findById(paymentId)`：详情、处理和历史存在性检查；
- `findByIdempotencyKey(key)`：幂等判断；
- `findAllByOrderByCreatedAtDesc()`：默认列表；
- `findAllByStatusOrderByCreatedAtDesc(status)`：状态筛选；
- `save(payment)`：新增或更新；
- `existsByIdempotencyKey(key)`：只在无需读取已有付款时使用。

注意：继承框架已有的 `findById` 和 `save` 时无需重复声明；文档列出的是业务会使用的数据库能力。

### 15.2 `PaymentStatusHistoryRepository.java`

方法设计：

- `save(history)`：保存一条历史；
- `findAllByPaymentIdOrderByChangedAtAsc(paymentId)`：返回完整有序历史；
- `findFirstByPaymentIdOrderByChangedAtDesc(paymentId)`：取得最后一条历史，用于一致性测试或诊断。

## 16. Entity 行为

### 16.1 `Payment.java`

目录：`entity/`。

建议行为方法：

- `changeStatus(nextStatus, changedAt)`：仅执行字段变化，调用前由 StateMachine 校验；
- `markFailed(errorCode, errorMessage, changedAt)`：设置 FAILED 和失败详情；
- `clearFailure()`：成功状态下清理错误字段；
- Getter：提供业务读取；
- 不提供任意状态 Setter，避免绕过状态机。

Entity 字段需与数据库设计一致，并为幂等键设置唯一约束、为状态建立索引。

### 16.2 `PaymentStatusHistory.java`

主要行为：构造不可随意修改的审计记录。历史创建后原则上不更新和删除。

## 17. 枚举

### 17.1 `PaymentStatus.java`

值：CREATED、VALIDATED、SENT、COMPLETED、FAILED。

### 17.2 `PaymentErrorCode.java`

至少包含：INVALID_AMOUNT、INVALID_CURRENCY、INVALID_ACCOUNT、SAME_SOURCE_AND_DESTINATION、DUPLICATE_PAYMENT、INVALID_STATUS_TRANSITION、PAYMENT_NOT_FOUND、VALIDATION_FAILED、PROCESSING_ERROR；是否加入 NETWORK_ERROR 由第一轮模拟策略决定。

### 17.3 `TriggeredBy.java`

第一轮包含 USER 和 SYSTEM。创建由 USER 触发，内部状态处理由 SYSTEM 触发。

## 18. 异常处理

### 18.1 `BusinessException`

- 文件：`exception/BusinessException.java`
- 字段：错误码、HTTP 状态和可读消息。
- 用途：统一承载预期业务失败。

### 18.2 专用异常

可选文件：PaymentNotFoundException、DuplicatePaymentException、InvalidStatusTransitionException。它们继承 BusinessException，固定错误码和 HTTP 状态，使调用点更易读。

### 18.3 `GlobalExceptionHandler`

- 文件：`exception/GlobalExceptionHandler.java`

方法：

- `handleBusinessException`：使用异常内的状态和错误码构造 ErrorResponse；
- `handleMethodArgumentNotValid`：收集请求 DTO 校验错误，返回 400；
- `handleMissingHeader`：缺少 Idempotency-Key 时返回 400；
- `handleUnexpectedException`：记录完整服务端日志，只向客户端返回通用 500。

## 19. 配置方法

### 19.1 `ClockConfig.clock()`

- 文件：`config/ClockConfig.java`
- 返回系统 UTC Clock。
- 用处：生产代码统一取时，测试可替换为固定 Clock，避免时间断言不稳定。

### 19.2 `PaymentProperties`

- 文件：`config/PaymentProperties.java`
- 属性：最大付款金额、支持币种、账户格式等。
- 用处：避免业务常量分散在多个类；修改规则时只改配置和测试。

## 20. 方法级事务设计

| 方法 | 事务类型 | 原因 |
|---|---|---|
| `createPayment` | 读写事务 | Payment 与创建历史原子提交 |
| `getPayment` | 只读事务 | 单笔查询 |
| `listPayments` | 只读事务 | 列表查询 |
| `processPayment` | 读写事务 | 多次状态和历史必须一致 |
| `getPaymentHistory` | 只读事务 | 历史查询 |
| Lifecycle `mark...` | 参与外层事务 | 避免部分状态提交 |

如果未来模拟器包含真实网络调用，不应让数据库事务长时间包围远程请求；第一轮是内部同步模拟，可先保持简单，后续再设计事件、Outbox 和重试。

## 21. 方法对应测试

| 被测类 | 必测内容 |
|---|---|
| PaymentController | 路由、参数、Header、HTTP 状态和错误体 |
| PaymentService | 创建、查询、筛选、成功处理、各阶段失败 |
| Validation Service | 金额边界、账户规则、支持/不支持币种 |
| Idempotency Service | 新键、同内容重放、不同内容冲突、并发冲突 |
| StateMachine | 每个合法和非法状态组合 |
| Lifecycle Service | 每次状态与历史同时更新 |
| Simulator | 发送和确认成功/失败结果 |
| History Service | 创建历史、失败历史、时间顺序 |
| Repository | 唯一键、状态查询、历史排序 |
| Mapper | 字段正确且内部字段不泄露 |

## 22. 四人文件级分工

### 成员 A：API 与用例编排

- `controller/PaymentController.java`
- `dto/request/CreatePaymentRequest.java`
- `dto/response/PaymentResponse.java`
- `dto/response/PaymentListItemResponse.java`
- `dto/response/ProcessPaymentResponse.java`
- `service/PaymentService.java`
- 对应 Controller、Service 和集成测试

### 成员 B：校验、幂等与异常

- `service/PaymentValidationService.java`
- `service/PaymentIdempotencyService.java`
- `util/RequestFingerprintGenerator.java`
- `dto/internal/IdempotencyDecision.java`
- `exception/` 下所有业务异常和全局处理器
- `config/PaymentProperties.java`
- 对应单元测试

### 成员 C：状态与模拟处理

- `statemachine/PaymentStateMachine.java`
- `service/PaymentLifecycleService.java`
- `service/PaymentProcessingSimulator.java`
- `dto/internal/ProcessingResult.java`
- `enums/PaymentStatus.java`
- 对应状态和失败路径测试

### 成员 D：持久化、历史与映射

- `entity/Payment.java`
- `entity/PaymentStatusHistory.java`
- `repository/PaymentRepository.java`
- `repository/PaymentStatusHistoryRepository.java`
- `service/PaymentHistoryService.java`
- `dto/response/PaymentHistoryResponse.java`
- `mapper/PaymentMapper.java`
- 数据库迁移文件和持久化测试

公共文件修改必须先在团队内同步，尤其是 PaymentStatus、Entity 字段、DTO 字段和错误码。

## 23. 评审检查清单

- Controller 是否只处理 HTTP 边界；
- 外部响应是否完全使用 DTO；
- 所有状态更新是否经过 StateMachine；
- 每次状态变化是否记录历史；
- 幂等键是否同时有应用检查与数据库唯一约束；
- 同键同请求是否返回原付款，同键不同请求是否 409；
- 失败付款是否同时保存错误码和错误信息；
- 付款更新与历史写入是否在同一事务；
- 查询是否使用正确排序和索引；
- 错误响应是否统一且不泄露内部异常；
- 时间是否统一采用 UTC；
- 金额是否使用十进制类型；
- 测试是否覆盖成功、失败、重复和非法状态转换。
