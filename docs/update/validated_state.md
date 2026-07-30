# VALIDATED 状态流转说明（基于当前项目实际实现）

本文档只说明当前项目中，`Payment` 在什么情况下会从 `VALIDATED` 进入 `SENT`，以及在什么情况下会从 `VALIDATED` 进入 `FAILED`。

主要依据当前实现：

- `demo1/src/main/java/com/example/demo/service/PaymentService.java`
- `demo1/src/main/java/com/example/demo/service/PaymentLifecycleService.java`
- `demo1/src/main/java/com/example/demo/service/PaymentProcessingSimulator.java`
- `demo1/src/main/java/com/example/demo/statemachine/PaymentStateMachine.java`

---

## 1. `VALIDATED -> SENT` 是什么情况下发生的

在当前项目里，`VALIDATED -> SENT` 不是单独调用某个接口直接发生的，而是发生在：

- 一笔 payment 先已经通过了 `CREATED -> VALIDATED`；
- 然后在同一次 `process` 处理流程中；
- 系统执行发送阶段模拟；
- 发送阶段返回成功；
- 于是 payment 从 `VALIDATED` 进入 `SENT`。

也就是说，核心入口仍然是：

```text
POST /api/payments/{paymentId}/process
```

### 1.1 Payment 必须已经先到达 `VALIDATED`

在 `PaymentService.processPayment(...)` 中，系统会先把 payment 从 `CREATED` 推进到 `VALIDATED`。

只有当这一阶段已经成功后，后面才会继续执行发送逻辑。

也就是说：

- 如果 payment 还停留在 `CREATED`；
- 或者在创建后的校验阶段已经失败；
- 那就不会进入 `VALIDATED -> SENT` 这一步。

### 1.2 发送阶段会调用模拟器

当 payment 已经被 `markValidated(...)` 成功写成 `VALIDATED` 后，`PaymentService.processPayment(...)` 会调用：

```text
paymentProcessingSimulator.sendPayment(current)
```

这个方法的作用是：
- 模拟把 payment 发送到目标处理系统；
- 自己不改数据库状态；
- 只返回发送结果成功还是失败。

### 1.3 模拟器要求当前状态必须是 `VALIDATED`

`PaymentProcessingSimulator.sendPayment(...)` 内部会检查：

- `payment` 不能是 `null`
- `payment.id` 不能为空
- `payment.status` 不能是 `null`
- 当前状态必须正好是 `VALIDATED`

也就是它期望这一步一定发生在：

```text
VALIDATED -> SENT
```

如果状态不是 `VALIDATED`，模拟器会把这次发送当成失败处理。

### 1.4 发送结果必须成功

如果 `sendPayment(current)` 返回：

```text
isSuccess() == true
```

那么 `PaymentService.processPayment(...)` 就会继续调用：

```text
paymentLifecycleService.markSent(current)
```

然后由 `PaymentLifecycleService`：

- 调用 `PaymentStateMachine.validateTransition(VALIDATED, SENT)`
- 清理旧失败信息
- 更新状态为 `SENT`
- 更新 `updatedAt`
- 保存 payment
- 写入一条状态历史

### 1.5 满足以上条件后会发生什么

当发送阶段成功时：

- payment 状态从 `VALIDATED` 变成 `SENT`
- 状态历史新增一条记录，通常包含：
  - `fromStatus = VALIDATED`
  - `toStatus = SENT`
  - `triggeredBy = SYSTEM`
  - `notes = Payment sent to target system`

---

## 2. `VALIDATED -> FAILED` 是什么情况下发生的

在当前项目里，`VALIDATED -> FAILED` 发生在：

- payment 已经先进入 `VALIDATED`
- 系统执行发送阶段模拟
- 但发送阶段返回失败
- 于是 payment 被标记为 `FAILED`

也就是说，这条失败路径对应的是：

```text
VALIDATED
   |
   | send payment failed
   v
FAILED
```

### 2.1 失败发生点在发送阶段

在 `PaymentService.processPayment(...)` 中，代码逻辑是：

1. 先 `markValidated(current)`
2. 再执行 `sendPayment(current)`
3. 如果发送失败，就立刻调用：

```text
paymentLifecycleService.markFailed(...)
```

因此，`VALIDATED -> FAILED` 对应的不是“校验失败”，而是“发送失败”。

### 2.2 什么情况下发送会失败

就当前实现来看，`PaymentProcessingSimulator` 默认策略非常保守：

- 只要输入是它期望的；
- 默认就返回成功；
- 没有做专门的业务失败规则（比如 `FAIL_SEND` 这种还没实现）。

所以当前代码里，发送失败主要会出现在下面几类情况：

#### 情况 1：传给模拟器的 payment 不合法
例如：
- `payment` 是 `null`
- `payment.id` 为空
- `payment.status` 是 `null`
- `payment.status` 不是 `VALIDATED`

这些情况会让模拟器内部抛异常，随后被 catch 住，并转换成失败结果。

#### 情况 2：发送阶段内部出现异常
如果模拟器在执行发送阶段时出现异常，也会返回失败结果。

当前实现里：
- 网络类异常会被映射成 `NETWORK_ERROR`
- 其他异常会被映射成 `PROCESSING_ERROR`

### 2.3 失败后会发生什么

如果 `sendPayment(current)` 返回失败，也就是：

```text
isSuccess() == false
```

那么 `PaymentService.processPayment(...)` 会调用：

```text
paymentLifecycleService.markFailed(
    current,
    sendResult.getErrorCode(),
    sendResult.getErrorMessage(),
    "Payment send stage failed"
)
```

之后会发生：

- `PaymentStateMachine.validateTransition(VALIDATED, FAILED)` 校验合法性
- payment 状态从 `VALIDATED` 变成 `FAILED`
- 保存失败信息：
  - `errorCode`
  - `errorMessage`
  - 更新时间
- 写入一条失败历史，通常包含：
  - `fromStatus = VALIDATED`
  - `toStatus = FAILED`
  - `errorCode`
  - `notes = Payment send stage failed`
  - `changedAt`

### 2.4 当前项目里这条路径的一个实际特点

虽然代码结构明确支持 `VALIDATED -> FAILED`，但在你当前项目里，这条路径**默认不容易自然触发**。

原因是：

- 模拟器现在默认是“输入合法就成功”；
- `processPayment(...)` 又是按正确顺序调用发送阶段；
- 所以正常 happy path 下，发送阶段通常会成功；
- 因此大多数情况下会继续走到 `SENT`，而不是从 `VALIDATED` 进入 `FAILED`。

换句话说：

- `VALIDATED -> SENT` 是当前系统的正常主路径；
- `VALIDATED -> FAILED` 是当前系统已经留好的失败分支；
- 但因为还没有专门的“可控发送失败规则”，这条分支现在更多是结构上支持，而不是前端演示里经常能点出来的路径。

---

## 3. 一句话总结

### `VALIDATED -> SENT`
发生条件：
- payment 已经先从 `CREATED` 进入 `VALIDATED`；
- 调用 `/process` 的流程继续执行到发送阶段；
- `paymentProcessingSimulator.sendPayment(current)` 返回成功；
- 状态机允许 `VALIDATED -> SENT`。

### `VALIDATED -> FAILED`
发生条件：
- payment 已经先从 `CREATED` 进入 `VALIDATED`；
- 调用 `/process` 的流程继续执行到发送阶段；
- `paymentProcessingSimulator.sendPayment(current)` 返回失败；
- 状态机允许 `VALIDATED -> FAILED`。

