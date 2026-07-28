package com.example.demo.exception;

/**
 * 付款不存在异常
 *
 * 当按 Payment ID 查询但对应付款不存在时抛出此异常。
 * 固定返回 404 Not Found 状态码和 PAYMENT_NOT_FOUND 错误码。
 *
 * 使用场景：
 * - 查询付款详情时（getPayment）
 * - 查询付款历史时（getPaymentHistory）
 * - 处理付款时（processPayment）
 * - 其他任何需要找到已有付款但实际不存在的操作
 *
 * @author Demo Team
 * @since 1.0.0
 */
public class PaymentNotFoundException extends BusinessException {

    /**
     * 固定错误码
     */
    private static final String ERROR_CODE = "PAYMENT_NOT_FOUND";

    /**
     * 固定 HTTP 状态码：404 Not Found
     */
    private static final Integer HTTP_STATUS = 404;

    /**
     * 构造函数：使用指定的 Payment ID 或消息
     *
     * @param message 可读的错误消息，通常包含找不到的 Payment ID
     *        示例："Payment with id 'xyz' not found"
     */
    public PaymentNotFoundException(String message) {
        super(ERROR_CODE, HTTP_STATUS, message);
    }

    /**
     * 构造函数：使用默认消息
     */
    public PaymentNotFoundException() {
        super(ERROR_CODE, HTTP_STATUS, "Payment not found");
    }

    /**
     * 工厂方法：创建 Payment 未找到异常，更语义化
     *
     * @param paymentId 付款 ID
     * @return PaymentNotFoundException 异常实例
     */
    public static PaymentNotFoundException ofId(String paymentId) {
        return new PaymentNotFoundException(String.format("Payment with id '%s' not found", paymentId));
    }
}

