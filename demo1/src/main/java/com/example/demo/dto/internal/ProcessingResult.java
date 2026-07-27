package com.example.demo.dto.internal;

import com.example.demo.enums.PaymentErrorCode;

import java.util.Objects;

/**
 * Internal result object for simulated payment processing steps.
 *
 * <p><b>Purpose:</b> Represents the outcome of simulator operations such as
 * {@code sendPayment(...)} and {@code confirmPayment(...)}. This DTO is used
 * between backend services and is not part of external REST API contracts.</p>
 *
 * <p><b>Design:</b> Immutable value object with static factory methods for
 * successful and failed outcomes.</p>
 */
public final class ProcessingResult {

    /**
     * Whether the processing step succeeded.
     */
    private final boolean success;

    /**
     * Stable error code for failed results. Null when success is true.
     */
    private final PaymentErrorCode errorCode;

    /**
     * Human-readable error message for failed results. Null when success is true.
     */
    private final String errorMessage;

    /**
     * Private constructor to enforce factory method usage.
     */
    private ProcessingResult(boolean success, PaymentErrorCode errorCode, String errorMessage) {
        this.success = success;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * Create a successful processing result.
     *
     * @return success result with no error details
     */
    public static ProcessingResult success() {
        return new ProcessingResult(true, null, null);
    }

    /**
     * Create a failed processing result.
     *
     * @param errorCode stable business error code, must not be null
     * @param errorMessage human-readable message, must not be blank
     * @return failed result
     * @throws NullPointerException if errorCode is null
     * @throws IllegalArgumentException if errorMessage is blank
     */
    public static ProcessingResult failure(PaymentErrorCode errorCode, String errorMessage) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("errorMessage must not be blank");
        }
        return new ProcessingResult(false, errorCode, errorMessage.trim());
    }

    /**
     * @return true when the processing step succeeded
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return error code for failures; null for success
     */
    public PaymentErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * @return error message for failures; null for success
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "ProcessingResult{" +
                "success=" + success +
                ", errorCode=" + errorCode +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}

