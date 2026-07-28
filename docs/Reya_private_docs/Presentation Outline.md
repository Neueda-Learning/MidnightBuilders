支付处理系统（Payment Processing System）

构建一个完整的支付生命周期管理系统


团队成员：

- 成员A Reya
- 成员 B Mia
- 成员 C Fayne
- 成员D bobby


项目周期：

- 4days



==============================

PPT 2 — 项目背景：为什么要做这个系统？

为什么需要支付处理系统？


对于用户来说，支付过程非常简单：

用户

↓

点击支付

↓

支付完成


但是对于系统来说，一个支付需要经过多个步骤：


支付请求

↓

数据验证

↓

支付处理

↓

成功 / 失败

↓

状态记录



我们希望解决的问题：

1. 如何知道支付当前状态？

例如：

- 支付是否创建成功？
- 是否正在处理中？
- 是否已经完成？


2. 如何避免重复支付？

例如：

用户连续点击支付：

请求1

↓

创建支付


请求2

↓

重复支付


3. 支付失败后如何定位问题？

例如：

支付失败

↓

为什么失败？

什么时候失败？



==============================

PPT 3 — 团队介绍

我们的团队


团队共有4名成员：


**成员A (Reya)：API 与用例编排**

负责：
- PaymentController（REST API 接口）
- CreatePaymentRequest（请求 DTO）
- PaymentResponse / PaymentListItemResponse / ProcessPaymentResponse（响应 DTO）
- PaymentService（业务编排与主流程）
- 对应的 Controller、Service 和集成测试


**成员B (Mia)：校验、幂等与异常处理**

负责：
- PaymentValidationService（账户、金额、币种校验）
- PaymentIdempotencyService（幂等判决与冲突处理）
- RequestFingerprintGenerator（请求指纹生成）
- GlobalExceptionHandler（全局异常处理）
- PaymentProperties（业务配置管理）
- 对应单元测试


**成员C (Fayne)：状态与模拟处理**

负责：
- PaymentStateMachine（状态转换规则）
- PaymentLifecycleService（状态变更与历史记录）
- PaymentProcessingSimulator（支付处理模拟）
- PaymentStatus 枚举
- 对应状态和失败路径测试


**成员D (bobby)：持久化、历史与映射**

负责：
- Payment Entity（支付实体，13个字段）
- PaymentStatusHistory Entity（历史审计实体）
- PaymentRepository（支付数据访问）
- PaymentStatusHistoryRepository（历史数据访问）
- PaymentHistoryService（审计历史记录）
- PaymentMapper（对象映射转换）
- PaymentHistoryResponse（历史响应 DTO）
- 数据库迁移脚本和持久化测试


团队分工方式：


项目

↓

-------------------------------

✓ API与流程编排模块（成员A）

✓ 校验与规则引擎模块（成员B）

✓ 生命周期与状态管理模块（成员C）

✓ 数据层与审计模块（成员D）


我们按照职责边界进行模块划分。

每个成员负责专属的功能模块，通过 Git 分支和 PR 进行协作。

同时遵循 Spring Boot 分层架构原则，保证系统的可维护性。



==============================

PPT 4 — 我们学习了什么？

项目学习目标


通过这个项目，我们学习和实践了：


后端开发：

- REST API 开发
- Spring Boot
- Java


数据库设计：

- 数据模型设计
- 数据表关系设计


软件工程实践：

- Git团队协作
- 代码管理
- 单元测试
- 软件架构设计



这个项目不仅让我们学习编程技术，也让我们体验了真实的软件开发流程。



==============================

PPT 5 — 我们被要求完成什么？

项目需求


我们的任务：

开发一个支付处理系统。


系统需要管理：

支付创建

↓

支付验证

↓

支付处理

↓

成功 / 失败


完整生命周期。



系统功能要求：


1. 创建支付

用户可以创建新的支付。


2. 查询支付信息

查看：

- 支付金额
- 当前状态
- 创建时间


3. 跟踪支付状态

例如：

创建

↓

验证

↓

处理中

↓

完成


4. 查看支付历史

记录所有状态变化。


5. 搜索支付

例如：

查询：

- 成功支付
- 失败支付
- 处理中支付



项目时间：

两周完成项目开发。



==============================

PPT 6 — 我们如何完成项目？

项目开发流程


需求分析

↓

系统设计

↓

功能开发

↓

测试

↓

系统整合



开发方式：

按照功能模块进行开发。


例如：

- 支付API模块
- 验证模块
- 数据库模块
- 测试模块



我们先分析需求，然后设计系统结构，再分别开发不同模块，最后进行整合测试。



==============================

PPT 7 — 团队协作方式和使用工具

团队协作方式


Git开发流程：


创建分支

↓

开发功能

↓

提交代码

↓

代码审查

↓

合并代码



使用技术：


编程技术：

- Java
- Spring Boot
- REST API


数据库：

- MySQL 8.0+


测试：

- JUnit
- Postman


工具：

- Git
- GitHub
- Docker



我们使用 Git 分支管理代码，每个功能开发完成后，通过代码审查确保代码质量。



==============================

PPT 8 — 数据模型设计

数据库设计


我们主要设计了两个核心数据表：


**1. Payment 表（支付主表）**


保存支付基本信息：


| 字段 | 类型 | 说明 |
|---|---|---|
| id | VARCHAR(36) | 支付唯一标识（UUID） |
| source_account | VARCHAR(50) | 付款账户 |
| destination_account | VARCHAR(50) | 收款账户 |
| amount | DECIMAL(19,2) | 支付金额（精确到分） |
| currency | VARCHAR(3) | 币种代码（USD/EUR/GBP/CNY） |
| reference | VARCHAR(255) | 备注（可选） |
| status | VARCHAR(20) | 当前状态（CREATED/VALIDATED/SENT/COMPLETED/FAILED） |
| idempotency_key | VARCHAR(100) | 幂等键（防重复支付，唯一约束） |
| request_fingerprint | VARCHAR(64) | 请求指纹（判断是否同一内容） |
| error_code | VARCHAR(50) | 错误码（失败时填写） |
| error_message | VARCHAR(255) | 错误说明（失败时填写） |
| created_at | TIMESTAMP | 创建时间（UTC） |
| updated_at | TIMESTAMP | 最后更新时间（UTC） |

重要约束：idempotency_key 有唯一索引，status 有普通索引


**2. Payment Status History 表（审计历史表）**


保存支付状态变化：


| 字段 | 类型 | 说明 |
|---|---|---|
| id | VARCHAR(36) | 历史记录唯一标识 |
| payment_id | VARCHAR(36) | 关联的支付ID（外键） |
| from_status | VARCHAR(20) | 原始状态（创建记录为空） |
| to_status | VARCHAR(20) | 进入的新状态 |
| triggered_by | VARCHAR(50) | 触发者（USER/SYSTEM） |
| error_code | VARCHAR(50) | 错误码（失败转换时填写） |
| notes | VARCHAR(255) | 状态变化说明 |
| changed_at | TIMESTAMP | 状态变化时间（UTC） |

重要约束：payment_id 为外键，payment_id + changed_at 组合索引



为什么需要两个表？


因为我们需要：

1. 完整记录支付过程

所有状态转换都要被记录下来，不能只保留最后一个状态。

2. 支持审计

突然出现问题时，能追踪支付历史，找到每一步的变化。

3. 方便问题排查

通过历史表可以快速看出：
- 支付在哪一步失败？
- 每一步花了多长时间？
- 有没有跳过某个状态？

如果只保存当前状态，我们无法知道支付之前经历了什么。

所以我们额外保存状态变化历史，确保数据的完整性和可追溯性。



==============================

PPT 9 — 系统架构设计

系统整体架构


分层架构设计：

```
前端 / 客户端
       ↓
REST API 入口 (/api/payments)
       ↓
┌─────────────────────┐
│ Controller 层       │ 负责 HTTP 边界处理
│ PaymentController   │
└─────────────────────┘
       ↓
┌─────────────────────────────────────┐
│ Service 业务层（核心编排）          │
├─────────────────────────────────────┤
│ PaymentService（主入口）            │
│    ↓                                 │
│    PaymentValidationService         │ 数据校验
│    ↓                                 │
│    PaymentIdempotencyService        │ 幂等判决
│    ↓                                 │
│    PaymentLifecycleService          │ 生命周期
│    ↓                                 │
│    PaymentProcessingSimulator       │ 模拟处理
│    ↓                                 │
│    PaymentHistoryService            │ 审计记录
│    ↓                                 │
│    PaymentStateMachine              │ 状态转换
│    ↓                                 │
│    PaymentMapper                    │ 对象映射
└─────────────────────────────────────┘
       ↓
┌─────────────────────┐
│ Repository 数据层   │ 数据库访问
│ PaymentRepository   │
│ PaymentStatusHistory│
│ Repository          │
└─────────────────────┘
       ↓
┌─────────────────────┐
│   MySQL 数据库      │ 持久化存储
│                     │
│ - payments 表       │
│ - payment_status_   │
│   history 表        │
└─────────────────────┘
```


每层职责：


**Controller 层（HTTP 边界）**

负责：
- 接收 HTTP 请求
- 参数验证和 DTO 转换
- 调用 Service 处理业务
- 返回正确的 HTTP 状态码和响应体

不做：
- 业务规则判断
- 数据库查询
- 内部逻辑计算


**Service 业务层（核心编排）**

主要组件及职责：

- **PaymentService**：用例编排主入口
  - 创建支付的完整流程
  - 查询支付和列表
  - 处理支付的生命周期
  
- **PaymentValidationService**：数据校验
  - 账户格式校验
  - 金额范围和精度校验
  - 币种合法性校验
  
- **PaymentIdempotencyService**：幂等处理
  - 判决是新建、重放还是冲突
  - 生成请求指纹
  - 处理并发重复提交
  
- **PaymentLifecycleService**：生命周期管理
  - 状态转换（CREATED → VALIDATED → SENT → COMPLETED/FAILED）
  - 每次转换同时记录历史
  - 保证状态与历史的一致性
  
- **PaymentProcessingSimulator**：支付处理
  - 模拟支付发送到外部系统
  - 模拟支付确认
  - 返回成功/失败结果
  
- **PaymentHistoryService**：审计历史
  - 记录所有状态变化
  - 提供历史查询能力
  
- **PaymentStateMachine**：状态规则
  - 定义合法状态转换
  - 校验状态转换是否允许
  
- **PaymentMapper**：对象映射
  - Entity ↔ DTO 转换


**Repository 数据层（数据访问）**

负责：
- 按 ID、幂等键查询支付
- 按状态查询支付列表
- 创建或更新支付
- 保存和查询历史记录

特点：
- 不包含业务逻辑
- 只负责 CRUD 操作
- 与数据库结构紧密关联



关键设计原则：


**单一职责**

每个类只做一件事。PaymentService 只编排，验证交给 ValidationService。

**分层调用**

Controller → Service → Repository → Database，不能跨层调用。

**事务一致性**

状态变更和历史记录必须在同一个事务中提交，保证数据一致。

**无状态设计**

Service 本身不保持状态，每次调用都是独立的。



==============================

PPT 9.5 — 数据流过程（创建支付示例）

支付创建的完整数据流


以"创建支付"为例，展示数据从前端到数据库的完整流程：


**第1步：前端发送请求**

```
前端 (JavaScript / React)
   ↓
POST /api/payments
Header: Idempotency-Key: abc123def456
Content-Type: application/json

{
  "sourceAccount": "ACC001",
  "destinationAccount": "ACC002",
  "amount": 1000.00,
  "currency": "CNY",
  "reference": "Invoice-2026-07-28"
}
```


**第2步：Controller 接收并验证格式**

```
PaymentController.createPayment()
   ↓
1. 读取请求头 Idempotency-Key
2. 反序列化 JSON 到 CreatePaymentRequest
3. 框架自动进行基础 DTO 校验（非空、格式）
   ↓
将请求转发给 PaymentService
```


**第3步：Service 执行业务逻辑**

```
PaymentService.createPayment()
   ↓
① normalizeAndValidateIdempotencyKey()
   - 检查是否为空
   - 检查长度 ≤ 100
   
② normalizeAndValidateCreateRequest()
   - 账户格式校验（长度 ≤ 50）
   - 检查账户不相同
   - 金额校验（> 0，≤ 1000000，≤ 2位小数）
   - 币种校验（3位大写字母，是否在支持列表）
   - 备注校验（≤ 255 字符）
   - 规范化所有字段
   
③ checkIdempotency()
   - 生成请求指纹（account+amount+currency等内容的哈希）
   - 查询 DB：是否存在相同幂等键
     * 不存在 → 允许新建
     * 存在且指纹相同 → 返回原有记录（防重复）
     * 存在但指纹不同 → 抛冲突异常（409）
   
④ 生成新 Payment 记录
   - ID: UUID
   - 所有字段 + idempotency_key + request_fingerprint
   - status: CREATED
   - created_at & updated_at: 当前 UTC 时间
   
⑤ PaymentRepository.save()
   - 保存到 Payment 表（可能触发唯一键冲突）
   
⑥ PaymentHistoryService.recordCreation()
   - 新增历史记录：
     * payment_id: 新建的 ID
     * from_status: NULL
     * to_status: CREATED
     * triggered_by: USER
     * changed_at: 当前 UTC 时间
   
⑦ PaymentMapper.toPaymentResponse()
   - 将 Payment Entity 转换为 DTO
   - 返回给 Controller
```


**第4步：Controller 返回响应**

```
PaymentController
   ↓
检查 isCreated() 标志
   ↓
首次创建返回 201 Created
Header: Location: /api/payments/{id}

{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "sourceAccount": "ACC001",
  "destinationAccount": "ACC002",
  "amount": 1000.00,
  "currency": "CNY",
  "reference": "Invoice-2026-07-28",
  "status": "CREATED",
  "errorCode": null,
  "errorMessage": null,
  "createdAt": "2026-07-28T10:00:00Z",
  "updatedAt": "2026-07-28T10:00:00Z"
}
```


**第5步：数据库状态**

```
payments 表：
┌──────────────────────────────────────────────┐
│ id                                           │
│ 550e8400-e29b-41d4-a716-446655440000        │
│ ────────────────────────────────────────    │
│ source_account: ACC001                       │
│ destination_account: ACC002                  │
│ amount: 1000.00                              │
│ currency: CNY                                │
│ reference: Invoice-2026-07-28                │
│ status: CREATED                              │
│ idempotency_key: abc123def456 (UNIQUE)       │
│ request_fingerprint: 7f5c8a2b... (64 char)   │
│ error_code: NULL                             │
│ error_message: NULL                          │
│ created_at: 2026-07-28 10:00:00              │
│ updated_at: 2026-07-28 10:00:00              │
└──────────────────────────────────────────────┘

payment_status_history 表：
┌──────────────────────────────────────────────┐
│ id: (UUID)                                   │
│ payment_id: 550e8400-e29b-41d4-a716-...      │
│ from_status: NULL                            │
│ to_status: CREATED                           │
│ triggered_by: USER                           │
│ error_code: NULL                             │
│ notes: Payment created                       │
│ changed_at: 2026-07-28 10:00:00              │
└──────────────────────────────────────────────┘
```


**关键特性总结**

✓ 幂等键确保重试请求不会创建重复支付
✓ 请求指纹区分是否真正的重复请求还是并发冲突
✓ 每次状态变化都记录到历史表
✓ 使用 UTC 时间戳确保跨时区一致
✓ 事务保证 Payment 和 History 同时提交或同时失败

==============================

PPT 10 — 支付生命周期演示

支付流程展示


支付的 5 个状态：


1. **CREATED（已创建）**

请求已接收，支付记录已创建

但还未开始验证

2. **VALIDATED（已验证）**

数据验证通过

账户、金额、币种都符合规则

3. **SENT（已发送）**

支付已发送到外部处理系统

等待外部系统的确认

4. **COMPLETED（已完成）**

外部系统已确认

支付成功，资金已转账

5. **FAILED（已失败）**

可从任何非终态状态进入

包含失败原因（错误码和错误消息）


成功流程（正常支付）：


创建

↓ (接收请求，生成 Payment ID)

验证成功

↓ (账户和金额校验通过)

处理中

↓ (发送到外部系统)

完成

↓ (外部确认成功)

[支付成功]


失败流程1（验证阶段失败）：


创建

↓

验证失败

↓

失败

↓



状态变化：


创建

10:00


↓

验证成功

10:01


↓

处理中

10:02


↓

完成

10:03



==============================

PPT 11 — 支付历史记录展示

状态历史追踪


示例：


支付ID：

PAY12345



历史记录：


创建

2026-07-20 10:00


验证成功

2026-07-20 10:01


处理中

2026-07-20 10:02


完成

2026-07-20 10:03



价值：


通过历史记录：

- 可以查看完整支付流程
- 可以快速定位错误
- 提高系统透明度



==============================

PPT 12 — 遇到的挑战



挑战1：支付状态设计


问题：

最开始状态设计过于简单：


创建

↓

完成


无法描述真实支付过程。


解决：

设计完整状态：


创建

↓

验证

↓

处理中

↓

完成 / 失败



------------------------------


挑战2：重复支付问题


问题：

用户重复提交支付请求。


解决：

增加：

幂等性检查


保证：

同一个请求不会创建多个支付。



------------------------------


挑战3：数据一致性


问题：

支付状态和历史记录可能不同步。


解决：

每次状态变化：

同时更新：

- Payment表
- History表



==============================

PPT 13 — 未来改进方向



1. 增加前端页面


让用户可以：

- 创建支付
- 查看支付状态
- 查看支付历史
- 搜索支付



2. 增加更多功能


支付通知：

支付完成后发送通知。


数据分析：

生成支付统计报表。


批量支付：

支持多个支付同时处理。


权限管理：

增加用户角色控制。



==============================

PPT 14 — 项目总结



我们的项目从一个问题开始：


如何让支付系统更加可靠、透明、可追踪？



通过：

- REST API设计
- Spring Boot开发
- 数据库建模
- 支付生命周期管理
- 团队协作
- 自动化测试



我们最终完成：


一个能够：

- 创建支付
- 处理支付
- 跟踪状态
- 保存历史
- 管理失败情况


的完整支付处理系统。



谢谢大家！

欢迎提问。
