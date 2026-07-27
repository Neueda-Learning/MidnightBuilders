# 角色D P1 代码架构可视化

## 1. 整体分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                         HTTP API Layer                       │
│  POST /api/payments  |  GET /api/payments  |  GET /history  │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    Controller & Router                       │ ← Role A
│              (PaymentController 待实现)                      │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                      Service Layer                           │
├──────────────────────────────────────────────────────────────│
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │ PaymentService  │  │ Validation      │  │ Idempotency │ │ ← Role A/B
│  │ (编排)          │→ │ Service(Role B) │→ │ Service     │ │
│  └─────────────────┘  └─────────────────┘  │ (Role B)    │ │
│                                             └─────────────┘ │
│  ┌──────────────────────┐  ┌────────────────────────────┐   │
│  │ StateMachine         │  │ LifecycleService           │   │ ← Role C
│  │ (Role C)             │  │ (Role C)                   │   │
│  └──────────────────────┘  └────────────────────────────┘   │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ PaymentHistoryService (Role D ✅ 已实现)               │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ PaymentMapper (Role D ✅ 已实现)                        │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   Repository Layer (Role D ✅)              │
├──────────────────────────────────────────────────────────────│
│  PaymentRepository              │ PaymentStatusHistoryRepo  │
│  ├── findById()                 ├── findAllByPaymentId      │
│  ├── findByIdempotencyKey()     └── findFirstByPaymentId    │
│  ├── findAllByOrderByCreated    (历史表访问)               │
│  └── findAllByStatus...                                     │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    Entity Layer (Role D ✅)                 │
├──────────────────────────────────────────────────────────────│
│  Payment Entity (13字段)    │ PaymentStatusHistory (8字段) │
│  ├── id                      ├── id                        │
│  ├── sourceAccount           ├── paymentId (FK)            │
│  ├── destinationAccount      ├── fromStatus (nullable)     │
│  ├── amount (BigDecimal)     ├── toStatus                  │
│  ├── currency                ├── triggeredBy               │
│  ├── reference               ├── errorCode                 │
│  ├── status                  ├── notes                     │
│  ├── idempotencyKey (UNIQUE) └── changedAt                 │
│  ├── requestFingerprint                                    │
│  ├── errorCode & errorMessage                              │
│  ├── createdAt & updatedAt                                 │
│  └── 业务方法: changeStatus, markFailed, clearFailure      │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   MySQL Database                            │
├──────────────────────────────────────────────────────────────│
│  ┌──────────────────┐      ┌──────────────────────────────┐ │
│  │ payments 表      │  ←FK─ │ payment_status_history 表  │ │
│  │                  │       │                            │ │
│  │ PK: id           │       │ PK: id                    │ │
│  │ UNIQUE:          │       │ FK: payment_id ──→ payments│ │
│  │   idempotency_   │       │ INDEX: (payment_id,       │ │
│  │   key            │       │         changed_at)       │ │
│  │ INDEX: status    │       │                            │ │
│  └──────────────────┘       └──────────────────────────────┘ │
│                                                              │
│  支付审计链条：payment ← ← ← history (1:N 关系)            │
└─────────────────────────────────────────────────────────────┘


DTO Layer (Role D ✅ 已实现)
├── Request
│   └── CreatePaymentRequest (验证注解)
└── Response
    ├── PaymentResponse (详情)
    ├── PaymentListItemResponse (列表)
    └── PaymentHistoryResponse (历史)

Enum Layer (Role D ✅ 已实现)
├── PaymentStatus (5值)
├── PaymentErrorCode (10值)
└── TriggeredBy (2值)
```

---

## 2. 创建支付的数据流

```
用户点击"创建支付"
        │
        ▼
┌─────────────────────────────────┐
│  前端填表并提交                 │
│  {                              │
│    sourceAccount: "ACC001"      │
│    destinationAccount: "ACC002" │
│    amount: 1000.00              │
│    currency: "CNY"              │
│    reference: "Invoice #123"    │
│  }                              │
│  Header: Idempotency-Key        │
└─────────┬───────────────────────┘
          │
          ▼ HTTP POST /api/payments
┌─────────────────────────────────┐ 
│ Role A: PaymentController       │
│ - 验证 Idempotency-Key Header   │
│ - 反序列化为 CreatePaymentRequest│
│   (字段级验证: @NotBlank等)     │
└─────────┬───────────────────────┘
          │
          ▼
┌─────────────────────────────────────┐
│ Role A: PaymentService              │
│ .createPayment(request, idempKey)   │
└─────────┬─────────────────────────────┘
          │
          ├─→ Role B: PaymentIdempotencyService
          │   .check(idempKey, request)
          │   ↓
          │   PaymentRepository
          │   .findByIdempotencyKey("xxx-yyy-zzz")
          │   ↓
          │   生成 requestFingerprint (SHA256)
          │   ↓
          │   决定：新建 or 重放 or 冲突
          │
          ├─→ Role B: PaymentValidationService
          │   .validateCreateRequest(request)
          │   ↓
          │   检查金额、币种、账户、reference
          │
          ├─→ Role D: PaymentMapper
          │   .toEntity(request, idempKey, fingerprint, now)
          │   ↓
          │   生成 Payment Entity:
          │   ├─ id = UUID.randomUUID()      ✨ 新ID
          │   ├─ status = CREATED             ✨ 强制初始状态
          │   ├─ createdAt = now
          │   ├─ updatedAt = now              ✨ 初始时相同
          │   └─ idempotencyKey = "xxx-yyy-zzz"
          │
          ├─→ Role D: PaymentRepository
          │   .save(payment)
          │   ↓
          │   INSERT INTO payments (...)
          │   VALUES ('pay-abc', 'ACC001', ...)
          │   (idempotency_key UNIQUE 约束生效)
          │
          ├─→ Role D: PaymentHistoryService
          │   .recordCreation(payment, now)
          │   ↓
          │   创建 PaymentStatusHistory:
          │   ├─ id = UUID.randomUUID()
          │   ├─ paymentId = "pay-abc"
          │   ├─ fromStatus = null             ✨ 创建记录特殊标记
          │   ├─ toStatus = CREATED
          │   ├─ triggeredBy = USER            ✨ 用户操作
          │   ├─ errorCode = null
          │   └─ changedAt = now
          │   ↓
          │   PaymentStatusHistoryRepository
          │   .save(history)
          │   ↓
          │   INSERT INTO payment_status_history (...)
          │
          ├─→ Role D: PaymentMapper
          │   .toPaymentResponse(payment)
          │   ↓
          │   {
          │     "id": "pay-abc",
          │     "sourceAccount": "ACC001",
          │     ...
          │     "status": "CREATED",
          │     "createdAt": "2026-07-27T10:00:00Z",
          │     "updatedAt": "2026-07-27T10:00:00Z"
          │     // ❌ 不返回: idempotencyKey, requestFingerprint
          │   }
          │
          └─→ Role A: PaymentController
              HttpStatus: 201 Created
              Location: /api/payments/pay-abc
              Body: PaymentResponse

                    ▼ HTTP 响应到前端
          
          前端收到 201 + PaymentResponse
          ├─ 显示 Payment ID: "pay-abc"
          ├─ 显示状态: CREATED
          └─ 提示: "支付创建成功"
```

---

## 3. 查询支付详情的数据流

```
用户点击"查看详情"(Payment ID: pay-abc)
        │
        ▼ HTTP GET /api/payments/pay-abc
┌─────────────────────────────────────┐
│ Role A: PaymentController           │
│ .getPayment("pay-abc")              │
└─────────┬───────────────────────────┘
          │
          ▼
┌─────────────────────────────────────┐
│ Role A: PaymentService              │
│ .getPayment("pay-abc")              │
│ 调用 .findPaymentOrThrow("pay-abc") │
└─────────┬───────────────────────────┘
          │
          ├─→ Role D: PaymentRepository
          │   .findById("pay-abc")
          │   ↓
          │   SELECT * FROM payments
          │   WHERE id = "pay-abc"
          │   ↓
          │   返回 Optional<Payment>
          │
          ├─→ 若找不到，抛 PaymentNotFoundException (404)
          │
          ├─→ Role D: PaymentMapper
          │   .toPaymentResponse(payment)
          │   ↓
          │   {
          │     "id": "pay-abc",
          │     "sourceAccount": "ACC001",
          │     "destinationAccount": "ACC002",
          │     "amount": 1000.00,
          │     "currency": "CNY",
          │     "reference": "Invoice #123",
          │     "status": "COMPLETED",
          │     "errorCode": null,
          │     "errorMessage": null,
          │     "createdAt": "2026-07-27T10:00:00Z",
          │     "updatedAt": "2026-07-27T10:05:00Z"
          │   }
          │
          └─→ Role A: PaymentController
              HttpStatus: 200 OK
              Body: PaymentResponse

              ▼ HTTP 响应到前端
              
          前端渲染详情页面
```

---

## 4. 查询历史时间线的数据流

```
用户打开"历史"选项卡 (Payment ID: pay-abc)
        │
        ▼ HTTP GET /api/payments/pay-abc/history
┌─────────────────────────────────────┐
│ Role A: PaymentController           │
│ .getPaymentHistory("pay-abc")       │
└─────────┬───────────────────────────┘
          │
          ▼
┌─────────────────────────────────────┐
│ Role A: PaymentService              │
│ .getPaymentHistory("pay-abc")       │
└─────────┬───────────────────────────┘
          │
          ├─→ Role D: PaymentHistoryService
          │   .getHistory("pay-abc")
          │   ↓
          │   @Transactional(readOnly=true)
          │   PaymentStatusHistoryRepository
          │   .findAllByPaymentIdOrderByChangedAtAsc("pay-abc")
          │   ↓
          │   SELECT * FROM payment_status_history
          │   WHERE payment_id = "pay-abc"
          │   ORDER BY changed_at ASC
          │   ↓
          │   返回 List<PaymentStatusHistory>:
          │   [
          │     {id: 'his-1', fromStatus: null,      toStatus: CREATED,   triggeredBy: USER,   changedAt: 10:00},
          │     {id: 'his-2', fromStatus: CREATED,   toStatus: VALIDATED, triggeredBy: SYSTEM, changedAt: 10:01},
          │     {id: 'his-3', fromStatus: VALIDATED, toStatus: SENT,      triggeredBy: SYSTEM, changedAt: 10:02},
          │     {id: 'his-4', fromStatus: SENT,      toStatus: COMPLETED, triggeredBy: SYSTEM, changedAt: 10:05}
          │   ]
          │
          ├─→ Role D: PaymentMapper
          │   (循环所有历史记录)
          │   .toHistoryResponse(history[i])
          │   ↓
          │   转换为:
          │   [
          │     {fromStatus: null,      toStatus: CREATED,   triggeredBy: USER,   notes: "Payment created",           changedAt: "2026-07-27T10:00:00Z"},
          │     {fromStatus: CREATED,   toStatus: VALIDATED, triggeredBy: SYSTEM, notes: "Validation passed",         changedAt: "2026-07-27T10:01:00Z"},
          │     {fromStatus: VALIDATED, toStatus: SENT,      triggeredBy: SYSTEM, notes: "Payment sent",              changedAt: "2026-07-27T10:02:00Z"},
          │     {fromStatus: SENT,      toStatus: COMPLETED, triggeredBy: SYSTEM, notes: "Confirmation received",     changedAt: "2026-07-27T10:05:00Z"}
          │   ]
          │
          └─→ Role A: PaymentController
              HttpStatus: 200 OK
              Body: List<PaymentHistoryResponse>

              ▼ HTTP 响应到前端
              
          前端渲染时间轴
          ┌─────────────────────────────┐
          │ 创建     │ ✓ Payment created   │← USER
          │ 10:00   │                     │
          ├─────────────────────────────┤
          │ 编审通过  │ ✓ Validation passed │← SYSTEM
          │ 10:01   │                     │
          ├─────────────────────────────┤
          │ 已发送   │ ✓ Payment sent      │← SYSTEM
          │ 10:02   │                     │
          ├─────────────────────────────┤
          │ 完成     │ ✓ Confirmation rec  │← SYSTEM
          │ 10:05   │                     │
          └─────────────────────────────┘
```

---

## 5. 状态转换的生命周期

```
支付创建时：CREATED
        │
        │ ProcessPayment 开始
        │ Role C: PaymentLifecycleService.markValidated()
        ▼
状态为：VALIDATED
        │ 存储: {status: VALIDATED, updatedAt: now}
        │ 历史: {fromStatus: CREATED, toStatus: VALIDATED, triggeredBy: SYSTEM}
        │
        │ Role C: PaymentProcessingSimulator.sendPayment() -> 成功
        ▼
状态为：SENT
        │ 存储: {status: SENT, updatedAt: now}
        │ 历史: {fromStatus: VALIDATED, toStatus: SENT, triggeredBy: SYSTEM}
        │
        │ Role C: PaymentProcessingSimulator.confirmPayment() -> 成功
        ▼
状态为：COMPLETED ❌ 终态（不能再转换）
        │ 存储: {status: COMPLETED, updatedAt: now, errorCode: null}
        │ 历史: {fromStatus: SENT, toStatus: COMPLETED, triggeredBy: SYSTEM}
        │
        └─ 若用户再点"处理"，返回 400 INVALID_STATUS_TRANSITION


或者在任何阶段发生错误：
        │
        │ Role B/C: 发现问题 or Role C: Simulator 失败
        ▼
状态为：FAILED ❌ 终态
        │ 存储: {status: FAILED, updatedAt: now, errorCode: "NETWORK_ERROR", errorMessage: "..."}
        │ 历史: {fromStatus: {CREATED,VALIDATED,SENT}, toStatus: FAILED, triggeredBy: SYSTEM, errorCode: "NETWORK_ERROR"}
        │
        └─ 无法恢复，用户需要重新创建新支付
```

---

## 6. 幂等性的设计

```
同一个请求可能被提交多次（网络超时重试）

第一次请求：
POST /api/payments
Header: Idempotency-Key: "req-2026-07-27-001"
Body: {sourceAccount: ACC001, destinationAccount: ACC002, amount: 1000, currency: CNY}
        │
        ▼ Role B: PaymentIdempotencyService.check()
        ├─ 规范化 key: "req-2026-07-27-001"
        ├─ 生成指纹: SHA256("ACC001|ACC002|1000|CNY") = "abc123...xyz"
        ├─ 查询: SELECT * FROM payments WHERE idempotency_key = "req-2026-07-27-001"
        ├─ 结果: 不存在
        ├─ 允许创建新支付: DECISION_CREATE_NEW
        └─ 返回: {status: CREATE_NEW, fingerprint: "abc123...xyz"}
        │
        ▼ 创建支付，返回 201 Created + Payment ID: pay-abc
        │
        ▼ 用户网络超时，未收到响应，点"重试"
        │

第二次请求（网络重试）：
POST /api/payments
Header: Idempotency-Key: "req-2026-07-27-001"  ← 相同的key
Body: {sourceAccount: ACC001, destinationAccount: ACC002, amount: 1000, currency: CNY}  ← 相同的内容
        │
        ▼ Role B: PaymentIdempotencyService.check()
        ├─ 规范化 key: "req-2026-07-27-001"
        ├─ 生成指纹: SHA256("ACC001|ACC002|1000|CNY") = "abc123...xyz"
        ├─ 查询: SELECT * FROM payments WHERE idempotency_key = "req-2026-07-27-001"
        ├─ 结果: 存在记录，payment_id = pay-abc, request_fingerprint = "abc123...xyz"
        ├─ 比较指纹: "abc123...xyz" == "abc123...xyz" ✓
        ├─ 决定：这是合法的重放
        └─ 返回: {status: REPLAY, payment: pay-abc}
        │
        ▼ 返回原支付，HTTP 200 OK + Payment ID: pay-abc（与第一次相同）


但如果有人篡改请求内容（同key不同内容）：
POST /api/payments
Header: Idempotency-Key: "req-2026-07-27-001"  ← 相同的key（故意重用）
Body: {sourceAccount: ACC001, destinationAccount: ACC003, amount: 2000, currency: USD}  ← 不同的内容
        │
        ▼ Role B: PaymentIdempotencyService.check()
        ├─ 规范化 key: "req-2026-07-27-001"
        ├─ 生成指纹: SHA256("ACC001|ACC003|2000|USD") = "def456...uvw"  ← 不同！
        ├─ 查询: SELECT * FROM payments WHERE idempotency_key = "req-2026-07-27-001"
        ├─ 结果: 存在记录，但 request_fingerprint = "abc123...xyz"
        ├─ 比较指纹: "def456...uvw" ≠ "abc123...xyz" ✗
        ├─ 发现冲突！这是故意攻击或客户端Bug
        └─ 抛出: DUPLICATE_PAYMENT 异常
        │
        ▼ 返回 409 Conflict，拒绝请求


数据库层面也有保护：
UNIQUE 约束: idempotency_key
        │
        └─ 即使应用层有Bug，两个并发请求也不能同时插入相同的key
        │
        ▼ 第二个请求会被 violate UNIQUE constraint，
          应用层需要捕获这个异常并做一致性判断
```

---

## 7. Component Dependency Graph

```
非常高的耦合度问题处理：

                    ┌─────────────────┐
                    │  PaymentStatus  │ ← 所有人依赖
                    │  (Enum: 5值)    │
                    └────────┬────────┘
                             │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
   ┌─────────┐          ┌──────────┐         ┌─────────────┐
   │ Payment │          │ History  │         │ Controller  │
   │ Entity  │          │ Response │         │ (Role A)    │
   │ (Role D)│          │ DTO      │         │             │
   │         │          │ (Role D) │         │             │
   └─────────┘          └──────────┘         └─────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                             │
                    ← 共享模型，版本控制重要！
                      任何改动都要通知其他人
```

---

## 8. 数据库约束与索引

```
payments 表：

CREATE TABLE payments (
    id VARCHAR(36) PRIMARY KEY,
    ├─ PK: 唯一标识每笔支付
    │
    source_account VARCHAR(50) NOT NULL,
    destination_account VARCHAR(50) NOT NULL,
    ├─ 无约束，但 Service 层确保格式正确
    │
    amount DECIMAL(19,2) NOT NULL,
    ├─ 精度: 整数部分19位，小数部分2位（分）
    │
    currency VARCHAR(3) NOT NULL,
    ├─ 三位码，Service 层验证支持列表
    │
    status VARCHAR(20) NOT NULL,
    ├─ INDEX idx_status: 支持按状态快速查询
    │      SELECT * FROM payments WHERE status = 'COMPLETED'
    │
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    ├─ UNIQUE 约束: 防止并发冲突
    │      两个请求同时INSERT相同的key，第二个报错
    │
    request_fingerprint VARCHAR(64) NOT NULL,
    ├─ 存储请求指纹，判断重放vs冲突
    │
    error_code VARCHAR(50),
    error_message VARCHAR(255),
    ├─ 失败时填充，成功时为 NULL
    │
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
    └─ 记录时间戳，不支持修改created_at

payment_status_history 表：

CREATE TABLE payment_status_history (
    id VARCHAR(36) PRIMARY KEY,
    ├─ PK: 独立主键，每条历史有自己的ID
    │
    payment_id VARCHAR(36) NOT NULL,
    ├─ FK: 外键关联到 payments.id
    │ INDEX idx_payment_id: 查询某支付的全部历史
    │      SELECT * FROM payment_status_history WHERE payment_id = '...'
    │
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    ├─ from_status 为 NULL 表示初始创建记录
    │
    triggered_by VARCHAR(50) NOT NULL,
    ├─ USER or SYSTEM，区分谁发起的操作
    │
    changed_at DATETIME NOT NULL,
    ├─ INDEX idx_payment_changed_at: 复合索引
    │ (payment_id, changed_at)
    │ ├─ 支持高效的范围查询
    │ │  SELECT * FROM history WHERE payment_id = '...' AND changed_at BETWEEN ? AND ?
    │ ├─ 支持高效的有序扫描
    │ │  SELECT * FROM history WHERE payment_id = '...' ORDER BY changed_at ASC
    │ └─ 作为总是追加、从不修改的审计表，这个索引很高效
    │
    error_code VARCHAR(50),
    notes VARCHAR(255)
    └─ 失败时填充信息


索引使用示例：

// 1. 按状态查询所有支付（使用 idx_status）
SELECT * FROM payments WHERE status = 'COMPLETED' ORDER BY created_at DESC;
↓ Index Range Scan on idx_status

// 2. 查询某支付的全部历史（使用 idx_payment_id）
SELECT * FROM payment_status_history WHERE payment_id = 'pay-abc' ORDER BY changed_at ASC;
↓ Index Range Scan on idx_payment_id, then Sort by changed_at
  或 使用 idx_payment_changed_at 复合索引直接有序返回

// 3. 幂等性检查（使用 UNIQUE 约束）
SELECT * FROM payments WHERE idempotency_key = 'req-xxx';
↓ Unique Index Scan (最快)

// 4. 精确查询支付（使用 PK）
SELECT * FROM payments WHERE id = 'pay-abc';
↓ Primary Key Lookup (最快)
```

---

## 9. 角色间的数据流交接

```
用户                     前端                    Role A             Role B/C
 │                        │                       │                  │
 │ 创建支付                │                       │                  │
 └──────────────────→ 填表单─────→ POST /api/payments              │
                            │                ↓                      │
                            │         检查Header(Idempotency-Key)    │
                            │         反序列化DTO                  │
                            │         ↓                              │
                            │         调用 PaymentService            │
                            │         ↓─────────────→ 幂等检查 ← Role B
                            │         │              验证字段 ← Role B
                            │         │
                            │         Role D的输入点：
                            │         ├─ PaymentMapper.toEntity()
                            │         │  └─ 生成 Payment Entity
                            │         │
                            │         ├─ PaymentRepository.save()
                            │         │  └─ 存储到数据库
                            │         │
                            │         ├─ PaymentHistoryService.recordCreation()
                            │         │  └─ 记录 "CREATED" 历史
                            │         │
                            │         ├─ PaymentMapper.toPaymentResponse()
                            │         │  └─ 转换为 Response DTO
                            │         │
                            │         返回 201 Created ─── ─────────────→ 显示Payment ID
                            │         │
用户点"处理"              │
 │                        │         Role C 的输入点：
 └──────────────────→ GET /api/payments/{id} → 查询支付
                            └──────────→ PaymentRepository.findById()
                                         ↓ 获取 Payment Entity
                                         ↓ PaymentMapper.toPaymentResponse()
                                         └─ 返回详情
                            │
                            │         POST /api/payments/{id}/process
                            │         ↓ Role C:validatePayment()
                            │         ↓ Role C:sendPayment()
                            │         ↓ Role C:confirmPayment()
                            │         ├─ 调用 PaymentLifecycleService
                            │         │  ├─ markValidated()
                            │         │  ├─ markSent()
                            │         │  ├─ markCompleted()
                            │         │  └─ 每次都调用...
                            │         ├─ PaymentRepository.save()
                            │         │  └─ 更新 Payment status
                            │         ├─ PaymentHistoryService.recordTransition()
                            │         │  └─ 记录转换历史
                            │         │
                            └── ← ─── Response 返回 200 + 最终状态
                            
                            GET /api/payments/{id}/history
                            ↓ Role A: PaymentService.getPaymentHistory()
                            ↓ Role D: PaymentHistoryService.getHistory()
                            ↓        PaymentStatusHistoryRepository.find...()
                            ↓        QueryMySQL历史表
                            ↓        PaymentMapper.toHistoryResponse()[]
                            └── ← ─── 返回时间轴
```

---

## 10. 完整的创建→处理→查看流程（Role D视角）

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1️⃣  创建支付阶段                                                    │
│                                                                     │
│ PaymentMapper.toEntity()                                           │
│ └─ 输入: CreatePaymentRequest + key + fingerprint + now           │
│    输出: Payment entity {                                          │
│        id: UUID,                  ✨ 为空请求生成                 │
│        status: CREATED,           ✨ 强制为CREATED                │
│        idempotencyKey: key,       ✨ 存储key                     │
│        requestFingerprint: finger ✨ 存储指纹                     │
│        createdAt: now,                                            │
│        updatedAt: now             ✨ 相同的时间                   │
│    }                                                               │
│                                                                     │
│ PaymentRepository.save(entity)                                    │
│ └─ INSERT INTO payments (...)                                    │
│    idempotency_key UNIQUE 约束生效                               │
│                                                                     │
│ PaymentHistoryService.recordCreation(entity, now)                 │
│ └─ 创建 PaymentStatusHistory {                                    │
│        id: UUID,                                                  │
│        paymentId: entity.id,      ✨ FK关联                      │
│        fromStatus: null,          ✨ 初始为null                   │
│        toStatus: CREATED,                                         │
│        triggeredBy: USER,         ✨ 用户操作                     │
│        changedAt: now                                             │
│    }                                                               │
│    INSERT INTO payment_status_history (...)                      │
│                                                                     │
│ PaymentMapper.toPaymentResponse(entity)                           │
│ └─ 输出: {                                                        │
│        id: "pay-xxx",             ✨ 返回ID给用户               │
│        status: "CREATED",                                         │
│        createdAt: "2026-07-27T10:00:00Z"  ✨ ISO 8601格式       │
│        // ❌ 不返回 idempotencyKey, requestFingerprint          │
│    }                                                               │
│                                                                     │
│ ✅ 返回 201 Created                                               │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ 2️⃣  处理支付阶段（Role C参与）                                      │
│                                                                     │
│ PaymentRepository.findById("pay-xxx")                              │
│ └─ SELECT * FROM payments WHERE id = "pay-xxx"                  │
│    返回 Payment entity (status: CREATED)                         │
│                                                                     │
│ Role C: PaymentLifecycleService.markValidated(entity)            │
│ └─ entity.changeStatus(VALIDATED, now)                          │
│    └─ 更新 status = VALIDATED, updatedAt = now                 │
│    PaymentRepository.save(entity) ✨ UPDATE                      │
│    PaymentHistoryService.recordTransition(...)                   │
│    └─ 创建历史 {                                                  │
│        fromStatus: CREATED,       ✨ 原状态                      │
│        toStatus: VALIDATED,       ✨ 新状态                      │
│        triggeredBy: SYSTEM,       ✨ 系统操作                     │
│    }                                                               │
│                                                                     │
│ Role C: PaymentProcessingSimulator.sendPayment(entity)            │
│ └─ 模拟发送，返回 success                                        │
│                                                                     │
│ Role C: PaymentLifecycleService.markSent(entity)                  │
│ └─ entity.changeStatus(SENT, now)                               │
│    PaymentRepository.save(entity)                                 │
│    PaymentHistoryService.recordTransition(...)                   │
│                                                                     │
│ Role C: PaymentProcessingSimulator.confirmPayment(entity)         │
│ └─ 模拟确认，返回 success                                        │
│                                                                     │
│ Role C: PaymentLifecycleService.markCompleted(entity)             │
│ └─ entity.changeStatus(COMPLETED, now)                          │
│    entity.clearFailure() ✨ 清理错误字段                         │
│    PaymentRepository.save(entity)                                 │
│    PaymentHistoryService.recordTransition(...)                   │
│    └─ 创建历史 {                                                  │
│        fromStatus: SENT,                                         │
│        toStatus: COMPLETED,      ✨ 终态                         │
│        triggeredBy: SYSTEM,                                       │
│    }                                                               │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ 3️⃣  查询支付详情阶段                                               │
│                                                                     │
│ PaymentRepository.findById("pay-xxx")                              │
│ └─ SELECT * FROM payments WHERE id = "pay-xxx"                  │
│    返回 Payment entity (status: COMPLETED)                       │
│                                                                     │
│ PaymentMapper.toPaymentResponse(entity)                           │
│ └─ 输出: {                                                        │
│        id: "pay-xxx",                                             │
│        sourceAccount: "ACC001",                                   │
│        destinationAccount: "ACC002",                              │
│        status: "COMPLETED",       ✨ 最终状态                    │
│        createdAt: "2026-07-27T10:00:00Z",                       │
│        updatedAt: "2026-07-27T10:05:00Z"  ✨ 最后更新时间       │
│    }                                                               │
│                                                                     │
│ ✅ 返回 200 OK                                                    │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ 4️⃣  查询历史时间线阶段                                             │
│                                                                     │
│ PaymentStatusHistoryRepository                                     │
│ .findAllByPaymentIdOrderByChangedAtAsc("pay-xxx")                 │
│ └─ SELECT * FROM payment_status_history                          │
│    WHERE payment_id = "pay-xxx"                                  │
│    ORDER BY changed_at ASC  ✨ 时间升序                         │
│                                                                     │
│    返回 4 条历史记录（时间轴顺序）：                              │
│    [                                                               │
│        {id: his-1, fromStatus: null,      toStatus: CREATED  },   │
│        {id: his-2, fromStatus: CREATED,   toStatus: VALIDATED},   │
│        {id: his-3, fromStatus: VALIDATED, toStatus: SENT     },   │
│        {id: his-4, fromStatus: SENT,      toStatus: COMPLETED}   │
│    ]                                                               │
│                                                                     │
│ PaymentMapper.toHistoryResponse()（循环4次）                      │
│ └─ 输出: [                                                        │
│        {fromStatus: null,      toStatus: CREATED,   changedAt: ...},
│        {fromStatus: CREATED,   toStatus: VALIDATED, changedAt: ...},
│        {fromStatus: VALIDATED, toStatus: SENT,      changedAt: ...},
│        {fromStatus: SENT,      toStatus: COMPLETED, changedAt: ...}
│    ]                                                               │
│                                                                     │
│ ✅ 返回 200 OK + 历史数组                                          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Summary

**角色D (持久化与映射)** 在整个流程中的关键职责：

1. **Entity 定义**：Payment 和 PaymentStatusHistory，映射数据库模式
2. **Repository 接口**：暴露必要的查询方法，让其他人调用
3. **Service 协作**：PaymentHistoryService 记录每次转换
4. **DTO 转换**：PaymentMapper 在 Entity 和 API 之间兼容化
5. **数据一致性**：确保状态变化和历史记录同时提交（事务）

整体的**关键约束**：
- ✅ BigDecimal 精确金额
- ✅ Instant UTC 时间
- ✅ 枚举强类型
- ✅ 幂等键唯一 + 指纹判断
- ✅ 历史表不可变
- ✅ API 不泄露内部字段


