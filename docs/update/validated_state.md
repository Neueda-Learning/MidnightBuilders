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
- 系统先校验付款账户 `sourceAccount` 是否存在于本地 `accounts` 表；
- 只有付款账户存在时，才会继续执行发送阶段模拟；
- 并且发送阶段返回成功；
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

### 1.2 `VALIDATED` 之后会先做付款账户存在性校验

当 payment 已经被 `markValidated(...)` 成功写成 `VALIDATED` 后，`PaymentService.processPayment(...)` 不会立刻调用发送模拟器，而是先执行：

```text
accountValidationService.validatePayerAccount(current)
```

这个校验只检查一件事：

- `sourceAccount` 不能为空；
- `sourceAccount` 必须能在本地 `accounts` 表里查到。

这里特意只查付款账户，不查收款账户，因为当前项目约定 `destinationAccount` 可以代表外部银行账户。

### 1.3 付款账户存在后，发送阶段才会调用模拟器

当 payment 已经被 `markValidated(...)` 成功写成 `VALIDATED` 后，`PaymentService.processPayment(...)` 会调用：

```text
paymentProcessingSimulator.sendPayment(current)
```

这个方法的作用是：
- 模拟把 payment 发送到目标处理系统；
- 自己不改数据库状态；
- 只返回发送结果成功还是失败。

### 1.4 模拟器要求当前状态必须是 `VALIDATED`

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

### 1.5 发送结果必须成功

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

### 1.6 满足以上条件后会发生什么

当发送阶段成功时：

- payment 状态从 `VALIDATED` 变成 `SENT`
- 状态历史新增一条记录，通常包含：
  - `fromStatus = VALIDATED`
  - `toStatus = SENT`
  - `triggeredBy = SYSTEM`
  - `notes = Payment sent to target system`

---

## 2. `VALIDATED -> FAILED` 是什么情况下发生的

在当前项目里，`VALIDATED -> FAILED` 现在已经有一条可以稳定显式触发的业务路径：

- payment 已经先进入 `VALIDATED`
- 系统校验付款账户 `sourceAccount` 是否存在于本地 `accounts` 表
- 如果查不到该付款账户，就立刻把 payment 标记为 `FAILED`
- 错误码使用 `ACCOUNT_NOT_FOUND`
- 错误信息为 `Source account does not exist`

也就是说，这条失败路径对应的是：

```text
VALIDATED
   |
   | payer account not found
   v
FAILED
```

### 2.1 失败发生点现在先于发送阶段

在 `PaymentService.processPayment(...)` 中，代码逻辑是：

1. 先 `markValidated(current)`
2. 再执行 `validatePayerAccount(current)`
3. 如果付款账户不存在，就立刻调用：

```text
paymentLifecycleService.markFailed(...)
```

因此，这条新增的 `VALIDATED -> FAILED` 对应的是：

- payment 已经通过了前置字段校验；
- 也已经真正进入了 `VALIDATED`；
- 但在进入发送阶段前，付款账户主数据校验失败；
- 所以从 `VALIDATED` 直接转入 `FAILED`。

### 2.2 什么情况下会触发这个失败

最典型、也是现在前端最容易演示的情况是：

- 创建 payment 时填写了一个格式上合法、但数据库里不存在的付款账户；
- 例如：`ACC-NOT-FOUND-01`；
- 调用 `POST /api/payments/{paymentId}/process` 后；
- payment 会先从 `CREATED` 进入 `VALIDATED`；
- 随后因为账户不存在，被立即标记为 `FAILED`。

也就是说，这里失败的关键不是“账号格式错”，而是：

- `sourceAccount` 看起来像一个合法账号；
- 创建阶段不会把它拦下来；
- 但处理阶段发现本地账户表里没有这条主数据；
- 所以被归类为 `VALIDATED -> FAILED`。

### 2.3 失败后会发生什么

如果付款账户不存在，`PaymentService.processPayment(...)` 会调用：

```text
paymentLifecycleService.markFailed(
    current,
    ACCOUNT_NOT_FOUND,
    "Source account does not exist",
    "Payer account validation failed"
)
```

之后会发生：

- `PaymentStateMachine.validateTransition(VALIDATED, FAILED)` 校验合法性
- payment 状态从 `VALIDATED` 变成 `FAILED`
- 保存失败信息：
  - `errorCode = ACCOUNT_NOT_FOUND`
  - `errorMessage = Source account does not exist`
  - 更新时间
- 写入一条失败历史，通常包含：
  - `fromStatus = VALIDATED`
  - `toStatus = FAILED`
  - `errorCode = ACCOUNT_NOT_FOUND`
  - `notes = Payer account validation failed`
  - `changedAt`

实际返回给前端的 `process` 响应通常会表现为：

```json
{
  "previousStatus": "CREATED",
  "currentStatus": "FAILED",
  "errorCode": "ACCOUNT_NOT_FOUND",
  "errorMessage": "Source account does not exist"
}
```

### 2.4 发送阶段失败这条老路径仍然存在

这次改动并没有删除原来“发送阶段失败也会从 `VALIDATED -> FAILED`”的结构。

也就是说，在付款账户存在的前提下：

- 如果后续 `paymentProcessingSimulator.sendPayment(current)` 返回失败；
- 系统仍然会把 payment 从 `VALIDATED` 标记为 `FAILED`；
- 只是当前项目里这条路径默认没有你这次新增的账户不存在场景那么容易显式演示。

### 2.5 当前项目里这条路径的一个实际特点

现在你当前项目里，`VALIDATED -> FAILED` 已经不再只是“结构上支持”。

因为新增了本地 `accounts` 表和付款账户存在性检查后：

- 你可以创建一笔 `sourceAccount` 不存在的 payment；
- 再点击处理；
- 系统就会稳定进入 `VALIDATED -> FAILED`；
- 并明确返回 `ACCOUNT_NOT_FOUND`。

换句话说：

- `VALIDATED -> SENT` 是当前系统的正常主路径；
- `VALIDATED -> FAILED` 现在已经有了一个稳定、可控、可以前端演示的失败分支；
- 这个分支就是“付款账户不存在”。

---

## 3. 一句话总结

### `VALIDATED -> SENT`
发生条件：
- payment 已经先从 `CREATED` 进入 `VALIDATED`；
- `sourceAccount` 能在本地 `accounts` 表中查到；
- 调用 `/process` 的流程继续执行到发送阶段；
- `paymentProcessingSimulator.sendPayment(current)` 返回成功；
- 状态机允许 `VALIDATED -> SENT`。

### `VALIDATED -> FAILED`
发生条件：
- payment 已经先从 `CREATED` 进入 `VALIDATED`；
- 调用 `/process` 后，系统校验付款账户存在性；
- `sourceAccount` 在本地 `accounts` 表中不存在；
- 系统以 `ACCOUNT_NOT_FOUND` 把 payment 标记为 `FAILED`；
- 状态机允许 `VALIDATED -> FAILED`。

