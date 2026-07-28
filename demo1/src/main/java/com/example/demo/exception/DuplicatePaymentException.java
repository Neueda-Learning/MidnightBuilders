package com.example.demo.exception;

/**
 * 重复付款异常
 *
 * 当同一幂等键（Idempotency-Key）对应不同的请求内容时抛出此异常。
 * 这是幂等性冲突的具体表现：客户端用同一个键重复提交了不同的请求。
 *
 * 固定返回 409 Conflict 状态码和 DUPLICATE_PAYMENT 错误码。
 *
 * 幂等键机制说明：
 * - 同一幂等键 + 相同请求内容 → 返回原有 Payment（200 OK）
 * - 同一幂等键 + 不同请求内容 → 抛出此异常（409 Conflict）
 * - 不同幂等键 → 总是创建新 Payment（201 Created）
 *
 * 原因分析：通常表示客户端或网络问题，例如：
 * - 用户误操作，先后提交了两笔不同金额的付款，但使用了相同的幂等键
 * - 网络重连导致请求重试时，请求内容被篡改或变化
 *
 * @author Demo Team
 * @since 1.0.0
 */
public class DuplicatePaymentException extends BusinessException {

    /**
     * 固定错误码
     */
    private static final String ERROR_CODE = "DUPLICATE_PAYMENT";

    /**
     * 固定 HTTP 状态码：409 Conflict
     * 表示请求冲突，客户端需要修正幂等键或请求内容后重试
     */
    private static final Integer HTTP_STATUS = 409;

    /**
     * 构造函数：使用自定义错误消息
     *
     * @param message 可读的错误消息，应说明冲突的原因
     *        示例："Idempotency key 'key-123' already exists with different request content"
     */
    public DuplicatePaymentException(String message) {
        super(ERROR_CODE, HTTP_STATUS, message);
    }

    /**
     * 构造函数：使用默认消息
     */
    public DuplicatePaymentException() {
        super(ERROR_CODE, HTTP_STATUS, "Duplicate payment request with same idempotency key but different content");
    }

    /**
     * 工厂方法：使用幂等键创建异常，更语义化
     *
     * @param idempotencyKey 重复的幂等键
     * @return DuplicatePaymentException 异常实例
     */
    public static DuplicatePaymentException ofKey(String idempotencyKey) {
        return new DuplicatePaymentException(
            String.format("Idempotency key '%s' already exists with different request content", idempotencyKey)
        );
    }
}

