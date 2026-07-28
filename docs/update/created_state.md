# CREATED 状态流转说明（基于当前项目实际实现）

本文档只说明当前项目中，`Payment` 在什么情况下会从 `CREATED` 进入 `VALIDATED`，以及在什么情况下会从 `CREATED` 进入 `FAILED`。

主要依据当前实现：

- `demo1/src/main/java/com/example/demo/service/PaymentService.java`
- `demo1/src/main/java/com/example/demo/service/PaymentLifecycleService.java`
- `demo1/src/main/java/com/example/demo/statemachine/PaymentStateMachine.java`

---

## 1. `CREATED -> VALIDATED` 是什么情况下发生的

在当前项目里，`CREATED -> VALIDATED` 不是创建付款时自动发生的，而是**调用处理接口**后发生的。

也就是先有一笔已经保存成功、状态为 `CREATED` 的 payment，然后调用：

```text
POST /api/payments/{paymentId}/process
```

当且仅当下面条件都满足时，这笔 payment 才会从 `CREATED` 进入 `VALIDATED`：

### 1.1 Payment 必须存在

`PaymentService.processPayment(...)` 会先根据 `paymentId` 查库。

如果查不到：
- 不会发生状态流转；
- 会直接抛 `PAYMENT_NOT_FOUND`。

### 1.2 当前状态必须是 `CREATED`

在 `PaymentService.processPayment(...)` 里，先取当前状态：

- 如果当前状态不是 `CREATED`；
- 就不会继续处理；
- 会直接抛 `INVALID_STATUS_TRANSITION` 对应的异常消息。

所以，只有当前状态本来就是 `CREATED`，才有机会继续往下走。

### 1.3 处理前校验必须全部通过

当前项目真正使用的是 `PaymentService.validatePaymentForProcessing(...)`，不是别的文档理想状态下的未来实现。

这一步会检查：

#### 账户条件
- `sourceAccount` 不能是 `null`
- `destinationAccount` 不能是 `null`
- 两个账户去掉首尾空格后都不能为空
- 两个账户去掉首尾空格后不能相同
- 账户长度不能超过 50

#### 金额条件
- `amount` 不能是 `null`
- `amount` 必须大于 0
- `amount` 不能大于 `1000000.00`
- `amount` 最多保留 2 位小数

#### 币种条件
- `currency` 不能是 `null`
- 去掉空格并转大写后，长度必须正好是 3
- 3 个字符都必须是字母
- 币种必须在当前支持集合中：
  - `USD`
  - `EUR`
  - `GBP`
  - `CNY`

### 1.4 状态机必须允许这次流转

在 `PaymentStateMachine` 里，当前允许：

```text
CREATED -> VALIDATED
CREATED -> FAILED
```

`PaymentLifecycleService.markValidated(...)` 在真正写库前，还会再次调用：

```text
PaymentStateMachine.validateTransition(CREATED, VALIDATED)
```

只有状态机也认可这次转换，才会真正落库。

### 1.5 满足以上条件后会发生什么

如果上面条件全部满足：

- `PaymentLifecycleService.markValidated(...)` 会被调用；
- payment 状态从 `CREATED` 改为 `VALIDATED`；
- 清理旧的失败信息；
- 更新 `updatedAt`；
- 保存 payment；
- 写入一条状态历史，通常是：
  - `fromStatus = CREATED`
  - `toStatus = VALIDATED`
  - `triggeredBy = SYSTEM`
  - `notes = Payment validation passed`

---

## 2. `CREATED -> FAILED` 是什么情况下发生的

在当前项目里，`CREATED -> FAILED` 也是发生在**调用处理接口**时，不是在创建 payment 的那一刻直接发生。

也就是说：
- payment 已经创建成功；
- 当前状态还是 `CREATED`；
- 调用 `/process`；
- 但处理前校验失败；
- 于是这笔 payment 会被标记为 `FAILED`。

### 2.1 必须先进入 `processPayment(...)`

只有在调用：

```text
POST /api/payments/{paymentId}/process
```

并且这笔 payment 当前状态是 `CREATED` 时，系统才会尝试做这条分支。

如果当前状态不是 `CREATED`，那不是 `CREATED -> FAILED`，而是直接被拒绝为非法状态流转。

### 2.2 处理前校验失败时，会进入 `CREATED -> FAILED`

在 `PaymentService.processPayment(...)` 中，`validatePaymentForProcessing(current)` 被包在 `try/catch` 里。

只要这一步抛出运行时异常，系统就会调用：

```text
paymentLifecycleService.markFailed(...)
```

于是 payment 从 `CREATED` 进入 `FAILED`。

### 2.3 会导致 `CREATED -> FAILED` 的具体情况

以下情况都会让处理前校验失败，从而触发 `CREATED -> FAILED`：

#### 账户问题
- `sourceAccount` 为 `null`
- `destinationAccount` 为 `null`
- 账户去空格后为空字符串
- 账户长度超过 50
- `sourceAccount` 和 `destinationAccount` 相同

#### 金额问题
- `amount` 为 `null`
- `amount <= 0`
- `amount > 1000000.00`
- 小数位超过 2 位

#### 币种问题
- `currency` 为 `null`
- 币种去空格转大写后长度不是 3
- 币种包含非字母字符
- 币种不在支持集合中：`USD`、`EUR`、`GBP`、`CNY`

### 2.4 失败后会发生什么

如果这里失败：

- `PaymentLifecycleService.markFailed(...)` 会被调用；
- 状态机会先校验 `CREATED -> FAILED` 合法；
- payment 状态改为 `FAILED`；
- 保存失败信息：
  - `errorCode`
  - `errorMessage`
  - 更新时间
- 写入失败历史记录，通常包含：
  - `fromStatus = CREATED`
  - `toStatus = FAILED`
  - `errorCode`
  - `notes`
  - `changedAt`

### 2.5 当前项目里这条路径的一个实际特点

虽然代码支持 `CREATED -> FAILED`，但你现在项目里的创建接口本身已经先做了一轮创建校验。

这意味着：
- 很多明显非法的数据；
- 在创建 payment 时就已经被拦住；
- 根本不会先保存成一条 `CREATED` payment。

所以当前实际运行中：

- `CREATED -> VALIDATED` 是正常主路径；
- `CREATED -> FAILED` 更像是“已存在的 `CREATED` 数据在处理前复核失败”时触发；
- 这条分支在正常前端 happy path 下不如成功路径常见。

---

## 3. 一句话总结

### `CREATED -> VALIDATED`
发生条件：
- payment 存在；
- 当前状态是 `CREATED`；
- 调用 `/process`；
- 处理前校验全部通过；
- 状态机允许 `CREATED -> VALIDATED`。

### `CREATED -> FAILED`
发生条件：
- payment 存在；
- 当前状态是 `CREATED`；
- 调用 `/process`；
- 处理前校验失败；
- 状态机允许 `CREATED -> FAILED`。

