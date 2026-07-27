# 角色D P1 - 快速入门 (团队版)

**项目**：MidnightBuilders - Payment Processing System  
**交付**：角色D P1完整版本  
**状态**：✅ 生产就绪

---

## 📦 你需要的是什么？

### 👶 新手 (5分钟)
→ 看本文档的"核心概念"部分

### 📚 要写代码 (10分钟)
1. 看下面的"Repository方法速查"
2. 看下面的"关键接口"
3. 开始写代码

### 🏗️ 要理解架构 (30分钟)
→ 看本文档 + 查看代码的Javadoc

---

## 🎯 核心概念（30秒速记）

```
Payment Entity
  → 支付主表，13个字段
  → 对应 payments 数据库表

PaymentStatusHistory Entity
  → 历史审计表，8个字段
  → 记录每次状态转换

PaymentRepository
  → 查询Payment的接口
  → 已实现：findById / findByIdempotencyKey / findAll...

PaymentHistoryService
  → 记录历史的服务
  → 方法：recordCreation / recordTransition / recordFailure / getHistory

PaymentMapper
  → 转换对象的工具
  → Entity ↔ DTO 互转
```

---

## 📋 Repository 方法速查

### PaymentRepository
```java
// 查询单笔支付
Payment findById(String id)

// 幂等检查（找重复key）
Optional<Payment> findByIdempotencyKey(String key)

// 全量列表（最新优先）
List<Payment> findAllByOrderByCreatedAtDesc()

// 按状态筛选
List<Payment> findAllByStatusOrderByCreatedAtDesc(PaymentStatus status)

// 保存支付
Payment save(Payment entity)
```

### PaymentStatusHistoryRepository
```java
// 查询某支付的全部历史（时间升序）
List<PaymentStatusHistory> findAllByPaymentIdOrderByChangedAtAsc(String paymentId)

// 查最后一条历史
Optional<PaymentStatusHistory> findFirstByPaymentIdOrderByChangedAtDesc(String paymentId)
```

---

## 🔗 关键接口

### PaymentHistoryService
```java
// 记录创建
void recordCreation(Payment payment, Instant changedAt)

// 记录转换（如 CREATED → VALIDATED）
void recordTransition(Payment payment, PaymentStatus fromStatus, 
                     PaymentStatus toStatus, TriggeredBy triggeredBy, 
                     String notes, Instant changedAt)

// 记录失败
void recordFailure(Payment payment, PaymentStatus fromStatus, 
                  String errorCode, String errorMessage, 
                  String notes, Instant changedAt)

// 查询历史
List<PaymentStatusHistory> getHistory(String paymentId)
```

### PaymentMapper
```java
// DTO → Entity
Payment toEntity(CreatePaymentRequest request, String idempotencyKey, 
                String requestFingerprint, Instant now)

// Entity → 详情DTO
PaymentResponse toPaymentResponse(Payment payment)

// Entity → 列表项DTO
PaymentListItemResponse toListItemResponse(Payment payment)

// History Entity → DTO
PaymentHistoryResponse toHistoryResponse(PaymentStatusHistory history)
```

---

## 💡 关键设计点

### 1. 幂等性
```java
idempotencyKey      // 快速识别"是否提交过"
requestFingerprint  // 判断"是否同一内容"
// 两个结合：同key同内容→返回原支付  同key不同内容→409冲突
```

### 2. 状态转换
```java
CREATED → VALIDATED → SENT → COMPLETED
   ↓        ↓         ↓
   └────→ FAILED (任何阶段可失败，终态)
```

### 3. 审计历史
```java
// 创建记录：fromStatus = null, toStatus = CREATED, triggeredBy = USER
// 其他记录：fromStatus = 原状态, toStatus = 新状态, triggeredBy = SYSTEM
// 历史表不可修改，仅追加
```

### 4. 时间与金额
```java
Instant createdAt   // UTC时间，不是long
BigDecimal amount   // 精确到分，不是double
```

---

## 📂 文件对应关系

| 我的文件 | 类型 | 你要用 |
|---|---|---|
| PaymentRepository.java | Interface | @Autowired 注入 |
| PaymentStatusHistoryRepository.java | Interface | @Autowired 注入 |
| PaymentHistoryService.java | Service | @Autowired 注入 |
| PaymentMapper.java | Component | @Autowired 注入 |
| Payment.java | Entity | 字段查看 |
| PaymentStatusHistory.java | Entity | 字段查看 |
| PaymentStatus.java | Enum | 状态值参考 |
| PaymentErrorCode.java | Enum | 错误码参考 |

---

## 🚀 立即可以做

### 代码注入示例
```java
@Service
public class YourService {
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Autowired
    private PaymentMapper paymentMapper;
    
    @Autowired
    private PaymentHistoryService historyService;
    
    public void example() {
        // 查询支付
        Payment p = paymentRepository.findById("pay-123").orElseThrow();
        
        // 转换为DTO
        PaymentResponse response = paymentMapper.toPaymentResponse(p);
        
        // 记录历史
        historyService.recordTransition(p, CREATED, VALIDATED, SYSTEM, "OK", now);
    }
}
```

---

## ❓ 常见问题

### Q: Payment 有哪些字段？
→ id, sourceAccount, destinationAccount, amount, currency, reference, status, 
   idempotencyKey, requestFingerprint, errorCode, errorMessage, createdAt, updatedAt

### Q: 怎么创建新支付？
→ 用 PaymentMapper.toEntity()，它会生成UUID和初始状态

### Q: 怎么更新支付？
→ 修改字段后调用 PaymentRepository.save()

### Q: 怎么记录状态变化？
→ 调用 PaymentHistoryService.recordTransition()

### Q: 为什么历史表的fromStatus可以为null？
→ 初始创建记录没有"原状态"，所以fromStatus为null

### Q: 时间怎么设置？
→ 用 Instant.now(clock) 或传入的参数

---

## 📊 数据库表结构速览

### payments 表 (13列)
```sql
id, source_account, destination_account, amount, currency, reference,
status (有INDEX), idempotency_key (UNIQUE), request_fingerprint,
error_code, error_message, created_at, updated_at
```

### payment_status_history 表 (8列)
```sql
id, payment_id (FK), from_status, to_status, triggered_by,
error_code, notes, changed_at (有INDEX)
```

---

## 🎯 关键约束

✅ 幂等键全局唯一（UNIQUE约束）  
✅ 金额用BigDecimal（精确到分）  
✅ 时间用Instant（UTC）  
✅ 状态用Enum（编译检查）  
✅ 历史记录不可修改（审计原则）  

---

## 🔍 注意事项

⚠️ idempotencyKey 改动需通知前端  
⚠️ PaymentStatus 或 PaymentErrorCode 新增需团队讨论  
⚠️ 记录历史和更新Payment要在同一事务  
⚠️ 状态转换前必须调 StateMachine.validateTransition()  

---

## 📞 遇到问题

**不明白字段含义？** → 看 Payment.java 的Javadoc  
**不知道方法签名？** → 看 PaymentRepository/Service 的Javadoc  
**想了解设计为什么？** → 看 iteration1 文件夹的设计文档  
**代码有错？** → mvn clean compile 检查编译

---

**版本**: P1.0  
**状态**: ✅ 生产就绪  
**最后更新**: 2026-07-27


