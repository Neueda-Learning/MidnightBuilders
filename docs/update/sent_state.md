# SENT 状态流转说明（基于当前项目实际实现）

本文档只说明当前项目中，`Payment` 在什么情况下会从 `SENT` 进入 `COMPLETED`，以及在什么情况下会从 `SENT` 进入 `FAILED`。

主要依据当前实现：

- `demo1/src/main/java/com/example/demo/service/PaymentService.java`
- `demo1/src/main/java/com/example/demo/service/PaymentLifecycleService.java`
- `demo1/src/main/java/com/example/demo/service/PaymentProcessingSimulator.java`
- `demo1/src/main/java/com/example/demo/statemachine/PaymentStateMachine.java`

---

## 1. `SENT -> COMPLETED` 是什么情况下发生的

在当前项目里，`SENT -> COMPLETED` 不是单独调用某个接口直接发生的，而是发生在：

- 一笔 payment 已经先通过了 `CREATED -> VALIDATED -> SENT`；
- 然后在同一次 `process` 处理流程中；
- 系统执行最终确认阶段模拟；
- 确认阶段返回成功；
- 于是 payment 从 `SENT` 进入 `COMPLETED`。

也就是说，核心入口仍然是：

```text
POST /api/payments/{paymentId}/process
```

### 1.1 Payment 必须已经先到达 `SENT`

在 `PaymentService.processPayment(...)` 中，系统会按顺序先完成：

1. `CREATED -> VALIDATED`
2. `VALIDATED -> SENT`

只有发送阶段已经成功，并且 `paymentLifecycleService.markSent(current)` 已经把 payment 持久化成 `SENT` 之后，后面才会继续执行确认逻辑。

也就是说：

- 如果 payment 还停留在 `CREATED`；
- 或者在校验阶段失败；
- 或者在发送阶段失败；
- 那就不会进入 `SENT -> COMPLETED` 这一步。

### 1.2 确认阶段会调用模拟器

当 payment 已经被 `markSent(...)` 成功写成 `SENT` 后，`PaymentService.processPayment(...)` 会调用：

```text
paymentProcessingSimulator.confirmPayment(current)
```

这个方法的作用是：
- 模拟目标系统对 payment 的最终确认；
- 自己不改数据库状态；
- 只返回确认结果成功还是失败。

### 1.3 模拟器要求当前状态必须是 `SENT`

`PaymentProcessingSimulator.confirmPayment(...)` 内部会检查：

- `payment` 不能是 `null`
- `payment.id` 不能为空
- `payment.status` 不能是 `null`
- 当前状态必须正好是 `SENT`

也就是它期望这一步一定发生在：

```text
SENT -> COMPLETED
```

如果状态不是 `SENT`，模拟器会把这次确认当成失败处理。

### 1.4 确认结果必须成功

如果 `confirmPayment(current)` 返回：

```text
isSuccess() == true
```

那么 `PaymentService.processPayment(...)` 就会继续调用：

```text
paymentLifecycleService.markCompleted(current)
```

然后由 `PaymentLifecycleService`：

- 调用 `PaymentStateMachine.validateTransition(SENT, COMPLETED)`
- 清理旧失败信息
- 更新状态为 `COMPLETED`
- 更新 `updatedAt`
- 保存 payment
- 写入一条状态历史

### 1.5 满足以上条件后会发生什么

当确认阶段成功时：

- payment 状态从 `SENT` 变成 `COMPLETED`
- 状态历史新增一条记录，通常包含：
  - `fromStatus = SENT`
  - `toStatus = COMPLETED`
  - `triggeredBy = SYSTEM`
  - `notes = Payment processing completed`

---

## 2. `SENT -> FAILED` 是什么情况下发生的

在当前项目里，`SENT -> FAILED` 发生在：

- payment 已经先进入 `SENT`
- 系统执行最终确认阶段模拟
- 但确认阶段返回失败
- 于是 payment 被标记为 `FAILED`

也就是说，这条失败路径对应的是：

```text
SENT
   |
   | confirm payment failed
   v
FAILED
```

### 2.1 失败发生点在确认阶段

在 `PaymentService.processPayment(...)` 中，代码逻辑是：

1. 先 `markValidated(current)`
2. 再执行 `sendPayment(current)`
3. 发送成功后 `markSent(current)`
4. 再执行 `confirmPayment(current)`
5. 如果确认失败，就立刻调用：

```text
paymentLifecycleService.markFailed(...)
```

因此，`SENT -> FAILED` 对应的不是“发送失败”，而是“确认失败”。

### 2.2 什么情况下确认会失败

就当前实现来看，`PaymentProcessingSimulator` 默认策略非常保守：

- 只要输入是它期望的；
- 默认就返回成功；
- 没有做专门的业务失败规则（比如 `FAIL_CONFIRM` 这种还没实现）。

所以当前代码里，确认失败主要会出现在下面几类情况：

#### 情况 1：传给模拟器的 payment 不合法
例如：
- `payment` 是 `null`
- `payment.id` 为空
- `payment.status` 是 `null`
- `payment.status` 不是 `SENT`

这些情况会让模拟器内部抛异常，随后被 catch 住，并转换成失败结果。

#### 情况 2：确认阶段内部出现异常
如果模拟器在执行确认阶段时出现异常，也会返回失败结果。

当前实现里：
- 网络类异常会被映射成 `NETWORK_ERROR`
- 其他异常会被映射成 `PROCESSING_ERROR`

### 2.3 失败后会发生什么

如果 `confirmPayment(current)` 返回失败，也就是：

```text
isSuccess() == false
```

那么 `PaymentService.processPayment(...)` 会调用：

```text
paymentLifecycleService.markFailed(
    current,
    confirmResult.getErrorCode(),
    confirmResult.getErrorMessage(),
    "Payment confirm stage failed"
)
```

之后会发生：

- `PaymentStateMachine.validateTransition(SENT, FAILED)` 校验合法性
- payment 状态从 `SENT` 变成 `FAILED`
- 保存失败信息：
  - `errorCode`
  - `errorMessage`
  - 更新时间
- 写入一条失败历史，通常包含：
  - `fromStatus = SENT`
  - `toStatus = FAILED`
  - `errorCode`
  - `notes = Payment confirm stage failed`
  - `changedAt`

### 2.4 当前项目里这条路径的一个实际特点

虽然代码结构明确支持 `SENT -> FAILED`，但在你当前项目里，这条路径**默认不容易自然触发**。

原因是：

- 模拟器现在默认是“输入合法就成功”；
- `processPayment(...)` 又是按正确顺序调用确认阶段；
- 所以正常 happy path 下，确认阶段通常会成功；
- 因此大多数情况下会继续走到 `COMPLETED`，而不是从 `SENT` 进入 `FAILED`。

换句话说：

- `SENT -> COMPLETED` 是当前系统的正常主路径；
- `SENT -> FAILED` 是当前系统已经留好的失败分支；
- 但因为还没有专门的“可控确认失败规则”，这条分支现在更多是结构上支持，而不是前端演示里经常能点出来的路径。

### 2.5 一个额外需要知道的现实点

虽然文档里讨论的是 `SENT -> COMPLETED` 和 `SENT -> FAILED`，但你当前项目的对外接口并**不能**让一笔已经停留在 `SENT` 的 payment 被用户单独继续处理。

因为 `PaymentService.processPayment(...)` 一开始就要求：

- 当前状态必须是 `CREATED`

所以现在 `SENT` 之后的流转属于：
- 同一次 `/process` 请求内部继续往下推进；
- 不是“先停在 `SENT`，之后再单独调一个接口继续到 `COMPLETED` 或 `FAILED`”。

---

## 3. 一句话总结

### `SENT -> COMPLETED`
发生条件：
- payment 已经先从 `CREATED` 进入 `VALIDATED`，再进入 `SENT`；
- 调用 `/process` 的流程继续执行到确认阶段；
- `paymentProcessingSimulator.confirmPayment(current)` 返回成功；
- 状态机允许 `SENT -> COMPLETED`。

### `SENT -> FAILED`
发生条件：
- payment 已经先从 `CREATED` 进入 `VALIDATED`，再进入 `SENT`；
- 调用 `/process` 的流程继续执行到确认阶段；
- `paymentProcessingSimulator.confirmPayment(current)` 返回失败；
- 状态机允许 `SENT -> FAILED`。

