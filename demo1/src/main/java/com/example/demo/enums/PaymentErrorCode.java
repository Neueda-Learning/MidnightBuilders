package com.example.demo.enums;

/**
 * Business error codes for payment processing.
 *
 * <p>These codes represent expected business failures. Each error code is mapped to:
 * - A human-readable error message
 * - An appropriate HTTP status code (400, 404, 409, 500, 503)
 * - Associated metadata for logging and API responses</p>
 *
 * <p>Error codes are persisted in payment records and exposed in API responses.
 * Client-facing error messages should not contain sensitive internal information.</p>
 */
public enum PaymentErrorCode {

    /**
     * Payment amount is outside allowed range or does not meet precision requirements.
     * Triggered when: amount <= 0 or amount > configured limit or has > 2 decimal places.
     * HTTP Status: 400
     */
    INVALID_AMOUNT,

    /**
     * Currency code is invalid or not supported.
     * Triggered when: not 3-char uppercase or not in supportedCurrencies list.
     * HTTP Status: 400
     */
    INVALID_CURRENCY,

    /**
     * Account number is empty, whitespace-only, or does not match format rules.
     * Triggered when: source or destination account fails format validation.
     * HTTP Status: 400
     */
    INVALID_ACCOUNT,

    /**
     * Source/payer account does not exist in local account master data.
     * Triggered when: sourceAccount passes format validation but is not found in accounts table.
     * HTTP Status: 400
     */
    ACCOUNT_NOT_FOUND,

    /**
     * Source and destination accounts are identical after normalization.
     * Triggered when: normalized sourceAccount == normalized destinationAccount.
     * HTTP Status: 400
     */
    SAME_SOURCE_AND_DESTINATION,

    /**
     * A payment with the same idempotency key already exists but has different content.
     * Triggered when: idempotency key collision with different request fingerprint.
     * HTTP Status: 409 (Conflict)
     */
    DUPLICATE_PAYMENT,

    /**
     * Payment status transition is not allowed.
     * Triggered when: attempting transition not in state machine rules.
     * HTTP Status: 400
     */
    INVALID_STATUS_TRANSITION,

    /**
     * Payment with the specified ID not found in database.
     * Triggered when: paymentId does not exist.
     * HTTP Status: 404
     */
    PAYMENT_NOT_FOUND,

    /**
     * Generic validation failure not covered by more specific error codes.
     * Triggered when: missing required headers (e.g., Idempotency-Key) or invalid state.
     * HTTP Status: 400
     */
    VALIDATION_FAILED,

    /**
     * Unexpected internal processing error during payment simulation or database operation.
     * Triggered when: unhandled exception in business logic.
     * HTTP Status: 500
     */
    PROCESSING_ERROR,

    /**
     * Simulated or actual network failure when communicating with external system.
     * Triggered when: timeout, connection refused, or similar in processing simulator.
     * HTTP Status: 503 (optional for iteration 1)
     */
    NETWORK_ERROR
}

