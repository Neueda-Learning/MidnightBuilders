# 005 - Payment State Flow Gap TODO（按文件分工）

## 1. 文档目的

本文档把当前项目中与 `docs/payment-state-flow.md` 对应、但尚未完全落地的功能缺口，整理成**按文件分工的 TODO 清单**。

适用目标：

- 明确每个文件还需要补什么；
- 便于按后端 / 前端 / 测试 / 文档拆任务；
- 便于后续提交 PR 或做联调前检查。

---

## 2. 现状总结

当前系统已经具备以下主流程能力：

- 创建 Payment；
- 查询 Payment 详情；
- 查询 Payment 列表并按状态筛选；
- 处理 Payment 成功主流程：`CREATED -> VALIDATED -> SENT -> COMPLETED`；
- 查询状态历史；
- 记录创建与成功流转历史；
- 使用幂等键避免重复创建。

当前主要缺口集中在：

- 非法状态转换未完全统一为标准业务异常；
- `FAILED` 相关分支缺少稳定可演示的触发方式；
- 终态（`COMPLETED` / `FAILED`）后的处理限制虽有部分逻辑，但未形成完整交付；
- 失败详情与历史审计完整性仍可增强；
- 成功 / 失败 / 非法路径测试覆盖不均衡。

---

## 3. Source of Truth

本清单主要以以下文档和现有实现为基准：

- `docs/payment-state-flow.md`
- `demo1/src/main/java/com/example/demo/statemachine/PaymentStateMachine.java`
- `demo1/src/main/java/com/example/demo/service/PaymentService.java`
- `demo1/src/main/java/com/example/demo/service/PaymentLifecycleService.java`
- `demo1/src/main/java/com/example/demo/service/PaymentProcessingSimulator.java`
- `demo1/src/main/java/com/example/demo/service/PaymentHistoryService.java`

---

# 4. 后端文件 TODO

## 4.1 `demo1/src/main/java/com/example/demo/statemachine/PaymentStateMachine.java`

**职责：** 统一定义 Payment 状态转换规则。

### TODO
- [ ] 把 `validateTransition(...)` 从抛普通 `IllegalStateException` 改为抛 `InvalidStatusTransitionException`
- [ ] 确保所有非法转换都走统一业务异常，而不是依赖异常 message 文本
- [ ] 保留并确认以下合法转换表：
  - [ ] `CREATED -> VALIDATED`
  - [ ] `CREATED -> FAILED`
  - [ ] `VALIDATED -> SENT`
  - [ ] `VALIDATED -> FAILED`
  - [ ] `SENT -> COMPLETED`
  - [ ] `SENT -> FAILED`
  - [ ] `COMPLETED -> NONE`
  - [ ] `FAILED -> NONE`
- [ ] 保留 `isTerminalStatus(...)` 作为终态判断入口

### 完成标准
- [ ] 任意非法状态转换都稳定抛 `InvalidStatusTransitionException`
- [ ] 状态机不再产出通用 `IllegalStateException("Invalid payment status transition: ...")`

---

## 4.2 `demo1/src/main/java/com/example/demo/service/PaymentService.java`

**职责：** Payment 主流程编排。

### TODO
- [ ] 把 `/process` 的非法状态入口统一收口处理
- [ ] 当前状态不是 `CREATED` 时，不再抛普通 `IllegalStateException`
- [ ] 对 `COMPLETED` 再次处理时稳定返回 `INVALID_STATUS_TRANSITION`
- [ ] 对 `FAILED` 再次处理时稳定返回 `INVALID_STATUS_TRANSITION`
- [ ] 评估并逐步复用 `PaymentValidationService`，减少内部重复校验逻辑
- [ ] 保留当前成功主流程：
  - [ ] `CREATED -> VALIDATED`
  - [ ] `VALIDATED -> SENT`
  - [ ] `SENT -> COMPLETED`
- [ ] 确保失败响应中的 `errorCode`、`errorMessage` 一致且可预测
- [ ] 优化失败 notes，区分：
  - [ ] validation failed
  - [ ] send failed
  - [ ] confirm failed

### 完成标准
- [ ] 成功路径不受影响
- [ ] 非法状态统一变成标准业务错误
- [ ] 失败路径返回结构一致

---

## 4.3 `demo1/src/main/java/com/example/demo/service/PaymentLifecycleService.java`

**职责：** 状态变更落库，并在同事务中写状态历史。

### TODO
- [ ] 确认所有状态修改都先经过 `PaymentStateMachine.validateTransition(...)`
- [ ] 保证 `markValidated(...)`、`markSent(...)`、`markCompleted(...)`、`markFailed(...)` 是唯一合法状态写入口
- [ ] 检查 `markFailed(...)` 中失败 notes 是否足够表达失败阶段
- [ ] 如果后续新增 `failedAt`，在这里统一赋值
- [ ] 如果后续 history 保存 `errorMessage`，在这里保证完整传递

### 完成标准
- [ ] Payment 与 history 的更新始终同事务提交
- [ ] 成功 / 失败状态都能留下完整审计轨迹

---

## 4.4 `demo1/src/main/java/com/example/demo/service/PaymentProcessingSimulator.java`

**职责：** 模拟 send / confirm 结果。

### TODO
- [ ] 增加可控失败规则，支持网站演示失败分支
- [ ] 增加“发送失败”触发规则
- [ ] 增加“确认失败”触发规则
- [ ] 建议基于 `reference` 实现可控触发，例如：
  - [ ] `FAIL_SEND`
  - [ ] `FAIL_CONFIRM`
- [ ] 保证失败时返回明确的：
  - [ ] `errorCode`
  - [ ] `errorMessage`
- [ ] 保留默认成功逻辑，避免影响 happy path demo

### 完成标准
- [ ] 可以稳定触发 `VALIDATED -> FAILED`
- [ ] 可以稳定触发 `SENT -> FAILED`

---

## 4.5 `demo1/src/main/java/com/example/demo/service/PaymentHistoryService.java`

**职责：** 记录和查询状态历史。

### TODO
- [ ] 检查 `recordFailure(...)` 是否要真正保存 `errorMessage`
- [ ] 若要增强 history 审计，给失败记录增加 `errorMessage`
- [ ] 保持 creation / success transition / failure 三类历史格式一致
- [ ] 确保 `FAILED` 记录至少包含：
  - [ ] `fromStatus`
  - [ ] `toStatus=FAILED`
  - [ ] `errorCode`
  - [ ] `changedAt`
- [ ] 视需要把失败阶段写入 `notes`

### 完成标准
- [ ] history 不只完整记录成功链，也能完整记录失败链

---

## 4.6 `demo1/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java`

**职责：** API 标准错误响应转换。

### TODO
- [ ] 确保 `InvalidStatusTransitionException` 被统一处理
- [ ] 减少对 `IllegalStateException.getMessage()` 解析错误码的依赖
- [ ] 确保以下错误码响应稳定：
  - [ ] `INVALID_STATUS_TRANSITION`
  - [ ] `PAYMENT_NOT_FOUND`
  - [ ] `DUPLICATE_PAYMENT`
  - [ ] `INVALID_AMOUNT`
  - [ ] `INVALID_CURRENCY`
- [ ] 避免非法状态流转误落到 `500`

### 完成标准
- [ ] 非法状态行为不会出现“有时 400、有时 500”的不稳定现象

---

## 4.7 `demo1/src/main/java/com/example/demo/entity/Payment.java`

**职责：** Payment 主实体。

### TODO
- [ ] 评估是否保留公开 `setStatus(...)`
- [ ] 如保留，至少通过注释明确“仅供 JPA / 测试使用”
- [ ] 可选：新增 `failedAt` 字段
- [ ] 若新增 `failedAt`：
  - [ ] `markFailed(...)` 时赋值
  - [ ] 成功状态切换时按设计决定是否清空
- [ ] 复核 `clearFailure()` 语义是否满足需求

### 完成标准
- [ ] 状态尽量只能通过受控入口修改
- [ ] 失败时间表达更清楚（如决定增加）

---

## 4.8 `demo1/src/main/java/com/example/demo/entity/PaymentStatusHistory.java`

**职责：** 状态审计实体。

### TODO
- [ ] 可选：新增 `errorMessage` 字段
- [ ] 若新增，仅在失败记录中填充
- [ ] 确保成功记录不被失败字段污染
- [ ] 注释与文档字段定义保持一致

---

## 4.9 `demo1/src/main/java/com/example/demo/dto/response/PaymentHistoryResponse.java`

**职责：** 状态历史接口返回 DTO。

### TODO
- [ ] 如果 history 新增 `errorMessage`，这里同步增加返回字段
- [ ] 保证失败历史可直接被前端展示
- [ ] 确认 `null` 字段的序列化策略满足前端需求

---

## 4.10 `demo1/src/main/java/com/example/demo/dto/response/PaymentResponse.java`

**职责：** Payment 详情返回 DTO。

### TODO
- [ ] 若新增 `failedAt`，这里同步增加返回字段
- [ ] 确保失败时 `errorCode` / `errorMessage` 可见
- [ ] 成功时失败字段可为空

---

## 4.11 `demo1/src/main/java/com/example/demo/mapper/PaymentMapper.java`

**职责：** Entity / DTO 转换。

### TODO
- [ ] 若 `PaymentStatusHistory` 增加 `errorMessage`，补 `toHistoryResponse(...)`
- [ ] 若 `Payment` 增加 `failedAt`，补 `toPaymentResponse(...)`
- [ ] 确认时间格式统一为 ISO 8601

---

# 5. 前端文件 TODO

## 5.1 `demo1/src/main/resources/static/js/app.js`

**职责：** 页面主交互逻辑。

### TODO
- [ ] 给创建表单增加“失败演示”能力
- [ ] 支持前端方便触发：
  - [ ] send fail
  - [ ] confirm fail
- [ ] 对 `FAILED` 状态做更清晰展示
- [ ] 在 Payment detail 区域展示：
  - [ ] `errorCode`
  - [ ] `errorMessage`
- [ ] 若后端补了 `failedAt`，前端同步展示失败时间
- [ ] 对终态 Payment 禁用或隐藏“process”按钮
- [ ] 非法操作失败时给出更明确 toast 提示
- [ ] 在 history 中高亮失败节点

### 完成标准
- [ ] 网站不只会展示成功路径，也能清楚展示失败路径

---

## 5.2 `demo1/src/main/resources/static/js/api.js`

**职责：** 前端 API 调用封装。

### TODO
- [ ] 确认能正确传递失败演示所需字段（如 `reference`）
- [ ] 确认错误响应被上抛并可被 UI 层明确展示
- [ ] 如接口返回新增字段（`failedAt` / history `errorMessage`），同步处理

---

## 5.3 `demo1/src/main/resources/static/index.html`

**职责：** 页面结构。

### TODO
- [ ] 增加失败演示入口 UI
- [ ] 增加失败详情展示区域
- [ ] 如存在 process 按钮，对终态禁用提供 DOM 支撑
- [ ] 如有历史展示区域，为失败节点高亮预留结构

---

## 5.4 `demo1/src/main/resources/static/css/styles.css`

**职责：** 页面样式。

### TODO
- [ ] 为 `FAILED` 状态添加更明显样式
- [ ] 为 `COMPLETED` / `FAILED` / 中间态做区分视觉样式
- [ ] 为历史失败节点添加高亮样式
- [ ] 为错误提示、终态标签优化展示

---

# 6. 测试文件 TODO

## 6.1 新增：`demo1/src/test/java/com/example/demo/statemachine/PaymentStateMachineTest.java`

**职责：** 状态机单元测试。

### TODO
- [ ] 测试所有合法转换
- [ ] 测试所有非法转换抛 `InvalidStatusTransitionException`
- [ ] 测试 `COMPLETED` 是终态
- [ ] 测试 `FAILED` 是终态
- [ ] 测试 `getAllowedNextStatuses(...)` 返回正确集合

### 必测用例
- [ ] `CREATED -> VALIDATED`
- [ ] `CREATED -> FAILED`
- [ ] `VALIDATED -> SENT`
- [ ] `VALIDATED -> FAILED`
- [ ] `SENT -> COMPLETED`
- [ ] `SENT -> FAILED`
- [ ] `CREATED -> SENT` 非法
- [ ] `CREATED -> COMPLETED` 非法
- [ ] `VALIDATED -> COMPLETED` 非法
- [ ] `COMPLETED -> CREATED` 非法
- [ ] `FAILED -> SENT` 非法

---

## 6.2 `demo1/src/test/java/com/example/demo/service/PaymentLifecycleIntegrationTest.java`

**职责：** 生命周期集成测试。

### TODO
- [ ] 增加 `COMPLETED` 后再次 process 的测试
- [ ] 增加 `FAILED` 后再次 process 的测试
- [ ] 增加 send fail 场景测试
- [ ] 增加 confirm fail 场景测试
- [ ] 增加失败时 history 正确性的测试
- [ ] 断言失败时：
  - [ ] `fromStatus` 正确
  - [ ] `toStatus=FAILED`
  - [ ] `errorCode` 正确保存
  - [ ] 若增加 `errorMessage` 到 history，也一并断言

### 完成标准
- [ ] 成功、失败、非法三类路径都有集成测试覆盖

---

## 6.3 `demo1/src/test/java/com/example/demo/controller/PaymentControllerIntegrationTest.java`

**职责：** 控制器对外契约测试。

### TODO
- [ ] 增加 process 一个 `COMPLETED` payment 的测试
- [ ] 增加 process 一个 `FAILED` payment 的测试
- [ ] 断言错误响应体中的：
  - [ ] `status`
  - [ ] `errorCode`
  - [ ] `message`
- [ ] 若支持失败模拟，测试 send fail / confirm fail 的响应

---

## 6.4 `demo1/src/test/java/com/example/demo/service/PaymentHistoryServiceTest.java`

**职责：** history 单元测试。

### TODO
- [ ] 增加失败记录保存细节断言
- [ ] 若加 `errorMessage` 字段，验证其会被保存
- [ ] 验证 `recordFailure(...)` 的 notes / error 字段与设计一致

---

## 6.5 `demo1/src/test/java/com/example/demo/mapper/PaymentMapperTest.java`

**职责：** 映射单元测试。

### TODO
- [ ] 若 `PaymentResponse` 增加 `failedAt`，补映射测试
- [ ] 若 `PaymentHistoryResponse` 增加 `errorMessage`，补映射测试

---

## 6.6 `demo1/src/test/java/com/example/demo/service/PaymentValidationServiceTest.java`

**职责：** 校验逻辑测试。

### TODO
- [ ] 如果 `PaymentService` 改为真正复用 `PaymentValidationService`，保留这里作为唯一校验规则证明
- [ ] 如有需要，可补处理前非法 Payment 与失败路径的协作测试

---

## 6.7 `demo1/src/test/java/com/example/demo/migration/FlywayMigrationTest.java`
## 6.8 `demo1/src/test/java/com/example/demo/migration/FlywaySchemaValidationTest.java`

**职责：** migration 回归验证。

### TODO
- [ ] 若新增 `V3` migration，补断言新列存在
- [ ] 确保 Flyway 仍可正常迁移

---

# 7. 数据库 Migration 文件 TODO

## 7.1 `demo1/src/main/resources/db/migration/V1__create_payments_table.sql`

### TODO
- [ ] 不建议直接修改已执行 migration
- [ ] 如果需要 `failed_at`，新增新的 migration 文件处理

---

## 7.2 `demo1/src/main/resources/db/migration/V2__create_payment_status_history_table.sql`

### TODO
- [ ] 不建议直接修改已执行 migration
- [ ] 如果需要给 history 增加 `error_message`，新增新的 migration 文件处理

---

## 7.3 新增：`demo1/src/main/resources/db/migration/V3__...sql`

### TODO
- [ ] 若决定补失败时间：新增 `failed_at` 到 `payments`
- [ ] 若决定补 history 失败详情：新增 `error_message` 到 `payment_status_history`
- [ ] 保证 schema 变更全部走 Flyway

---

# 8. 配置文件 TODO

## 8.1 `demo1/src/main/resources/application.properties`

### TODO
- [ ] 若失败模拟需要配置化，可在这里增加相关配置
- [ ] 若需要开关控制 demo 失败能力，可在这里加开关

---

## 8.2 `demo1/src/main/java/com/example/demo/config/PaymentProperties.java`

### TODO
- [ ] 若要把失败模拟或其他流程规则做成配置项，可在这里扩展
- [ ] 若要进一步统一校验规则来源，避免业务常量散落各处，继续通过本类集中管理

---

# 9. 文档文件 TODO

## 9.1 `docs/payment-state-flow.md`

### TODO
- [ ] 若采用 `updatedAt` 表示失败时间，在文档中写清楚
- [ ] 若新增 `failedAt`，同步补字段说明
- [ ] 若实现 `FAIL_SEND` / `FAIL_CONFIRM` 这类演示规则，可在测试场景或说明部分补充
- [ ] 同步写清终态再次处理会返回 `INVALID_STATUS_TRANSITION`

---

## 9.2 `README.md`

### TODO
- [ ] 增加如何演示成功流程
- [ ] 增加如何演示发送失败
- [ ] 增加如何演示确认失败
- [ ] 增加如何查看 history
- [ ] 增加常见错误码说明

---

# 10. 推荐执行顺序

## 第一批：先稳住状态规则
- [ ] `PaymentStateMachine.java`
- [ ] `PaymentService.java`
- [ ] `GlobalExceptionHandler.java`

## 第二批：让失败路径可演示
- [ ] `PaymentProcessingSimulator.java`
- [ ] `PaymentLifecycleService.java`
- [ ] `PaymentHistoryService.java`

## 第三批：前端把失败与终态展示清楚
- [ ] `static/js/app.js`
- [ ] `static/js/api.js`
- [ ] `static/index.html`
- [ ] `static/css/styles.css`

## 第四批：补测试
- [ ] `PaymentStateMachineTest.java`
- [ ] `PaymentLifecycleIntegrationTest.java`
- [ ] `PaymentControllerIntegrationTest.java`
- [ ] `PaymentHistoryServiceTest.java`

## 第五批：如需补字段，再处理 schema 与 DTO
- [ ] `Payment.java`
- [ ] `PaymentStatusHistory.java`
- [ ] `V3__...sql`
- [ ] `PaymentMapper.java`
- [ ] `PaymentResponse.java`
- [ ] `PaymentHistoryResponse.java`

## 第六批：文档收尾
- [ ] `docs/payment-state-flow.md`
- [ ] `README.md`

---

# 11. 最小可交付版本（建议优先完成）

如果当前时间有限，建议至少先完成以下文件：

- [ ] `demo1/src/main/java/com/example/demo/statemachine/PaymentStateMachine.java`
- [ ] `demo1/src/main/java/com/example/demo/service/PaymentService.java`
- [ ] `demo1/src/main/java/com/example/demo/service/PaymentProcessingSimulator.java`
- [ ] `demo1/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java`
- [ ] `demo1/src/main/resources/static/js/app.js`
- [ ] `demo1/src/test/java/com/example/demo/statemachine/PaymentStateMachineTest.java`
- [ ] `demo1/src/test/java/com/example/demo/service/PaymentLifecycleIntegrationTest.java`
- [ ] `demo1/src/test/java/com/example/demo/controller/PaymentControllerIntegrationTest.java`

完成这批后，系统就会从“主要能演示成功路径”升级到“可以演示成功、失败、非法状态”。

