# 第二轮迭代：Account 校验与网络重试项目结构

## 1. 文档目的

本文档基于当前仓库的实际代码，规划第二轮迭代需要新增或调整的目录、文件和职责。第二轮目标是：引入付款账户主数据、区分三个失败阶段，并在已发送阶段模拟网络延迟和超时重试。

本文档只描述后续实现计划，不包含对现有业务代码的修改。

## 2. 当前项目进度核对

当前 `demo1` 已完成以下第一轮能力：

- Spring Boot、JPA、Flyway、MySQL 与 H2 测试环境；
- `payments` 与 `payment_status_history` 两张表；
- Payment 创建、查询、列表、处理和历史查询 API；
- CREATED、VALIDATED、SENT、COMPLETED、FAILED 状态枚举及合法转换表；
- DTO Bean Validation、后端金额/账户格式/币种校验；
- 幂等键和请求指纹；
- 每次状态变化的历史记录；
- 静态前端页面、创建表单和基础客户端校验；
- 当前模拟器始终成功，尚未真正制造延迟、超时或重试。

需要注意的现状：

- `PaymentStateMachine` 已准确实现本轮指定的转换表，不需要改变转换关系；
- `CreatePaymentRequest` 已校验必填、长度、金额范围、小数位和币种格式；
- `app.js` 已检查账户必填且不同、金额范围，因此前端基础表单校验基本完成；
- 前端账户格式没有和后端配置完全共享，后端仍必须做权威校验；
- `PaymentValidationService` 已存在，但当前实际编排仍由 `PaymentService` 内部的重复校验方法完成；
- 当前 `PaymentProcessingSimulator` 只做调用顺序检查，不生成 0–20 秒延迟；
- 当前没有 Account 实体、Repository、迁移脚本和账户数据。

## 3. 第二轮目标目录结构

```text
demo1/src/
├── main/
│   ├── java/com/example/demo/
│   │   ├── config/
│   │   │   └── PaymentProperties.java             调整：增加网络模拟配置
│   │   ├── dto/internal/
│   │   │   ├── ProcessingAttempt.java             新增：单次尝试的延迟与结果
│   │   │   └── ProcessingResult.java              调整：增加尝试次数等信息
│   │   ├── entity/
│   │   │   ├── Account.java                       新增：账户主数据实体
│   │   │   └── Payment.java                       可选调整：增加 Account 外键关系
│   │   ├── enums/
│   │   │   ├── AccountStatus.java                 新增：ACTIVE/BLOCKED/CLOSED
│   │   │   ├── AccountType.java                   新增：PERSONAL/BUSINESS
│   │   │   └── PaymentErrorCode.java              调整：增加 ACCOUNT_NOT_FOUND 等错误码
│   │   ├── exception/
│   │   │   └── AccountNotFoundException.java      新增：付款方账户不存在
│   │   ├── repository/
│   │   │   └── AccountRepository.java             新增：按账号查询
│   │   ├── service/
│   │   │   ├── AccountValidationService.java      新增：数据库账户合法性校验
│   │   │   ├── PaymentService.java                调整：重新编排三阶段失败
│   │   │   ├── PaymentValidationService.java      调整：成为表单业务校验唯一入口
│   │   │   ├── PaymentProcessingSimulator.java    调整：0–20 秒延迟与超时
│   │   │   └── NetworkRetryService.java           新增：最多三次重试
│   │   └── time/
│   │       └── DelaySleeper.java                   新增：隔离真实等待，便于测试
│   └── resources/
│       ├── application.properties                 调整：超时与重试配置
│       └── db/migration/
│           ├── V3__create_accounts_table.sql      新增：Account 表
│           ├── V4__seed_demo_accounts.sql         建议：本地演示账户
│           └── V5__link_payments_to_accounts.sql  可选：建立外键
└── test/
    └── java/com/example/demo/
        ├── repository/AccountRepositoryTest.java
        ├── service/AccountValidationServiceTest.java
        ├── service/NetworkRetryServiceTest.java
        ├── service/PaymentProcessingSimulatorTest.java
        └── service/PaymentFailureStageIntegrationTest.java
```

## 4. 分层职责

### 4.1 表单校验层

前端负责即时提示，`CreatePaymentRequest` 和 `PaymentValidationService` 负责后端权威校验。前端校验不能作为可信安全边界。

失败语义为 `CREATED → FAILED`，错误码应属于字段或业务格式错误，例如 `INVALID_AMOUNT`、`INVALID_CURRENCY`、`INVALID_ACCOUNT`。

### 4.2 Account 数据层

`AccountRepository` 只负责查找 Account。`AccountValidationService` 负责判断 `sourceAccount` 是否存在以及是否满足本轮合法条件。第二轮核心条件是付款方账号存在于数据库；状态和币种字段先保留，为后续规则扩展准备。

失败语义为 `VALIDATED → FAILED`，建议错误码为 `ACCOUNT_NOT_FOUND`。

### 4.3 网络模拟与重试层

`PaymentProcessingSimulator` 只生成单次网络结果，不修改 Payment，不写数据库。`NetworkRetryService` 负责编排初次尝试和最多三次重试。`PaymentService` 根据最终结果调用生命周期服务。

失败语义为 `SENT → FAILED`，错误码为 `NETWORK_TIMEOUT` 或沿用并细化 `NETWORK_ERROR`。

### 4.4 生命周期层

`PaymentLifecycleService` 继续作为唯一状态写入口，每次转换必须同时更新 Payment 和插入历史。状态机规则保持：

```text
CREATED   -> VALIDATED | FAILED
VALIDATED -> SENT      | FAILED
SENT      -> COMPLETED | FAILED
COMPLETED -> 无
FAILED    -> 无
```

## 5. 推荐调用方向

```text
Frontend validation
        ↓
PaymentController
        ↓
PaymentService
  ├─ PaymentValidationService
  ├─ PaymentLifecycleService
  ├─ AccountValidationService ── AccountRepository
  └─ NetworkRetryService ── PaymentProcessingSimulator ── DelaySleeper
        ↓
PaymentRepository / PaymentStatusHistoryRepository
        ↓
MySQL
```

## 6. 重要设计约束

- 不在 Controller 中直接查询 Account；
- 不让 Simulator 或 Retry Service 修改 Payment 状态；
- 不使用前端校验代替后端校验；
- 不在单元测试中真实等待 10 秒，等待策略必须可替换；
- 随机数生成器必须可注入或可替换，测试才能稳定覆盖 0、10、11、20；
- 超时阈值、最大重试次数、随机延迟范围必须配置化；
- 终态 COMPLETED 和 FAILED 不能再次处理；
- Account 主数据和 Payment 交易数据职责分离。

## 7. 实施顺序

1. 新增 Account 迁移、实体、枚举、Repository 和测试；
2. 新增 AccountValidationService 与错误码；
3. 收敛现有重复表单校验逻辑；
4. 重构处理流程，使三种失败发生在正确状态；
5. 新增可测试的随机延迟与重试组件；
6. 增加三类失败的集成测试；
7. 更新 API、数据库和状态流文档；
8. 最后执行 Maven 全量测试和 Flyway Schema 校验。

## 8. 四人文件级分工

第二轮继续沿用第一轮的 A、B、C、D 职责边界。每个生产文件只设置一名主负责人，其他成员通过方法契约协作，避免多人同时修改同一文件。

### 8.1 成员 A：API、前端与支付流程编排

主要职责：把 B、C、D 提供的能力串成完整支付用例，维护对外响应和前端展示。

主负责生产文件：

```text
demo1/src/main/java/com/example/demo/
├── controller/PaymentController.java
├── dto/response/ProcessPaymentResponse.java
└── service/PaymentService.java

demo1/src/main/resources/static/
├── index.html
├── js/api.js
└── js/app.js
```

主负责测试文件：

```text
demo1/src/test/java/com/example/demo/
├── controller/PaymentControllerIntegrationTest.java
└── service/PaymentFailureStageIntegrationTest.java
```

具体交付：

- 调整 `processPayment` 编排顺序；
- 保证三个失败分支分别从 CREATED、VALIDATED、SENT 进入 FAILED；
- 在 `ProcessPaymentResponse` 中按约定增加 `failureStage`、`attemptCount` 等可选字段；
- 前端展示 ACCOUNT_NOT_FOUND、NETWORK_TIMEOUT 与字段校验失败的不同文案；
- 前端处理期间禁用重复点击，并正确展示长请求等待状态；
- 编写贯穿 Controller、Service、Account 和状态历史的集成测试。

不得直接实现：Account 数据查询、随机延迟算法、状态持久化和错误码映射。

建议分支：`feature/iteration2-payment-orchestration`。

### 8.2 成员 B：字段校验、Account 业务校验与错误规范

主要职责：统一表单业务规则和付款方合法性判断，消除 `PaymentService` 中的重复校验。

主负责生产文件：

```text
demo1/src/main/java/com/example/demo/
├── config/PaymentProperties.java
├── enums/PaymentErrorCode.java
├── exception/AccountNotFoundException.java
├── exception/BusinessException.java
├── exception/GlobalExceptionHandler.java
├── service/AccountValidationService.java
└── service/PaymentValidationService.java
```

主负责测试文件：

```text
demo1/src/test/java/com/example/demo/service/
├── AccountValidationServiceTest.java
└── PaymentValidationServiceTest.java
```

具体交付：

- 确认字段业务校验与 DTO Bean Validation 的边界；
- 让 `PaymentValidationService` 成为字段业务规则唯一入口；
- 使用 `AccountRepository.findByAccountNumber` 校验 sourceAccount；
- 增加 `ACCOUNT_NOT_FOUND`、`ACCOUNT_NOT_ACTIVE`（如启用）和 `NETWORK_TIMEOUT` 错误码；
- 维护错误码到 HTTP 响应的统一映射；
- 在 `PaymentProperties` 中提供 0–20 秒、10 秒超时和三次重试配置。

不得直接修改：Account 表结构、网络重试循环和 `PaymentService` 编排。

建议分支：`feature/iteration2-validation-errors`。

### 8.3 成员 C：状态生命周期、网络模拟与重试

主要职责：实现可测试的随机延迟、超时和最多三次重试，并保证 Simulator 不直接写数据库。

主负责生产文件：

```text
demo1/src/main/java/com/example/demo/
├── dto/internal/ProcessingAttempt.java
├── dto/internal/ProcessingResult.java
├── service/NetworkRetryService.java
├── service/PaymentLifecycleService.java
├── service/PaymentProcessingSimulator.java
├── statemachine/PaymentStateMachine.java
└── time/DelaySleeper.java
```

主负责测试文件：

```text
demo1/src/test/java/com/example/demo/
├── service/NetworkRetryServiceTest.java
├── service/PaymentProcessingSimulatorTest.java
├── service/PaymentLifecycleServiceTest.java
└── statemachine/PaymentStateMachineTest.java
```

具体交付：

- 保持状态转换表不变并补齐三条 FAILED 路径测试；
- 实现包含端点的 0–20 整数随机延迟；
- 保证延迟 10 秒成功、11 秒开始超时；
- 定义首次尝试加最多三次重试，总尝试数最多为四；
- 把随机生成和等待行为设计为可替换依赖；
- 测试使用固定随机序列和无真实等待的 Sleeper；
- 返回每次尝试信息，不在 Simulator 或 Retry Service 中修改 Payment。

需要 B 提供网络参数配置，需要 A 消费最终 `ProcessingResult`。

建议分支：`feature/iteration2-network-retry`。

### 8.4 成员 D：Account 持久化、迁移与审计一致性

主要职责：建立 Account 数据模型和数据库访问能力，并验证失败转换历史的持久化结果。

主负责生产文件：

```text
demo1/src/main/java/com/example/demo/
├── entity/Account.java
├── enums/AccountStatus.java
├── enums/AccountType.java
└── repository/AccountRepository.java

demo1/src/main/resources/db/migration/
├── V3__create_accounts_table.sql
├── V4__seed_demo_accounts.sql
└── V5__link_payments_to_accounts.sql       可选，本轮默认不实施
```

主负责测试文件：

```text
demo1/src/test/java/com/example/demo/
├── migration/FlywayMigrationTest.java
├── migration/FlywaySchemaValidationTest.java
├── repository/AccountRepositoryTest.java
└── repository/PaymentStatusHistoryRepositoryTest.java
```

具体交付：

- 创建 `accounts` 表、约束、Account JPA 实体和 Repository；
- 准备与现有前端示例账号一致的本地演示数据；
- 验证 account_number 唯一约束和枚举字符串映射；
- 保持 `payments.source_account` 字符串兼容，第二轮默认不强制增加外键；
- 验证三类 FAILED 历史的 fromStatus、toStatus、errorCode 和顺序；
- 确保 MySQL Flyway 与 H2 测试模型一致。

不得把 Account 合法性业务规则写进 Repository，也不得让迁移脚本依赖某台开发机已有数据。

建议分支：`feature/iteration2-account-persistence`。

### 8.5 公共文件所有权与修改规则

| 公共文件 | 主负责人 | 其他成员的协作方式 |
|---|---|---|
| `PaymentService.java` | A | B/C 提供接口，A 统一编排 |
| `PaymentProperties.java` | B | C 提交所需配置字段清单，不直接同时修改 |
| `PaymentErrorCode.java` | B | A/C/D 先确认名称和语义 |
| `PaymentLifecycleService.java` | C | A 只调用公开转换方法 |
| `Account.java`、迁移脚本 | D | B 通过 Repository/Entity 契约使用 |
| `ProcessPaymentResponse.java` | A | C 提供 ProcessingResult 字段契约 |
| `application.properties` | B | C 提供默认值，B 统一落盘 |

任何人需要修改其他成员主负责文件时，应先同步改动目的和方法签名，由主负责人合入或明确授权，避免同一文件产生交叉冲突。

### 8.6 并行开发前必须共同确认的契约

四人开始编码前先确认以下内容：

1. `Account` 字段、AccountStatus 和 AccountType 枚举值；
2. `AccountRepository.findByAccountNumber` 方法签名；
3. `AccountValidationService.validatePayerAccount` 的返回值和异常；
4. `ProcessingAttempt` 与 `ProcessingResult` 字段；
5. “三次重试”是首次请求之外再重试三次；
6. 10 秒成功、11 秒超时的边界；
7. `ACCOUNT_NOT_FOUND` 和 `NETWORK_TIMEOUT` 错误码；
8. `ProcessPaymentResponse` 新增字段是否对前端公开；
9. 协议校验失败不创建 Payment，业务校验失败才记录 CREATED → FAILED；
10. 第二轮是否暂不建立 `payments.source_account_id` 外键。

### 8.7 依赖和合并顺序

| 阶段 | 负责人 | 交付内容 | 后续依赖方 |
|---|---|---|---|
| 1 | 四人 | 公共契约和方法签名 | 全员 |
| 2 | D | Account 实体、迁移、Repository | B、A |
| 3 | B | 字段校验、Account 校验、错误码和配置 | A、C |
| 4 | C | 网络模拟、重试、生命周期测试 | A |
| 5 | A | PaymentService 编排、响应和前端 | 全员联调 |
| 6 | 四人 | 全量测试、文档核对和最终验收 | 发布 |

B 和 C 在公共契约确认后可以并行；A 可先使用 Mock 编写编排测试，待 B、C、D 合并后完成端到端联调。

### 8.8 每名成员的完成定义

- 成员 A：三类失败集成测试通过，前端能正确展示并禁止重复操作；
- 成员 B：所有字段和 Account 分支有单元测试，错误响应不泄露内部异常；
- 成员 C：0、10、11、20 及重试耗尽均有确定性测试，测试无真实秒级等待；
- 成员 D：Flyway、JPA Schema Validation、唯一约束和历史持久化测试通过；
- 全员：`./mvnw test` 通过，状态与历史一致，三类 FAILED 均可从数据中明确区分。
