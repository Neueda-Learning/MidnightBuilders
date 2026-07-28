package com.example.demo.exception;

/**
 * 业务异常基类
 *
 * 该异常类用于承载所有预期的业务失败情况，包含稳定的错误码、HTTP 状态码和可读错误信息。
 * 所有业务规则校验失败、状态转换非法等情况都应该抛出此异常或其子类。
 *
 * 使用示例：
 * throw new BusinessException(
 *     PaymentErrorCode.INVALID_AMOUNT.toString(),
 *     HttpStatus.BAD_REQUEST.value(),
 *     "Payment amount must be greater than zero"
 * );
 *
 * @author Demo Team
 * @since 1.0.0
 */
public class BusinessException extends RuntimeException {

    /**
     * 稳定的业务错误码，用于前端和外部系统识别错误类型
     * 例如：INVALID_AMOUNT、DUPLICATE_PAYMENT、PAYMENT_NOT_FOUND 等
     */
    private final String errorCode;

    /**
     * HTTP 响应状态码
     * 例如：400（客户端错误）、404（未找到）、409（冲突）、500（服务器错误）
     */
    private final Integer httpStatus;

    /**
     * 面向调用方的可读错误消息
     * 该消息将直接返回给前端或 API 客户端，应该清晰说明问题原因
     */
    private final String message;

    /**
     * 构造函数：完整初始化业务异常
     *
     * @param errorCode 业务错误码
     * @param httpStatus HTTP 状态码
     * @param message 可读错误信息
     */
    public BusinessException(String errorCode, Integer httpStatus, String message) {
        super(message);
        // 验证错误码和状态码不能为空，避免关键信息丢失
        if (errorCode == null || errorCode.trim().isEmpty()) {
            throw new IllegalArgumentException("errorCode cannot be null or empty");
        }
        if (httpStatus == null) {
            throw new IllegalArgumentException("httpStatus cannot be null");
        }

        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.message = message != null ? message : "";
    }

    /**
     * 构造函数：指定错误码和信息（HTTP 状态默认为 500）
     *
     * @param errorCode 业务错误码
     * @param message 可读错误信息
     */
    public BusinessException(String errorCode, String message) {
        this(errorCode, 500, message);
    }

    /**
     * 获取错误码
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 获取 HTTP 状态码
     * @return HTTP 状态码
     */
    public Integer getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String toString() {
        return "BusinessException{" +
                "errorCode='" + errorCode + '\'' +
                ", httpStatus=" + httpStatus +
                ", message='" + message + '\'' +
                '}';
    }
}

