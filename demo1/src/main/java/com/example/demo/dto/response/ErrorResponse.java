package com.example.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * 统一错误响应 DTO
 *
 * 所有非成功 HTTP 响应（4xx、5xx）都使用此数据结构返回给前端。
 * 错误响应包含时间戳、HTTP 状态码、稳定错误码、可读消息和请求路径，
 * 便于前端实现国际化、错误追踪和用户引导。
 *
 * 使用场景：
 * - 业务校验失败（400）
 * - 资源未找到（404）
 * - 幂等冲突（409）
 * - 服务器异常（500）
 * - 其他 HTTP 2xx 以外的所有响应
 *
 * 示例响应：
 * {
 *   "timestamp": "2026-07-25T10:00:00Z",
 *   "status": 400,
 *   "errorCode": "INVALID_AMOUNT",
 *   "message": "Payment amount must be greater than zero",
 *   "path": "/api/payments"
 * }
 *
 * @author Demo Team
 * @since 1.0.0
 */
public class ErrorResponse {

    /**
     * 错误发生的 UTC 时间戳，格式为 ISO 8601
     * 示例：2026-07-25T10:00:00Z
     */
    @JsonProperty("timestamp")
    private String timestamp;

    /**
     * HTTP 响应状态码
     * 400、404、409、500、503 等
     */
    @JsonProperty("status")
    private Integer status;

    /**
     * 稳定的业务错误码，前端和外部系统通过此码识别错误类型
     * 支持的错误码包括：
     * - INVALID_AMOUNT：金额范围或精度错误
     * - INVALID_CURRENCY：币种格式或支持范围错误
     * - INVALID_ACCOUNT：账户格式错误
     * - SAME_SOURCE_AND_DESTINATION：源和目标账户相同
     * - VALIDATION_FAILED：通用校验失败
     * - INVALID_STATUS_TRANSITION：非法状态转换
     * - PAYMENT_NOT_FOUND：付款不存在（404）
     * - DUPLICATE_PAYMENT：幂等冲突（409）
     * - PROCESSING_ERROR：处理异常（500）
     * - NETWORK_ERROR：网络故障（503，可选）
     */
    @JsonProperty("errorCode")
    private String errorCode;

    /**
     * 面向最终用户的可读错误信息
     * 该消息应该清晰说明问题原因和解决建议，避免泄露内部技术细节
     * 示例："Payment amount must be greater than zero and not exceed 1,000,000.00"
     */
    @JsonProperty("message")
    private String message;

    /**
     * 发生错误的请求路径
     * 示例：/api/payments 或 /api/payments/{id}
     * 用于追踪和日志记录
     */
    @JsonProperty("path")
    private String path;

    /**
     * 无参构造函数
     */
    public ErrorResponse() {}

    /**
     * 全参构造函数
     */
    public ErrorResponse(String timestamp, Integer status, String errorCode, String message, String path) {
        this.timestamp = timestamp;
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
        this.path = path;
    }

    /**
     * Getter 和 Setter 方法
     */
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Builder 内部类
     */
    public static class Builder {
        private String timestamp;
        private Integer status;
        private String errorCode;
        private String message;
        private String path;

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder status(Integer status) {
            this.status = status;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(timestamp, status, errorCode, message, path);
        }
    }

    /**
     * 工厂方法：创建 Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 工厂方法：从 BusinessException 创建 ErrorResponse
     *
     * @param exception 业务异常
     * @param path 请求路径
     * @return ErrorResponse 实例
     */
    public static ErrorResponse from(com.example.demo.exception.BusinessException exception, String path) {
        return ErrorResponse.builder()
            // 使用当前 UTC 时间，格式为 ISO 8601
            .timestamp(Instant.now().toString())
            .status(exception.getHttpStatus())
            .errorCode(exception.getErrorCode())
            .message(exception.getMessage())
            .path(path)
            .build();
    }

    /**
     * 工厂方法：创建通用服务器异常响应
     *
     * @param path 请求路径
     * @param message 错误信息
     * @return ErrorResponse 实例
     */
    public static ErrorResponse ofInternalError(String path, String message) {
        return ErrorResponse.builder()
            .timestamp(Instant.now().toString())
            .status(500)
            .errorCode("PROCESSING_ERROR")
            .message(message)
            .path(path)
            .build();
    }
}

