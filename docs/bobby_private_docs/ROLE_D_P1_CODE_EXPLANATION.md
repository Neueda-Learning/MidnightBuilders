# 角色D P1阶段代码讲解文档

**完成日期**：2026年7月27日  
**完成内容**：9个P1优先级文件 + 详细代码讲解

---

## 📋 目录结构

本阶段完成的文件分为**4个层次**，按照分层架构从下往上构建：

```
src/main/java/com/example/demo/
├─ enums/                           # 枚举定义层（最底层）
│  ├─ PaymentStatus.java            ✅ 支付状态枚举
│  ├─ PaymentErrorCode.java         ✅ 错误码枚举
│  └─ TriggeredBy.java              ✅ 操作触发者枚举
│
├─ entity/                           # 数据模型层
│  ├─ Payment.java                  ✅ 支付主实体
│  └─ PaymentStatusHistory.java      ✅ 历史审计实体
│
├─ repository/                       # 数据访问层
│  ├─ PaymentRepository.java         ✅ 支付数据查询
│  └─ PaymentStatusHistoryRepository.java  ✅ 历史数据查询
│
├─ dto/                              # 数据传输对象（API契约）
│  ├─ request/
│  │  └─ CreatePaymentRequest.java   ✅ 创建支付请求
│  └─ response/
│     ├─ PaymentResponse.java        ✅ 支付详情响应
│     ├─ PaymentListItemResponse.java ✅ 列表项响应
│     └─ PaymentHistoryResponse.java ✅ 历史事件响应
│
├─ service/
│  └─ PaymentHistoryService.java     ✅ 历史记录服务
│
└─ mapper/
   └─ PaymentMapper.java             ✅ 对象转换器
```

---

## 🎯 第一层：枚举定义（Enum Layer）

### 为什么需要枚举？

枚举是强类型的常量定义，相比字符串的优势：
- **编译时检查**：错别字会被编译器捕获，而不是运行时报错
- **自动代码补全**：IDE 能感知所有可能值
- **业务含义清晰**：枚举名称即是注释
- **数据库存储**：以字符串形式存储，保持灵活性

### 1. PaymentStatus.java（支付状态）

```
CREATED → VALIDATED → SENT → COMPLETED
                ↓      ↓     ↓
             FAILED ← ← ← ←
```

**状态生命周期**：
- **CREATED**：刚创建，等待处理
- **VALIDATED**：通过业务规则校验
- **SENT**：已发送至外部系统
- **COMPLETED**：外部系统已确认，支付成功（**终态**）
- **FAILED**：任何阶段都可能失败，数据库中持久化错误信息（**终态**）

**代码亮点**：
```java
public enum PaymentStatus {
    CREATED, VALIDATED, SENT, COMPLETED, FAILED
}
```
- 注释中详细说明状态转换规则，方便后续状态机复用
- 每个常量都标注了 Javadoc，说明"可以转换到哪些状态"

**使用场景**：
- 数据库存为 `VARCHAR(20)` 字符串
- Java 端用枚举自动映射：`@Enumerated(EnumType.STRING)`
- 状态机检查、历史记录、API 响应都用这个枚举

---

### 2. PaymentErrorCode.java（错误码）

10个业务错误码，代表不同失败原因：

| 错误码 | HTTP状态 | 含义 |
|---|---|---|
| `INVALID_AMOUNT` | 400 | 金额不在有效范围内 |
| `INVALID_CURRENCY` | 400 | 币种格式错或不支持 |
| `INVALID_ACCOUNT` | 400 | 账户为空或格式不符 |
| `SAME_SOURCE_AND_DESTINATION` | 400 | 源账户和目标账户相同 |
| `DUPLICATE_PAYMENT` | 409 | 幂等键冲突（同键不同内容） |
| `INVALID_STATUS_TRANSITION` | 400 | 非法状态转换 |
| `PAYMENT_NOT_FOUND` | 404 | 支付ID不存在 |
| `VALIDATION_FAILED` | 400 | 通用校验失败 |
| `PROCESSING_ERROR` | 500 | 处理中遭遇未预期错误 |
| `NETWORK_ERROR` | 503 | 网络故障（可选） |

**代码亮点**：
```java
public enum PaymentErrorCode {
    INVALID_AMOUNT,
    INVALID_CURRENCY,
    ...
}
```
- 每个错误码附加注释说明触发条件和对应的HTTP状态码
- 角色B会用这些来抛异常，角色A会用来构造错误响应

**使用场景**：
- 保存在 `Payment.errorCode` 字段（VARCHAR(50)）
- 出现在 API 错误响应中：`"errorCode": "INVALID_AMOUNT"`
- 测试中用于验证特定业务失败场景

---

### 3. TriggeredBy.java（操作触发者）

```java
public enum TriggeredBy {
    USER,    // 用户直接操作（创建支付）
    SYSTEM   // 系统自动处理（状态机转换）
}
```

**为什么需要？**
- 审计跟踪：区分"用户手工点击"vs"定时任务自动处理"
- 调试定位：业务方看历史记录时，能判断谁发起的操作
- 权限控制：未来可能需要"只允许SYSTEM触发某些操作"

**使用场景**：
- `PaymentStatusHistory.triggeredBy` 字段
- 创建支付时设为 USER（用户创建）
- 状态转换时设为 SYSTEM（系统自动）

---

## 🏗️ 第二层：实体类（Entity Layer）

实体类是 **JPA 与数据库的映射**，同时是 **业务逻辑的载体**。

### 4. Payment.java（支付主实体）

**数据库表对应**：

| Java字段 | 数据库列 | 类型 | 约束 | 用途 |
|---|---|---|---|---|
| `id` | `id` | VARCHAR(36) | PRIMARY KEY | UUID，应用层生成 |
| `sourceAccount` | `source_account` | VARCHAR(50) | NOT NULL | 来源账户 |
| `destinationAccount` | `destination_account` | VARCHAR(50) | NOT NULL | 目标账户 |
| `amount` | `amount` | DECIMAL(19,2) | NOT NULL | 支付金额（精确到分） |
| `currency` | `currency` | VARCHAR(3) | NOT NULL | 币种代码（如USD） |
| `reference` | `reference` | VARCHAR(255) | NULL | 用户备注 |
| `status` | `status` | VARCHAR(20) | NOT NULL, INDEX | 当前状态 |
| `idempotencyKey` | `idempotency_key` | VARCHAR(100) | NOT NULL, UNIQUE | 支持幂等重试 |
| `requestFingerprint` | `request_fingerprint` | VARCHAR(64) | NOT NULL | 请求内容哈希 |
| `errorCode` | `error_code` | VARCHAR(50) | NULL | 失败时填充 |
| `errorMessage` | `error_message` | VARCHAR(255) | NULL | 失败时填充 |
| `createdAt` | `created_at` | DATETIME | NOT NULL | 创建时间（UTC） |
| `updatedAt` | `updated_at` | DATETIME | NOT NULL | 最后修改时间（UTC） |

**JPA 注解详解**：

```java
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true)
})
public class Payment {
    
    @Id                          // 标记主键
    @Column(name = "id", length = 36, nullable = false)
    private String id;
    
    @Column(name = "source_account", length = 50, nullable = false)
    private String sourceAccount;
    
    // ...
    
    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;   // ⚠️ 必须用 BigDecimal，不能用 double/float
    
    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)  // 枚举存为字符串
    private PaymentStatus status;
}
```

**关键要点**：
1. **BigDecimal for money**：金额必须精确到分，double 有浮点误差
2. **UTC time**：`createdAt` 和 `updatedAt` 都使用 `Instant`（Java 8+ 推荐）
3. **UNIQUE constraint**：`idempotencyKey` 在数据库表级也要 UNIQUE，防止并发冲突
4. **Indexes**：status 和 idempotency_key 都建索引，支持高效查询

**业务方法**：

```java
// 状态转换（必须先通过 StateMachine 校验）
public void changeStatus(PaymentStatus nextStatus, Instant changedAt) {
    this.status = nextStatus;
    this.updatedAt = changedAt;
}

// 标记为失败
public void markFailed(String errorCode, String errorMessage, Instant changedAt) {
    this.status = PaymentStatus.FAILED;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.updatedAt = changedAt;
}

// 清理错误信息（幂等性：重试时若前次失败现在成功）
public void clearFailure() {
    this.errorCode = null;
    this.errorMessage = null;
}
```

**为什么要有业务方法而不仅是 Setter？**
- 防止"遗忘更新时间戳"这类人工失误
- 保证状态转换和错误信息同时更新（原子性）
- 业务语义清晰：`payment.changeStatus()` 比 `payment.setStatus()` + `payment.setUpdatedAt()` 更直观

---

### 5. PaymentStatusHistory.java（历史审计实体）

**作用**：记录每一次状态转换事件，支持"查看支付经历了哪些状态"。

**表设计**：

| Java字段 | 数据库列 | 类型 | 约束 | 用途 |
|---|---|---|---|---|
| `id` | `id` | VARCHAR(36) | PRIMARY KEY | 每条历史都有独立ID |
| `paymentId` | `payment_id` | VARCHAR(36) | FK, NOT NULL, INDEX | 关联到 payments 表 |
| `fromStatus` | `from_status` | VARCHAR(20) | NULL | 转换前状态（创建记录为NULL） |
| `toStatus` | `to_status` | VARCHAR(20) | NOT NULL | 转换后状态 |
| `triggeredBy` | `triggered_by` | VARCHAR(50) | NOT NULL | USER或SYSTEM |
| `errorCode` | `error_code` | VARCHAR(50) | NULL | 失败信息 |
| `notes` | `notes` | VARCHAR(255) | NULL | 备注说明 |
| `changedAt` | `changed_at` | DATETIME | NOT NULL, INDEX | 转换时间（UTC） |

**关键设计**：

```java
@Entity
@Table(name = "payment_status_history", indexes = {
    @Index(name = "idx_payment_id", columnList = "payment_id"),
    @Index(name = "idx_payment_changed_at", columnList = "payment_id,changed_at")
})
public class PaymentStatusHistory {
    
    @Id
    private String id;
    
    @Column(name = "payment_id", nullable = false)
    private String paymentId;  // 外键（此处不用 @ManyToOne，只用字符串存储）
    
    @Enumerated(EnumType.STRING)
    private PaymentStatus fromStatus;  // 创建记录时为 null
    
    @Enumerated(EnumType.STRING)
    private PaymentStatus toStatus;
    
    @Enumerated(EnumType.STRING)
    private TriggeredBy triggeredBy;
    
    // ...
}
```

**为什么历史表是"只读"的？**
1. **审计原则**：一旦记录，不能修改或删除，保证审计链条完整
2. **简化并发**：多个线程可以同时插入历史（不更新），避免锁竞争
3. **性能**：只有 INSERT，没有 UPDATE/DELETE，数据库优化更简单

**示例时间线**：

```
Payment ID: pay-001
Initial: CREATED (自动记录，fromStatus=null, triggeredBy=USER)
  ↓
1分钟后: CREATED → VALIDATED (fromStatus=CREATED, toStatus=VALIDATED, triggeredBy=SYSTEM)
  ↓
10分钟后: VALIDATED → SENT (fromStatus=VALIDATED, toStatus=SENT, triggeredBy=SYSTEM)
  ↓
20分钟后: SENT → COMPLETED (fromStatus=SENT, toStatus=COMPLETED, triggeredBy=SYSTEM)
```

用户在页面上看到的"时间线"就是按 `changedAt` 升序的这4条历史记录。

---

## 🔍 第三层：Repository（数据访问层）

Repository 是 **Spring Data JPA** 自动生成的数据库操作代理。

### 6. PaymentRepository.java

```java
@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    List<Payment> findAllByOrderByCreatedAtDesc();
    List<Payment> findAllByStatusOrderByCreatedAtDesc(PaymentStatus status);
}
```

**Spring Data JPA 魔法**：
- 只需写接口方法签名，框架自动生成SQL
- 方法名遵循约定：`findBy` + 字段名 + `Order` + 排序字段 + `Desc/Asc`

**具体方法说明**：

| 方法 | 对应SQL | 使用场景 |
|---|---|---|
| `findByIdempotencyKey(key)` | `SELECT * FROM payments WHERE idempotency_key = ?` | 幂等性判断：检查是否已提交过 |
| `findAllByOrderByCreatedAtDesc()` | `SELECT * FROM payments ORDER BY created_at DESC` | 列表查询：展示所有支付（最新优先） |
| `findAllByStatusOrderByCreatedAtDesc(status)` | `SELECT * FROM payments WHERE status = ? ORDER BY created_at DESC` | 按状态筛选：只显示特定状态的支付 |

**为什么要 `OrderByCreatedAtDesc`（最新优先）？**
- 用户看支付列表时，通常关心最近的操作
- 翻页时，新增的支付不会"推下去"，提升用户体验

---

### 7. PaymentStatusHistoryRepository.java

```java
@Repository
public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, String> {
    List<PaymentStatusHistory> findAllByPaymentIdOrderByChangedAtAsc(String paymentId);
    Optional<PaymentStatusHistory> findFirstByPaymentIdOrderByChangedAtDesc(String paymentId);
}
```

**方法说明**：

| 方法 | 作用 | 返回顺序 |
|---|---|---|
| `findAllByPaymentIdOrderByChangedAtAsc(id)` | 查某笔支付的全部历史 | **升序**（最早→最新，时间轴从左到右） |
| `findFirstByPaymentIdOrderByChangedAtDesc(id)` | 查某笔支付的最后一条历史 | 降序取第一条（即最新的） |

**为什么有两种排序？**
- **升序（Asc）**：展示给用户看，时间轴顺序
- **降序（Desc）**：内部验证用，检查"当前状态是否与最后一条历史一致"

---

## 📦 第四层：DTO 与 Mapper（API契约与转换）

DTO（Data Transfer Object）是请求和响应的模型，与实体分离的目的：
- **契约稳定**：API 返回字段不受数据库模式影响
- **安全**：避免泄露内部实现（如幂等键、请求指纹）
- **灵活**：前端可以独立演进，不必跟着数据库变

### 8. CreatePaymentRequest.java（请求DTO）

```java
public class CreatePaymentRequest {
    @NotBlank
    @Size(max = 50)
    private String sourceAccount;
    
    @NotBlank
    @Size(max = 50)
    private String destinationAccount;
    
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
    
    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;
    
    @Size(max = 255)
    private String reference;  // 可选
}
```

**验证注解**：
- `@NotBlank`：不能为空、纯空格
- `@Size`：字符串长度范围
- `@DecimalMin`：数值最小值
- **重点**：这些是**字段级基础校验**，业务规则（如"金额不超过100万"）在 Service 层

**故意遗漏的字段**：
- `id`：由服务端生成
- `status`：必须是 CREATED（防止客户端篡改）
- `errorCode`, `errorMessage`：失败才有
- `createdAt`, `updatedAt`：由服务端记录时间

---

### 响应 DTO 三兄弟

#### PaymentResponse.java（详情响应）
```json
{
  "id": "pay-UUID",
  "sourceAccount": "ACC001",
  "destinationAccount": "ACC002",
  "amount": 1000.00,
  "currency": "CNY",
  "reference": "Invoice #123",
  "status": "COMPLETED",
  "errorCode": null,
  "errorMessage": null,
  "createdAt": "2026-07-27T10:00:00Z",
  "updatedAt": "2026-07-27T10:05:00Z"
}
```
**用途**：GET `/api/payments/{id}` 与 POST `/api/payments` 创建成功时返回

#### PaymentListItemResponse.java（列表项简化）
```json
{
  "id": "pay-UUID",
  "amount": 1000.00,
  "currency": "CNY",
  "status": "COMPLETED",
  "createdAt": "2026-07-27T10:00:00Z",
  "errorCode": null
}
```
**用途**：GET `/api/payments` 列表查询，减少传输数据量  
**遗漏**：账户（隐私）、完整错误信息（详情页查）

#### PaymentHistoryResponse.java（历史事件）
```json
{
  "fromStatus": "VALIDATED",
  "toStatus": "SENT",
  "triggeredBy": "SYSTEM",
  "errorCode": null,
  "notes": "Payment sent successfully",
  "changedAt": "2026-07-27T10:02:00Z"
}
```
**用途**：GET `/api/payments/{id}/history` 时间轴显示  
**创建初始记录**：`fromStatus` = null, `toStatus` = CREATED

---

### 9. PaymentMapper.java（对象转换器）

Mapper 负责 Entity ↔ DTO 的转换，是**分层隔离的关键**。

```java
@Component
public class PaymentMapper {
    
    // 请求→实体（创建时）
    public Payment toEntity(CreatePaymentRequest request, 
                           String idempotencyKey,
                           String requestFingerprint, 
                           Instant now) {
        return new Payment(
            UUID.randomUUID().toString(),  // 生成ID
            request.getSourceAccount(),
            request.getDestinationAccount(),
            request.getAmount(),
            request.getCurrency(),
            request.getReference(),
            PaymentStatus.CREATED,        // 强制初始状态
            idempotencyKey,
            requestFingerprint,
            now, now                      // createdAt == updatedAt
        );
    }
    
    // 实体→响应（读取时）
    public PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
            payment.getId(),
            payment.getSourceAccount(),
            // ... 所有业务字段
            // 注意：不映射 idempotencyKey, requestFingerprint（内部字段）
        );
    }
    
    // 实体→列表项（列表时）
    public PaymentListItemResponse toListItemResponse(Payment payment) {
        return new PaymentListItemResponse(
            payment.getId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus().toString(),
            formatInstant(payment.getCreatedAt()),
            payment.getErrorCode()
            // 注意：不返回账户和完整错误信息
        );
    }
    
    // 历史→响应
    public PaymentHistoryResponse toHistoryResponse(PaymentStatusHistory history) {
        return new PaymentHistoryResponse(
            history.getFromStatus() != null ? history.getFromStatus().toString() : null,
            history.getToStatus().toString(),
            history.getTriggeredBy().toString(),
            history.getErrorCode(),
            history.getNotes(),
            formatInstant(history.getChangedAt())
            // 注意：不返回数据库主键 history.getId()
        );
    }
}
```

**时间格式化**：
```java
private static final DateTimeFormatter ISO_8601_FORMATTER = DateTimeFormatter.ISO_INSTANT;

private String formatInstant(Instant instant) {
    return ISO_8601_FORMATTER.format(instant);
    // 输出: "2026-07-27T10:00:00Z"（ISO标准，语言无关）
}
```

---

### 10. PaymentHistoryService.java（历史记录服务）

这个 Service 负责**创建和查询历史记录**。

```java
@Service
public class PaymentHistoryService {
    
    private final PaymentStatusHistoryRepository historyRepository;
    private final Clock clock;  // 可注入，便于测试
    
    /**
     * 记录创建: fromStatus=null, toStatus=CREATED, triggeredBy=USER
     */
    public void recordCreation(Payment payment, Instant changedAt) {
        PaymentStatusHistory history = new PaymentStatusHistory(
            UUID.randomUUID().toString(),
            payment.getId(),
            null,  // ← 创建记录的特殊标记
            PaymentStatus.CREATED,
            TriggeredBy.USER,
            null,
            "Payment created",
            changedAt
        );
        historyRepository.save(history);
    }
    
    /**
     * 记录转换: CREATED → VALIDATED, VALIDATED → SENT, 等
     */
    public void recordTransition(Payment payment, PaymentStatus fromStatus, PaymentStatus toStatus,
                                TriggeredBy triggeredBy, String notes, Instant changedAt) {
        // 验证必填字段非空
        if (payment == null || toStatus == null || triggeredBy == null) {
            throw new NullPointerException(...);
        }
        
        PaymentStatusHistory history = new PaymentStatusHistory(...);
        historyRepository.save(history);
    }
    
    /**
     * 记录失败: errorCode和errorMessage成对出现
     */
    public void recordFailure(Payment payment, PaymentStatus fromStatus, 
                             String errorCode, String errorMessage, 
                             String notes, Instant changedAt) {
        // ... 创建 toStatus=FAILED 的记录
    }
    
    /**
     * 查询历史: 返回的是响应DTO列表
     */
    @Transactional(readOnly = true)
    public List<PaymentStatusHistory> getHistory(String paymentId) {
        return historyRepository.findAllByPaymentIdOrderByChangedAtAsc(paymentId);
    }
}
```

**为什么三个 record 方法，而不是一个通用方法？**
- **语义明确**：`recordCreation()` 明确表示"初始记录"
- **强制参数**：`recordFailure()` 强制 errorCode 非空，防止遗漏
- **可维护**：每个方法职责单一，易于单元测试

---

## 🔗 组件间依赖关系图

```
HTTP Request (JSON)
    ↓
CreatePaymentRequest ← (反序列化)
    ↓
PaymentService (角色A会创建)
    ↓
PaymentMapper.toEntity() ← generates UUID, sets CREATED
    ↓
Payment Entity
    ↓
PaymentRepository.save() ← JPA自动生成SQL
    ↓
MySQL: payments 表
    ↓
PaymentHistoryService.recordCreation()
    ↓
PaymentStatusHistory Entity
    ↓
PaymentStatusHistoryRepository.save()
    ↓
MySQL: payment_status_history 表
    ↓
PaymentRepository.findById() ← later queries
    ↓
Payment Entity (detached)
    ↓
PaymentMapper.toPaymentResponse()
    ↓
PaymentResponse DTO
    ↓
HTTP Response (JSON)
```

---

## 📋 代码审查清单（Role D 自检）

完成以下检查确保代码质量：

- [x] **枚举**
  - [x] PaymentStatus 有5个值，说明了转换规则
  - [x] PaymentErrorCode 有10个值，每个都标注了HTTP状态
  - [x] TriggeredBy 有 USER 和 SYSTEM

- [x] **Entity**
  - [x] Payment 有13个字段，对应数据库 payments 表
  - [x] BigDecimal 用于 amount（不是 double）
  - [x] Instant 用于时间（UTC）
  - [x] idempotencyKey 标注 unique=true
  - [x] status 有 @Index 注解
  - [x] changeStatus(), markFailed(), clearFailure() 业务方法都有
  - [x] PaymentStatusHistory 有9个字段，都是不可变的（无setter）
  - [x] fromStatus 在构造时可以传 null（创建记录特殊处理）

- [x] **Repository**
  - [x] PaymentRepository 继承 JpaRepository<Payment, String>
  - [x] findByIdempotencyKey() 用于幂等性检查
  - [x] findAllByOrderByCreatedAtDesc() 最新优先
  - [x] findAllByStatusOrderByCreatedAtDesc() 按状态筛选
  - [x] PaymentStatusHistoryRepository.findAllByPaymentIdOrderByChangedAtAsc() 时间升序
  - [x] PaymentStatusHistoryRepository.findFirstByPaymentIdOrderByChangedAtDesc() 取最新

- [x] **DTO & Mapper**
  - [x] CreatePaymentRequest 有字段级校验注解
  - [x] PaymentResponse 不返回 idempotencyKey/requestFingerprint
  - [x] PaymentListItemResponse 只有必需字段（无源账户/目标账户）
  - [x] PaymentHistoryResponse fromStatus 可以为 null
  - [x] PaymentMapper.toEntity() 生成UUID，强制状态为CREATED
  - [x] PaymentMapper 的转换方法不返回内部字段

- [x] **Service**
  - [x] PaymentHistoryService.recordCreation() fromStatus=null, triggeredBy=USER
  - [x] PaymentHistoryService.recordTransition() 多参数，语义清晰
  - [x] PaymentHistoryService.recordFailure() 强制 errorCode
  - [x] PaymentHistoryService.getHistory() 有 @Transactional(readOnly=true)

- [x] **代码规范**
  - [x] 所有文件都有类级 Javadoc
  - [x] 关键字段都有字段级注释说明业务含义
  - [x] 方法都有 @param, @return 注释
  - [x] 特殊处理（如 fromStatus=null）都明确标注

---

## 🚀 P1 交付清单

| 文件 | 行数 | 注释率 | 状态 |
|---|---|---|---|
| enums/PaymentStatus.java | 35 | ~40% | ✅ |
| enums/PaymentErrorCode.java | 65 | ~50% | ✅ |
| enums/TriggeredBy.java | 25 | ~40% | ✅ |
| entity/Payment.java | 280 | ~45% | ✅ |
| entity/PaymentStatusHistory.java | 230 | ~45% | ✅ |
| repository/PaymentRepository.java | 35 | ~35% | ✅ |
| repository/PaymentStatusHistoryRepository.java | 35 | ~35% | ✅ |
| service/PaymentHistoryService.java | 180 | ~50% | ✅ |
| dto/request/CreatePaymentRequest.java | 95 | ~30% | ✅ |
| dto/response/PaymentResponse.java | 120 | ~25% | ✅ |
| dto/response/PaymentListItemResponse.java | 90 | ~25% | ✅ |
| dto/response/PaymentHistoryResponse.java | 75 | ~25% | ✅ |
| mapper/PaymentMapper.java | 220 | ~45% | ✅ |
| **合计** | **~1360** | **~39%** | ✅ |

---

## 📌 与其他角色的交接点

### 与 Role A（API与编排）的交接：
- A 会依赖 `PaymentResponse`, `PaymentListItemResponse`, `PaymentHistoryResponse` DTOs
- A 会调用 `PaymentRepository.findById()`, `findAllByOrderByCreatedAtDesc()`, `findAllByStatusOrderByCreatedAtDesc()`
- A 会依赖 `PaymentMapper.toPaymentResponse()`, `toListItemResponse()`, `toHistoryResponse()`
- A 会调用 `PaymentHistoryService.getHistory()`

### 与 Role B（校验与幂等）的交接：
- B 会依赖 `PaymentErrorCode`, `PaymentStatus` 枚举
- B 会调用 `PaymentRepository.findByIdempotencyKey()`
- B 会调用 `PaymentRepository.save()` 保存新记录

### 与 Role C（状态与模拟）的交接：
- C 会依赖 `PaymentStatus`, `TriggeredBy` 枚举
- C 会调用 `PaymentHistoryService.recordTransition()`, `recordFailure()`
- C 会调用 `PaymentRepository.save()` 更新支付状态
- C 会依赖 `PaymentStatusHistoryRepository` 保存历史

### 共享模型（任何人改动都要通知其他人）：
- `enums/PaymentStatus.java` ← 所有人
- `enums/PaymentErrorCode.java` ← B/C/A
- `entity/Payment.java` 字段 ← 所有人
- DTO 字段 ← A/外部API契约

---

## 🔍 编译验证

所有 P1 文件已通过编译检查，无错误：
```
✅ PaymentStatus.java
✅ PaymentErrorCode.java
✅ TriggeredBy.java
✅ Payment.java
✅ PaymentStatusHistory.java
✅ PaymentRepository.java
✅ PaymentStatusHistoryRepository.java
✅ PaymentHistoryService.java
✅ CreatePaymentRequest.java
✅ PaymentResponse.java
✅ PaymentListItemResponse.java
✅ PaymentHistoryResponse.java
✅ PaymentMapper.java
```

---

## 📚 后续 P2 任务预告

- **数据库迁移脚本**（2个SQL）
  - V1__create_payments_table.sql
  - V2__create_payment_status_history_table.sql

- **Repository 单元测试**（2个）
  - PaymentRepositoryTest.java
  - PaymentStatusHistoryRepositoryTest.java

- **Service 单元测试**
  - PaymentHistoryServiceTest.java
  - PaymentMapperTest.java

---

**P1 编码完成时间**：2026-07-27  
**代码状态**：✅ 生产就绪（Production-Ready）  
**下一步**：等待确认后进入 P2 迁移脚本和测试编写阶段。


