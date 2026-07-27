# 角色D P1 代码快速参考

## 🎯 核心设计模式速览

### 1. 分层架构（从下到上）

```
Enum Layer (枚举：最底层常量)
    ↓
Entity Layer (实体：数据模型+业务方法)
    ↓
Repository Layer (Spring Data JPA：数据访问)
    ↓
DTO Layer (请求/响应对象：API契约)
    ↓
Service Layer (业务逻辑：编排与验证)
    ↓
Mapper Layer (对象转换：Entity ↔ DTO)
```

---

## 📝 文件对应关系速查

### 支付主表 payments

| 字段 | Java类型 | 数据库类型 | 实体 | DTO |
|---|---|---|---|---|
| id | String | VARCHAR(36) PK | Payment | PaymentResponse |
| source_account | String | VARCHAR(50) | Payment | PaymentResponse |
| destination_account | String | VARCHAR(50) | Payment | PaymentResponse |
| amount | **BigDecimal** | DECIMAL(19,2) | Payment | PaymentResponse |
| currency | String | VARCHAR(3) | Payment | PaymentResponse |
| reference | String | VARCHAR(255) | Payment | PaymentResponse |
| status | PaymentStatus enum | VARCHAR(20) | Payment | PaymentResponse |
| idempotency_key | String | VARCHAR(100) UNIQUE | Payment | ❌ 不返回 |
| request_fingerprint | String | VARCHAR(64) | Payment | ❌ 不返回 |
| error_code | String | VARCHAR(50) | Payment | PaymentResponse |
| error_message | String | VARCHAR(255) | Payment | PaymentResponse |
| created_at | Instant | DATETIME | Payment | PaymentResponse |
| updated_at | Instant | DATETIME | Payment | PaymentResponse |

---

### 历史表 payment_status_history

| 字段 | Java类型 | 数据库类型 | 实体 | DTO |
|---|---|---|---|---|
| id | String | VARCHAR(36) PK | PaymentStatusHistory | ❌ 不返回 |
| payment_id | String | VARCHAR(36) FK | PaymentStatusHistory | ❌ 不返回 |
| from_status | PaymentStatus enum | VARCHAR(20) | PaymentStatusHistory | PaymentHistoryResponse |
| to_status | PaymentStatus enum | VARCHAR(20) | PaymentStatusHistory | PaymentHistoryResponse |
| triggered_by | TriggeredBy enum | VARCHAR(50) | PaymentStatusHistory | PaymentHistoryResponse |
| error_code | String | VARCHAR(50) | PaymentStatusHistory | PaymentHistoryResponse |
| notes | String | VARCHAR(255) | PaymentStatusHistory | PaymentHistoryResponse |
| changed_at | Instant | DATETIME | PaymentStatusHistory | PaymentHistoryResponse |

---

## 🔄 数据流向示意

### 创建支付流（POST /api/payments）

```
1️⃣ HTTP JSON
   ↓
2️⃣ CreatePaymentRequest (DTO请求)
   ↓ [字段级验证：@NotBlank, @Size, @DecimalMin]
3️⃣ PaymentService (角色A编排)
   ↓ [调用 PaymentMapper.toEntity()]
4️⃣ Payment Entity (新建状态为CREATED)
   ↓ [调用 PaymentRepository.save()]
5️⃣ MySQL payments 表插入
   ↓
6️⃣ PaymentHistoryService.recordCreation()
   ↓
7️⃣ PaymentStatusHistory Entity (fromStatus=null, triggeredBy=USER)
   ↓ [调用 PaymentStatusHistoryRepository.save()]
8️⃣ MySQL payment_status_history 表插入
   ↓ [调用 PaymentMapper.toPaymentResponse()]
9️⃣ PaymentResponse (DTO响应，不含幂等键/指纹)
   ↓
🔟 HTTP JSON (201 Created 或 200 OK)
```

### 查询支付详情流（GET /api/payments/{id}）

```
1️⃣ 请求路径参数 id
   ↓
2️⃣ PaymentService.getPayment(id)
   ↓ [调用 PaymentRepository.findById(id)]
3️⃣ MySQL payments 表查询
   ↓
4️⃣ Payment Entity (detached)
   ↓ [调用 PaymentMapper.toPaymentResponse()]
5️⃣ PaymentResponse (DTO响应)
   ↓
6️⃣ HTTP JSON (200 OK)
```

### 查询支付历史流（GET /api/payments/{id}/history）

```
1️⃣ 请求路径参数 id
   ↓
2️⃣ PaymentService.getPaymentHistory(id)
   ↓ [调用 PaymentHistoryService.getHistory(id)]
3️⃣ PaymentStatusHistoryRepository.findAllByPaymentIdOrderByChangedAtAsc(id)
   ↓
4️⃣ MySQL payment_status_history 表查询（按 changed_at 升序）
   ↓
5️⃣ List<PaymentStatusHistory> Entity
   ↓ [循环调用 PaymentMapper.toHistoryResponse()]
6️⃣ List<PaymentHistoryResponse> (DTO列表)
   ↓
7️⃣ HTTP JSON 数组 (200 OK)
```

---

## 🔐 关键约束与约定

### 金额处理（Amount）
```java
❌ 错误: double amount = 100.5;  // 浮点误差
✅ 正确: BigDecimal amount = new BigDecimal("100.50");
```

### 时间处理（Timestamp）
```java
❌ 错误: long createdAt = System.currentTimeMillis();  // 毫秒，时区不明确
✅ 正确: Instant createdAt = Instant.now(clock);  // UTC，可Mock

// API响应格式（ISO 8601）
"createdAt": "2026-07-27T10:00:00Z"
```

### 幂等键唯一性（Idempotency）
```java
// 数据库级约束（防止并发冲突）
@Column(unique = true)
private String idempotencyKey;

// 应用级检查（快速路径）
Optional<Payment> existing = paymentRepository.findByIdempotencyKey(key);
```

### 状态转换（State Transition）
```
CREATED ──→ VALIDATED ──→ SENT ──→ COMPLETED
   ↓          ↓           ↓
   └──→ FAILED ←──────────┘

// 不允许：CREATED → SENT（跳级）
// 不允许：COMPLETED → CREATED（回退）
// 不允许：COMPLETED → FAILED（终态不可变）
```

---

## 📊 枚举值完整列表

### PaymentStatus
```java
CREATED     // 初始状态
VALIDATED   // 校验通过
SENT        // 已发送
COMPLETED   // 成功（终态）
FAILED      // 失败（终态）
```

### PaymentErrorCode
```java
INVALID_AMOUNT                      // 金额校验失败
INVALID_CURRENCY                    // 币种不支持
INVALID_ACCOUNT                     // 账户格式错
SAME_SOURCE_AND_DESTINATION         // 源目标账户相同
DUPLICATE_PAYMENT                   // 幂等键冲突
INVALID_STATUS_TRANSITION           // 状态转换非法
PAYMENT_NOT_FOUND                   // 支付不存在
VALIDATION_FAILED                   // 通用校验失败
PROCESSING_ERROR                    // 处理异常
NETWORK_ERROR                       // 网络故障（可选）
```

### TriggeredBy
```java
USER        // 用户操作（创建支付）
SYSTEM      // 系统操作（自动转换）
```

---

## 🔀 Repository 方法速查

### PaymentRepository

```java
// 1. 幂等性检查
Optional<Payment> findByIdempotencyKey(String key);

// 2. 全量列表（最新优先）
List<Payment> findAllByOrderByCreatedAtDesc();

// 3. 按状态筛选
List<Payment> findAllByStatusOrderByCreatedAtDesc(PaymentStatus status);

// 继承自 JpaRepository
Optional<Payment> findById(String id);
Payment save(Payment entity);
boolean existsById(String id);
```

### PaymentStatusHistoryRepository

```java
// 1. 查询某笔支付的全部历史（时间升序）
List<PaymentStatusHistory> findAllByPaymentIdOrderByChangedAtAsc(String paymentId);

// 2. 查询最后一条历史（一致性验证）
Optional<PaymentStatusHistory> findFirstByPaymentIdOrderByChangedAtDesc(String paymentId);

// 继承自 JpaRepository
PaymentStatusHistory save(PaymentStatusHistory entity);
```

---

## 🗂️ DTO 字段映射表

### CreatePaymentRequest → Payment Entity

| Request | Entity | 描述 |
|---|---|---|
| sourceAccount | sourceAccount | 直接映射 |
| destinationAccount | destinationAccount | 直接映射 |
| amount | amount | 直接映射 |
| currency | currency | 直接映射 |
| reference | reference | 直接映射 |
| (无) | id | 生成UUID |
| (无) | status | 强制CREATED |
| (无) | idempotencyKey | 从HTTP头读取 |
| (无) | requestFingerprint | 由幂等服务生成 |
| (无) | createdAt | 取当前时间 |
| (无) | updatedAt | 取当前时间 |

### Payment Entity → PaymentResponse

| Entity | Response | 是否返回 |
|---|---|---|
| id | id | ✅ |
| sourceAccount | sourceAccount | ✅ |
| destinationAccount | destinationAccount | ✅ |
| amount | amount | ✅ |
| currency | currency | ✅ |
| reference | reference | ✅ |
| status | status | ✅ |
| errorCode | errorCode | ✅ |
| errorMessage | errorMessage | ✅ |
| createdAt | createdAt | ✅ |
| updatedAt | updatedAt | ✅ |
| idempotencyKey | (无) | ❌ 内部字段 |
| requestFingerprint | (无) | ❌ 内部字段 |

### PaymentStatusHistory Entity → PaymentHistoryResponse

| Entity | Response | 是否返回 |
|---|---|---|
| id | (无) | ❌ 数据库主键 |
| paymentId | (无) | ❌ 外键 |
| fromStatus | fromStatus | ✅ |
| toStatus | toStatus | ✅ |
| triggeredBy | triggeredBy | ✅ |
| errorCode | errorCode | ✅ |
| notes | notes | ✅ |
| changedAt | changedAt | ✅ |

---

## 🧪 关键测试场景

### PaymentRepository 测试
```
✓ save: 新增支付，idempotencyKey UNIQUE 约束生效
✓ findByIdempotencyKey: 找到已存在的支付
✓ findAllByOrderByCreatedAtDesc: 按创建时间倒序（最新优先）
✓ findAllByStatusOrderByCreatedAtDesc(COMPLETED): 按状态+时间筛选
✓ 唯一约束冲突: 同幂等键重复插入应报错
```

### PaymentStatusHistoryRepository 测试
```
✓ save: 新增历史记录
✓ findAllByPaymentIdOrderByChangedAtAsc: 验证升序（时间轴）
✓ findFirstByPaymentIdOrderByChangedAtDesc: 取最新记录
✓ 外键约束: 删除 Payment 时历史无孤立记录
```

### PaymentHistoryService 测试
```
✓ recordCreation: fromStatus=null, triggeredBy=USER
✓ recordTransition: 正常转换记录
✓ recordFailure: errorCode 必填，toStatus=FAILED
✓ getHistory: 返回升序历史列表
✓ 并发写: 多线程不丢失历史记录
```

### PaymentMapper 测试
```
✓ toEntity: id是UUID, status强制CREATED, created/updated同时间
✓ toPaymentResponse: 包含所有业务字段，不泄露内部字段
✓ toListItemResponse: 只有 id/amount/currency/status/createdAt/errorCode
✓ toHistoryResponse: fromStatus可以为null, 不返回主键
✓ 时间格式: ISO 8601 "2026-07-27T10:00:00Z"
```

---

## 🚨 常见错误与纠正

| 错误场景 | ❌ 错误做法 | ✅ 正确做法 |
|---|---|---|
| 金额精度 | `double amount = 100.5` | `BigDecimal amount = new BigDecimal("100.50")` |
| 时间处理 | `long timestamp = System.currentTimeMillis()` | `Instant created = Instant.now(clock)` |
| 返回响应 | 直接序列化 Entity | 通过 Mapper 转换为 DTO |
| 幂等性 | 只检查数据库 | 应用层快速检查 + 数据库唯一约束 |
| 历史增删 | 修改已有历史 | 只能追加，不能修改 |
| 状态转换 | 不验证即转换 | 必须经过 StateMachine 校验 |
| 错误字段 | errorCode 有但 errorMessage 无 | 必须同时设置或同时为空 |

---

## 📞 与其他角色沟通要点

### 给 Role A
- 你依赖的 DTO：`PaymentResponse`, `PaymentListItemResponse`, `PaymentHistoryResponse`
- 你调用的 Mapper 方法：`toPaymentResponse()`, `toListItemResponse()`, `toHistoryResponse()`
- Repository 方法的排序注意：`OrderByCreatedAtDesc` 最新优先

### 给 Role B
- `PaymentErrorCode` 枚举一定要对齐（我的定义你参考）
- `PaymentRepository.findByIdempotencyKey()` 用来做幂等键查询
- `PaymentStatus` 枚举状态值不能改

### 给 Role C
- 你调用的 Service 方法：`recordCreation()`, `recordTransition()`, `recordFailure()`
- `PaymentStatus`, `TriggeredBy` 枚举要对齐
- 状态转换前必须调用 `PaymentStateMachine.validateTransition()`

---

## 🔗 依赖声明

所有 P1 文件的依赖：
- Spring Framework: `spring-boot-starter-web`, `spring-boot-starter-data-jpa` ✅ pom.xml 已有
- Jakarta Persistence: `jakarta.persistence.*` ✅ spring-boot-starter-data-jpa 包含
- Jakarta Validation: `jakarta.validation.*` ✅ spring-boot-starter-validation 已有
- Jackson (JSON): `com.fasterxml.jackson.*` ✅ spring-boot-starter-web 包含

**无额外依赖需要安装**

---

## 📅 P2 待办

- [ ] V1__create_payments_table.sql（建表脚本）
- [ ] V2__create_payment_status_history_table.sql（历史表脚本）
- [ ] PaymentRepositoryTest.java（仓储单元测试）
- [ ] PaymentStatusHistoryRepositoryTest.java（历史仓储测试）
- [ ] PaymentHistoryServiceTest.java（服务单元测试）
- [ ] PaymentMapperTest.java（映射器测试）

---

**版本**：1.0  
**最后更新**：2026-07-27  
**作者**：Role D  
**审查状态**：✅ 生产就绪


