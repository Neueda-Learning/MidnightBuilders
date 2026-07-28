package com.example.demo.exception;

import com.example.demo.enums.PaymentStatus;

/**
 * 非法状态转换异常
 *
 * 当尝试进行状态机不允许的状态转换时抛出此异常。
 * 例如尝试将一笔已完成（COMPLETED）或已失败（FAILED）的付款重新处理。
 *
 * 固定返回 400 Bad Request 状态码和 INVALID_STATUS_TRANSITION 错误码。
 *
 * 合法状态转换流程（由 PaymentStateMachine 定义）：
 * - CREATED → VALIDATED → SENT → COMPLETED
 * - CREATED/VALIDATED/SENT → FAILED（在任何阶段可失败）
 *
 * 非法案例（会抛出此异常）：
 * - 尝试从 COMPLETED → VALIDATED（回退）
 * - 尝试从 FAILED → SENT（重新激活）
 * - 尝试从 COMPLETED → COMPLETED（重复处理）
 * - 参数或顺序错误导致的任何非预期转换
 *
 * @author Demo Team
 * @since 1.0.0
 */
public class InvalidStatusTransitionException extends BusinessException {

    /**
     * 固定错误码
     */
    private static final String ERROR_CODE = "INVALID_STATUS_TRANSITION";

    /**
     * 固定 HTTP 状态码：400 Bad Request
     */
    private static final Integer HTTP_STATUS = 400;

    /**
     * 构造函数：使用自定义错误消息
     *
     * @param message 可读的错误消息，应说明原状态和目标状态
     *        示例："Cannot transition from COMPLETED to VALIDATED"
     */
    public InvalidStatusTransitionException(String message) {
        super(ERROR_CODE, HTTP_STATUS, message);
    }

    /**
     * 构造函数：使用默认消息
     */
    public InvalidStatusTransitionException() {
        super(ERROR_CODE, HTTP_STATUS, "Invalid payment status transition");
    }

    /**
     * 工厂方法：使用 fromStatus 和 toStatus 创建异常，更语义化
     *
     * @param fromStatus 当前状态
     * @param toStatus 目标状态
     * @return InvalidStatusTransitionException 异常实例
     */
    public static InvalidStatusTransitionException of(PaymentStatus fromStatus, PaymentStatus toStatus) {
        return new InvalidStatusTransitionException(
            String.format("Cannot transition from %s to %s", fromStatus, toStatus)
        );
    }

    /**
     * 工厂方法：使用字符串状态创建异常
     *
     * @param fromStatus 当前状态（字符串）
     * @param toStatus 目标状态（字符串）
     * @return InvalidStatusTransitionException 异常实例
     */
    public static InvalidStatusTransitionException of(String fromStatus, String toStatus) {
        return new InvalidStatusTransitionException(
            String.format("Cannot transition from %s to %s", fromStatus, toStatus)
        );
    }
}

