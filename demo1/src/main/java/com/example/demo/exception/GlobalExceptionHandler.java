package com.example.demo.exception;

import com.example.demo.dto.response.ErrorResponse;
import com.example.demo.enums.PaymentErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 该类作为 Spring Boot 全局异常处理的入口，统一捕获和转换所有异常为 API 标准错误响应。
 * 它拦截从 Controller、Service 等所有层抛出的异常，按类型进行分类处理：
 *
 * 1. 业务异常（BusinessException）：包含错误码和 HTTP 状态，直接映射为客户端错误（400/404/409 等）
 * 2. 请求 DTO 校验失败：Spring 约束检查（@NotNull、@Valid 等）的结果，统一返回 400
 * 3. 缺少必填请求头：例如 Idempotency-Key，返回 400 并说明缺失字段
 * 4. 未预期异常：所有其他异常记录完整日志，向客户端返回通用 500
 *
 * 异常流程示意：
 *
 *     Controller/Service 抛出异常
 *            ↓
 *     GlobalExceptionHandler 捕获
 *            ↓
 *     按类型分发到对应处理方法
 *            ↓
 *     构造 ErrorResponse 并返回
 *            ↓
 *     前端收到标准错误体
 *
 * 关键设计原则：
 * - 业务异常直接使用内部错误码和 HTTP 状态
 * - 校验错误聚合详细信息但统一返回 400
 * - 系统异常记录完整堆栈但只向客户端返回通用信息（避免泄露内部逻辑）
 * - 所有时间使用 UTC ISO 8601 格式
 *
 * @author Demo Team
 * @since 1.0.0
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Set<PaymentErrorCode> BAD_REQUEST_CODES = EnumSet.of(
            PaymentErrorCode.INVALID_AMOUNT,
            PaymentErrorCode.INVALID_CURRENCY,
            PaymentErrorCode.INVALID_ACCOUNT,
            PaymentErrorCode.ACCOUNT_NOT_FOUND,
            PaymentErrorCode.NETWORK_TIMEOUT,
            PaymentErrorCode.SAME_SOURCE_AND_DESTINATION,
            PaymentErrorCode.VALIDATION_FAILED,
            PaymentErrorCode.INVALID_STATUS_TRANSITION
    );


    /**
     * 处理业务异常
     *
     * 捕获来自所有层（Validation Service、Idempotency Service 等）的 BusinessException。
     * 业务异常已包含错误码和 HTTP 状态，只需转换为 ErrorResponse 并返回。
     *
     * 处理的异常类型：
     * - BusinessException 本身：通用业务失败
     * - PaymentNotFoundException：付款不存在（404）
     * - DuplicatePaymentException：幂等冲突（409）
     * - InvalidStatusTransitionException：非法状态转换（400）
     * - 任何继承 BusinessException 的自定义异常
     *
     * @param ex 捕获的业务异常
     * @param request 当前 HTTP 请求
     * @return ResponseEntity，包含标准 ErrorResponse 和对应 HTTP 状态码
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex,
            WebRequest request) {
        // 获取请求路径，用于错误响应的 path 字段
        String path = request.getDescription(false).replace("uri=", "");

        // 日志记录：业务异常通常是预期的，不需要 ERROR 级别，info 即可
        log.info("Business exception handled: path={}, errorCode={}, httpStatus={}, message={}",
                path,
                ex.getErrorCode(),
                ex.getHttpStatus(),
                ex.getMessage());

        // 从异常构造标准错误响应
        ErrorResponse errorResponse = ErrorResponse.from(ex, path);

        // 返回对应的 HTTP 状态码和错误体
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(errorResponse);
    }

    /**
     * 处理请求 DTO 校验失败（如 @Valid、@NotNull、@Size 等约束检查失败）
     *
     * Spring 在 Controller 方法上使用 @Valid 或 @Validated 时，
     * 如果参数不符合 DTO 中定义的约束，会抛出 MethodArgumentNotValidException。
     *
     * 该方法聚合所有字段级校验错误的详情，向前端说明每个字段的具体校验失败原因。
     *
     * 典型场景：
     * - CreatePaymentRequest 中 amount 为负数 → "amount must be greater than 0"
     * - sourceAccount 超长或为空 → "sourceAccount is required and must not exceed 50 characters"
     * - currency 不符合三字符格式 → "currency must be exactly 3 characters"
     *
     * @param ex Spring MethodArgumentNotValidException
     * @param request 当前 HTTP 请求
     * @return ResponseEntity，包含 400 Bad Request 和聚合的字段错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            WebRequest request) {
        // 获取请求路径
        String path = request.getDescription(false).replace("uri=", "");

        // 聚合所有字段校验错误：将每个字段的约束违反信息拼接成一条消息
        String errorDetails = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(error -> String.format(
                        "%s: %s (received: %s)",  // 格式：字段名: 错误信息 (收到值)
                        error.getField(),
                        error.getDefaultMessage(),
                        error.getRejectedValue()
                ))
                .collect(Collectors.joining("; "));  // 多个错误用分号分隔

        // 日志记录：DTO 校验失败是常见的客户端错误，info 级别
        log.info("Request validation failed: path={}, details={}", path, errorDetails);

        // 构造标准错误响应
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode("VALIDATION_FAILED")  // 通用校验失败错误码
                .message("Request validation failed: " + errorDetails)
                .path(path)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    /**
     * 处理缺少必填请求头异常
     *
     * 设计中要求每次创建付款请求必须包含 Idempotency-Key 请求头。
     * 如果前端或客户端忘记提供此头，Spring 会抛出 MissingRequestHeaderException。
     * 该方法统一处理此类错误，告知客户端需要添加正确的请求头。
     *
     * 典型场景：POST /api/payments 时未提供 Idempotency-Key
     *
     * @param ex Spring MissingRequestHeaderException
     * @param request 当前 HTTP 请求
     * @return ResponseEntity，包含 400 Bad Request 和缺失头的说明
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeader(
            MissingRequestHeaderException ex,
            WebRequest request) {
        // 获取请求路径
        String path = request.getDescription(false).replace("uri=", "");

        // 日志记录：缺少请求头通常是客户端配置问题
        log.warn("Missing required request header: headerName={}, path={}", ex.getHeaderName(), path);

        // 构造标准错误响应
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode("VALIDATION_FAILED")  // 通用校验失败错误码
                .message(String.format(
                        "Missing required request header: '%s'",
                        ex.getHeaderName()
                ))
                .path(path)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleKnownBusinessRuntimeException(
            RuntimeException ex,
            WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        PaymentErrorCode errorCode = resolvePaymentErrorCode(ex);
        if (errorCode == null) {
            throw ex;
        }

        HttpStatus status = mapStatus(errorCode);
        log.info("Business runtime exception handled: path={}, errorCode={}, httpStatus={}, message={}",
                path,
                errorCode,
                status.value(),
                ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(status.value())
                .errorCode(errorCode.name())
                .message(buildBusinessMessage(errorCode))
                .path(path)
                .build();

        return ResponseEntity.status(status).body(errorResponse);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        log.debug("Static resource not found: path={}, resourceMessage={}", path, ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.NOT_FOUND.value())
                .errorCode(PaymentErrorCode.PAYMENT_NOT_FOUND.name())
                .message("Requested resource was not found")
                .path(path)
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 处理所有未预期的异常（捕获兜底）
     *
     * 该方法是最后的防线，捕获任何不属于上述特定类型的异常。
     * 包括：
     * - 数据库操作异常：DAOException、DataAccessException 等
     * - NPullException、ClassCastException 等程序错误
     * - 外部调用异常（如 HTTP 客户端异常）
     * - 其他所有未在代码中显式处理的异常
     *
     * 处理策略：
     * 1. 记录完整的异常堆栈到日志（ERROR 级别），便于开发者排查问题
     * 2. 向客户端返回通用 500 错误，不泄露内部技术细节
     * 3. 生成唯一的错误 ID 或请求追踪信息（可选），便于后续关联日志和用户反馈
     *
     * @param ex 捕获的任意异常
     * @param request 当前 HTTP 请求
     * @return ResponseEntity，包含 500 Internal Server Error 和通用错误信息
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception ex,
            WebRequest request) {
        // 获取请求路径
        String path = request.getDescription(false).replace("uri=", "");

        // 关键：ERROR 级别记录，记录完整堆栈，便于开发者或运维排查
        log.error("Unexpected exception occurred: path={}, exceptionType={}, message={}",
                path,
                ex.getClass().getName(),
                ex.getMessage(),
                ex);

        // 构造通用错误响应：不向客户端暴露内部堆栈或技术细节
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now().toString())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .errorCode("PROCESSING_ERROR")  // 统一的内部错误码
                .message("An unexpected error occurred. Please contact support if the problem persists.")
                .path(path)
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }

    private PaymentErrorCode resolvePaymentErrorCode(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null) {
            return null;
        }

        String message = ex.getMessage().trim();
        String candidate = message.contains(":") ? message.substring(0, message.indexOf(':')).trim() : message;

        try {
            return PaymentErrorCode.valueOf(candidate);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private HttpStatus mapStatus(PaymentErrorCode errorCode) {
        if (errorCode == PaymentErrorCode.DUPLICATE_PAYMENT) {
            return HttpStatus.CONFLICT;
        }
        if (errorCode == PaymentErrorCode.PAYMENT_NOT_FOUND) {
            return HttpStatus.NOT_FOUND;
        }
        if (BAD_REQUEST_CODES.contains(errorCode)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (errorCode == PaymentErrorCode.NETWORK_ERROR) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String buildBusinessMessage(PaymentErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_AMOUNT -> "Payment amount is invalid";
            case INVALID_CURRENCY -> "Payment currency is invalid";
            case INVALID_ACCOUNT -> "Payment account is invalid";
            case ACCOUNT_NOT_FOUND -> "Source account was not found";
            case NETWORK_TIMEOUT -> "Payment confirmation timed out after retries";
            case SAME_SOURCE_AND_DESTINATION -> "Source and destination accounts must be different";
            case DUPLICATE_PAYMENT -> "Duplicate payment request with same idempotency key but different content";
            case INVALID_STATUS_TRANSITION -> "Payment status transition is not allowed";
            case PAYMENT_NOT_FOUND -> "Payment was not found";
            case VALIDATION_FAILED -> "Request validation failed";
            case NETWORK_ERROR -> "Temporary network error occurred while processing payment";
            case PROCESSING_ERROR -> "Payment processing failed";
        };
    }
}
