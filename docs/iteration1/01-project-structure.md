# 第一轮迭代：Spring Boot 企业级项目目录规范

## 1. 文档目的

本文档给出 Payment Processing System 第一轮迭代建议采用的后端目录结构。它既说明当前仓库已有内容，也定义后续开发时各类文件应放置的位置，避免 Controller、业务逻辑、数据库访问和 DTO 混在一起。

本文档仅规划目录与职责，不包含业务代码。

## 2. 当前仓库结构概览

当前仓库中的主要目录如下：

```text
MidnightBuilders/
├── demo1/                         Spring Boot 后端工程
│   ├── src/main/java/             Java 源码
│   ├── src/main/resources/        应用配置与资源
│   ├── src/test/java/             Java 测试
│   ├── src/test/resources/        测试配置
│   ├── pom.xml                    Maven 项目配置
│   ├── mvnw                       macOS/Linux Maven Wrapper
│   └── mvnw.cmd                   Windows Maven Wrapper
├── docs/                          项目需求与设计文档
├── docker-compose.yml             本地 MySQL 容器配置
├── README.md                      项目入口说明
└── CONTRIBUTING.md                团队协作规范
```

目前 Java 工程只有 Spring Boot 启动类，第一轮业务代码应按照下面的目标结构逐步建立。

## 3. 第一轮后端目标目录结构

基础包继续使用当前启动类所在的 `com.example.demo`，避免 Spring 默认组件扫描不到业务类。后续若团队决定改为正式组织域名，应一次性重构所有包名，而不是同时保留两套根包。

```text
demo1/
├── pom.xml
├── mvnw
├── mvnw.cmd
└── src/
    ├── main/
    │   ├── java/com/example/demo/
    │   │   ├── Demo1Application.java
    │   │   │
    │   │   ├── controller/
    │   │   │   └── PaymentController.java
    │   │   │
    │   │   ├── dto/
    │   │   │   ├── request/
    │   │   │   │   └── CreatePaymentRequest.java
    │   │   │   ├── response/
    │   │   │   │   ├── PaymentResponse.java
    │   │   │   │   ├── PaymentListItemResponse.java
    │   │   │   │   ├── ProcessPaymentResponse.java
    │   │   │   │   ├── PaymentHistoryResponse.java
    │   │   │   │   └── ErrorResponse.java
    │   │   │   └── internal/
    │   │   │       ├── IdempotencyDecision.java
    │   │   │       └── ProcessingResult.java
    │   │   │
    │   │   ├── entity/
    │   │   │   ├── Payment.java
    │   │   │   └── PaymentStatusHistory.java
    │   │   │
    │   │   ├── enums/
    │   │   │   ├── PaymentStatus.java
    │   │   │   ├── PaymentErrorCode.java
    │   │   │   └── TriggeredBy.java
    │   │   │
    │   │   ├── repository/
    │   │   │   ├── PaymentRepository.java
    │   │   │   └── PaymentStatusHistoryRepository.java
    │   │   │
    │   │   ├── service/
    │   │   │   ├── PaymentService.java
    │   │   │   ├── PaymentValidationService.java
    │   │   │   ├── PaymentIdempotencyService.java
    │   │   │   ├── PaymentLifecycleService.java
    │   │   │   ├── PaymentHistoryService.java
    │   │   │   └── PaymentProcessingSimulator.java
    │   │   │
    │   │   ├── statemachine/
    │   │   │   └── PaymentStateMachine.java
    │   │   │
    │   │   ├── mapper/
    │   │   │   └── PaymentMapper.java
    │   │   │
    │   │   ├── exception/
    │   │   │   ├── BusinessException.java
    │   │   │   ├── PaymentNotFoundException.java
    │   │   │   ├── DuplicatePaymentException.java
    │   │   │   ├── InvalidStatusTransitionException.java
    │   │   │   └── GlobalExceptionHandler.java
    │   │   │
    │   │   ├── config/
    │   │   │   ├── ClockConfig.java
    │   │   │   └── PaymentProperties.java
    │   │   │
    │   │   └── util/
    │   │       └── RequestFingerprintGenerator.java
    │   │
    │   └── resources/
    │       ├── application.properties
    │       ├── application-local.properties
    │       ├── application-test.properties
    │       └── db/migration/
    │           ├── V1__create_payments_table.sql
    │           └── V2__create_payment_status_history_table.sql
    │
    └── test/
        ├── java/com/example/demo/
        │   ├── controller/
        │   │   └── PaymentControllerTest.java
        │   ├── service/
        │   │   ├── PaymentServiceTest.java
        │   │   ├── PaymentValidationServiceTest.java
        │   │   ├── PaymentIdempotencyServiceTest.java
        │   │   ├── PaymentLifecycleServiceTest.java
        │   │   └── PaymentHistoryServiceTest.java
        │   ├── statemachine/
        │   │   └── PaymentStateMachineTest.java
        │   ├── repository/
        │   │   ├── PaymentRepositoryTest.java
        │   │   └── PaymentStatusHistoryRepositoryTest.java
        │   └── integration/
        │       └── PaymentApiIntegrationTest.java
        └── resources/
            └── application.properties
```

## 4. 各目录职责

### 4.1 `controller`

负责 HTTP 请求与响应边界：接收路径参数、查询参数、请求头和 JSON，请求 DTO 校验，调用 Service，并返回正确 HTTP 状态码。

Controller 不应直接访问 Repository，不应修改 Entity，也不应包含金额、幂等或状态转换规则。

### 4.2 `dto/request`

保存外部请求对象。第一轮只有 `CreatePaymentRequest`，其中声明字段级的基础校验，例如必填、长度和数值格式。

请求 DTO 不作为数据库实体使用，也不携带客户端不允许修改的字段，例如 Payment ID、状态、错误码和时间戳。

### 4.3 `dto/response`

保存返回给前端的响应模型。详情、列表、处理结果、历史和错误响应分别建模，避免直接序列化 JPA Entity 导致字段泄露、懒加载或循环引用。

### 4.4 `dto/internal`

保存只在服务之间传递的结果对象。例如幂等判断结果和模拟处理结果。它们不属于外部 API 契约。

### 4.5 `entity`

保存与数据库表映射的 JPA 实体。第一轮仅有 `Payment` 和 `PaymentStatusHistory`。

Entity 负责维护自身数据一致性，但跨服务业务流程、HTTP 逻辑和复杂查询不放在实体中。

### 4.6 `enums`

保存有限且稳定的业务值：付款状态、错误码、历史触发方。统一枚举可以减少字符串拼写错误。

### 4.7 `repository`

负责数据库访问，包括按 ID、状态和幂等键查询，以及按时间查询状态历史。Repository 不负责业务校验、状态流转和响应 DTO 组装。

### 4.8 `service`

承载业务用例和业务规则：付款编排、数据校验、幂等控制、生命周期、历史审计和模拟处理。

`PaymentService` 是 Controller 的主要入口，其他 Service 是按职责拆分的协作者。

### 4.9 `statemachine`

集中声明和校验合法状态转换，保证任何调用路径都不能绕过状态规则。

### 4.10 `mapper`

负责 Entity、Request DTO、Response DTO 之间的转换。第一轮可以手写映射，不需要为了两个实体引入复杂映射框架。

### 4.11 `exception`

保存业务异常及全局异常处理器。该层把内部异常稳定地转换为前端可消费的错误码和 HTTP 状态。

### 4.12 `config`

保存 Spring Bean 和可配置业务参数。建议将系统时间 `Clock`、最大付款金额、支持币种等放入配置，便于测试和环境调整。

### 4.13 `util`

只放无状态、通用且不属于具体 Service 的辅助能力。第一轮建议只放请求指纹生成器，避免形成无边界的“杂物目录”。

### 4.14 `resources/db/migration`

保存版本化数据库脚本。企业项目建议使用 Flyway 或 Liquibase 管理结构变化，而不是长期依赖 `ddl-auto=update`。

### 4.15 测试目录

测试包结构尽量对应生产代码。Service 和状态机使用单元测试，Repository 使用数据库切片测试，Controller 使用 Web 层测试，完整付款生命周期使用集成测试。

## 5. 分层调用规则

允许的主要调用方向：

```text
Controller
    ↓
PaymentService
    ↓
Validation / Idempotency / Lifecycle / History / Simulator
    ↓
StateMachine / Mapper / Repository
    ↓
MySQL
```

禁止以下依赖：

- Controller 直接调用 Repository；
- Repository 调用 Service；
- Entity 依赖 Controller 或 DTO；
- Simulator 直接修改 Payment 状态或写数据库；
- 前端直接提交或修改付款状态；
- History Service 决定状态是否允许转换。

## 6. 文件命名约定

| 类型 | 命名方式 | 示例 |
|---|---|---|
| Controller | 业务名 + `Controller` | `PaymentController` |
| Service | 业务职责 + `Service` | `PaymentHistoryService` |
| Repository | 实体名 + `Repository` | `PaymentRepository` |
| Entity | 单数业务名 | `Payment` |
| Request DTO | 动作 + 业务名 + `Request` | `CreatePaymentRequest` |
| Response DTO | 业务含义 + `Response` | `PaymentHistoryResponse` |
| Exception | 原因 + `Exception` | `DuplicatePaymentException` |
| Test | 被测类 + `Test` | `PaymentStateMachineTest` |
| Migration | 版本 + 描述 | `V1__create_payments_table.sql` |

## 7. 第一轮必须创建与可延后文件

第一轮必须具备：Controller、外部 DTO、两个 Entity、两个 Repository、核心 Service、状态机、Mapper、异常处理和对应测试。

以下内容可根据进度简化：

- 小型项目可以先由 `PaymentService` 承担生命周期编排，但状态机必须独立；
- 独立异常子类可以先统一为带错误码的 `BusinessException`；
- `PaymentProperties` 可以先使用配置常量，但不能在多个类重复定义；
- 数据库迁移工具若第一轮未学习，可暂用 JPA 建表，但应在后续迭代补齐。

无论采用完整结构还是简化结构，都应保持职责边界一致。
