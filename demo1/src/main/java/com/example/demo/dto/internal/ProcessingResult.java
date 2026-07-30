package com.example.demo.dto.internal;

import com.example.demo.enums.PaymentErrorCode;

import java.util.Objects;
import java.util.List;

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

    private final List<ProcessingAttempt> attempts;

    /**
     * Private constructor to enforce factory method usage.
     */
    private ProcessingResult(boolean success, PaymentErrorCode errorCode, String errorMessage,
                             List<ProcessingAttempt> attempts) {
        this.success = success;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.attempts = List.copyOf(attempts);
    }

    /**
     * Create a successful processing result.
     *
     * @return success result with no error details
     */
    public static ProcessingResult success() {
        return new ProcessingResult(true, null, null, List.of());
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
        return new ProcessingResult(false, errorCode, errorMessage.trim(), List.of());
    }

    public static ProcessingResult success(List<ProcessingAttempt> attempts) {
        return new ProcessingResult(true, null, null, attempts);
    }

    public static ProcessingResult failure(PaymentErrorCode errorCode, String errorMessage,
                                           List<ProcessingAttempt> attempts) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("errorMessage must not be blank");
        }
        return new ProcessingResult(false, errorCode, errorMessage.trim(), attempts);
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

    public List<ProcessingAttempt> getAttempts() {
        return attempts;
    }

    public int getAttemptCount() {
        return attempts.size();
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
