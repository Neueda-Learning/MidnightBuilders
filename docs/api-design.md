# Payment Processing System - API Design

> Iteration 2 更新：处理付款响应可以返回失败阶段、网络尝试次数和模拟延迟序列。

## 1. API Overview

本文档定义 Payment Processing System 的 REST API。

API 主要用于：

- 创建付款；
- 查询付款信息；
- 查询付款列表；
- 处理付款生命周期；
- 查询付款状态历史；
- 返回标准错误信息。

API 使用 RESTful 风格设计。

Base URL:

```
/api/payments
```

---

# 2. API Summary


| Method | Endpoint | Description |
|-|-|-|
| POST | /api/payments | 创建付款 |
| GET | /api/payments/{id} | 查询付款详情 |
| GET | /api/payments | 查询付款列表 |
| GET | /api/payments?status={status} | 按状态查询付款 |
| POST | /api/payments/{id}/process | 处理付款 |
| GET | /api/payments/{id}/history | 查询状态历史 |


---

# 3. Create Payment

## POST /api/payments


## Description

创建一笔新的付款。

创建成功后：

```
Payment Status = CREATED
```

系统保存付款记录。


---

## Request


Headers:

```
Content-Type: application/json

Idempotency-Key: payment-request-001
```


Body:

```json
{
  "sourceAccount": "ACC001",
  "destinationAccount": "ACC002",
  "amount": 1000.00,
  "currency": "CNY",
  "reference": "Invoice Payment"
}
```


---

## Validation Rules


系统检查：

- sourceAccount不能为空；
- destinationAccount不能为空；
- sourceAccount不能等于destinationAccount；
- amount必须大于0；
- currency必须支持；
- Idempotency-Key不能重复。


---

## Success Response


HTTP:

```
201 Created
```


Response:

```json
{
  "id": "payment-001",
  "sourceAccount": "ACC001",
  "destinationAccount": "ACC002",
  "amount": 1000.00,
  "currency": "CNY",
  "status": "CREATED",
  "createdAt": "2026-07-25T10:00:00"
}
```


---

# 4. Get Payment Details


## GET /api/payments/{id}


## Description

根据 Payment ID 查询付款详情。


Example:

```
GET /api/payments/payment-001
```


Response:


```json
{
  "id": "payment-001",
  "sourceAccount": "ACC001",
  "destinationAccount": "ACC002",
  "amount": 1000.00,
  "currency": "CNY",
  "status": "COMPLETED",
  "createdAt": "2026-07-25T10:00:00",
  "updatedAt": "2026-07-25T10:05:00"
}
```


---

## Payment Not Found


HTTP:

```
404 Not Found
```


Response:

```json
{
  "errorCode": "PAYMENT_NOT_FOUND",
  "message": "Payment does not exist"
}
```


---

# 5. Get Payment List


## GET /api/payments


## Description

查询所有付款。


Example:

```
GET /api/payments
```


Response:


```json
[
  {
    "id": "payment-001",
    "amount": 1000.00,
    "currency": "CNY",
    "status": "COMPLETED"
  },
  {
    "id": "payment-002",
    "amount": 500.00,
    "currency": "CNY",
    "status": "FAILED"
  }
]
```


---

# 6. Filter Payments By Status

## Iteration 2 Process Response 扩展

`POST /api/payments/{id}/process` 保持原路径。响应新增以下可选字段：

| Field | Type | Description |
|---|---|---|
| failureStage | String | VALIDATION、ACCOUNT、NETWORK；成功时为 null |
| attemptCount | Integer | 实际网络尝试次数 |
| simulatedDelays | Integer[] | 每次生成的模拟延迟秒数 |

网络重试耗尽示例：

```json
{
  "id": "payment-003",
  "previousStatus": "CREATED",
  "currentStatus": "FAILED",
  "message": "Payment processing failed",
  "errorCode": "NETWORK_TIMEOUT",
  "errorMessage": "Network delay exceeded the timeout on 4 attempts",
  "attemptCount": 4,
  "simulatedDelays": [20, 15, 12, 11],
  "failureStage": "NETWORK"
}
```


## GET /api/payments?status={status}


## Description

根据付款状态筛选。


Example:


```
GET /api/payments?status=FAILED
```


支持状态：

```
CREATED

VALIDATED

SENT

COMPLETED

FAILED
```


Response:


```json
[
  {
    "id": "payment-002",
    "amount": 500.00,
    "currency": "CNY",
    "status": "FAILED",
    "errorCode": "INVALID_AMOUNT"
  }
]
```


---

# 7. Process Payment


## POST /api/payments/{id}/process


## Description

处理付款生命周期。

系统内部执行：

```
CREATED
    |
    v
VALIDATED
    |
    v
SENT
    |
    v
COMPLETED
```


如果发生错误：

```
CREATED
VALIDATED
SENT

    |
    v

FAILED
```


---

## Example


Request:


```
POST /api/payments/payment-001/process
```


Response:


```json
{
  "id": "payment-001",
  "previousStatus": "CREATED",
  "currentStatus": "COMPLETED",
  "message": "Payment processed successfully"
}
```


---

# 8. Get Payment History


## GET /api/payments/{id}/history


## Description

查询付款所有状态变化记录。


Example:


```
GET /api/payments/payment-001/history
```


Response:


```json
[
  {
    "fromStatus": null,
    "toStatus": "CREATED",
    "triggeredBy": "USER",
    "changedAt": "2026-07-25T10:00:00"
  },
  {
    "fromStatus": "CREATED",
    "toStatus": "VALIDATED",
    "triggeredBy": "SYSTEM",
    "changedAt": "2026-07-25T10:01:00"
  },
  {
    "fromStatus": "VALIDATED",
    "toStatus": "SENT",
    "triggeredBy": "SYSTEM",
    "changedAt": "2026-07-25T10:02:00"
  },
  {
    "fromStatus": "SENT",
    "toStatus": "COMPLETED",
    "triggeredBy": "SYSTEM",
    "changedAt": "2026-07-25T10:03:00"
  }
]
```


---

# 9. Error Response Design


所有错误统一返回格式：


```json
{
  "timestamp": "2026-07-25T10:00:00",
  "status": 400,
  "errorCode": "INVALID_AMOUNT",
  "message": "Amount must be greater than zero",
  "path": "/api/payments"
}
```


---

# 10. Error Codes


| Error Code | HTTP Status | Description |
|-|-|-|
| INVALID_AMOUNT | 400 | 金额非法 |
| INVALID_CURRENCY | 400 | 不支持的币种 |
| INVALID_ACCOUNT | 400 | 账户格式错误 |
| DUPLICATE_PAYMENT | 409 | 重复付款请求 |
| INVALID_STATUS_TRANSITION | 400 | 非法状态转换 |
| PAYMENT_NOT_FOUND | 404 | Payment不存在 |
| VALIDATION_FAILED | 400 | 校验失败 |
| PROCESSING_ERROR | 500 | 系统处理错误 |


---

# 11. Idempotency Design


## Purpose

避免客户端重复提交导致重复付款。


Example:


第一次请求：

```
Idempotency-Key:
payment-request-001
```


系统创建：

```
Payment-001
```


第二次相同请求：

```
Idempotency-Key:
payment-request-001
```


系统：

```
返回已有 Payment-001

不创建新的 Payment
```


---

# 12. API Flow Example


完整付款流程：


## Step 1

创建付款


```
POST /api/payments
```


Result:

```
CREATED
```


---

## Step 2

处理付款


```
POST /api/payments/{id}/process
```


System:


```
CREATED

↓

VALIDATED

↓

SENT

↓

COMPLETED
```


---

## Step 3

查看详情


```
GET /api/payments/{id}
```


---

## Step 4

查看历史


```
GET /api/payments/{id}/history
```


---

# 13. Future API Extensions


未来可以增加：

| API | Feature |
|-|-|
| POST /api/payments/batch | 批量付款 |
| POST /api/payments/{id}/cancel | 取消付款 |
| POST /api/payments/{id}/reverse | 撤销付款 |
| GET /api/reports/payments | 支付统计报表 |


---

# 14. Design Principles


本 API 遵循以下原则：

1. Controller 不直接修改数据库；
2. 状态变化由业务服务控制；
3. Payment Status 不允许客户端直接修改；
4. 所有状态变化必须产生 History；
5. 所有错误返回统一格式；
6. 使用 Idempotency-Key 防止重复付款。
