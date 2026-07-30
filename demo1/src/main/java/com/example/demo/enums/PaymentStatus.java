package com.example.demo.enums;

/**
 * Payment status enumeration.
 *
 * <p>A payment follows the lifecycle: CREATED -> VALIDATED -> SENT -> COMPLETED or FAILED.
 * At any point in CREATED, VALIDATED, or SENT, a payment can transition to FAILED.
 * COMPLETED and FAILED are terminal states with no further transitions.</p>
 *
 * <p>These values are persisted in the database and exposed in API responses.
 * Any change to this enum must be synchronized with database migrations, tests, and API contracts.</p>
 */
public enum PaymentStatus {

    /**
     * Initial state when a payment is first created.
     * Transition: CREATED -> VALIDATED or FAILED.
     */
    CREATED,

    /**
     * Payment has passed initial validation.
     * Transition: VALIDATED -> SENT or FAILED.
     */
    VALIDATED,

    /**
     * Payment is in transit to the destination system.
     * Transition: SENT -> COMPLETED or FAILED.
     */
    SENT,

    /**
     * Payment has been successfully completed and confirmed.
     * Terminal state: no further transitions.
     */
    COMPLETED,

    /**
     * Payment has encountered an error and cannot proceed.
     * Terminal state: no further transitions.
     */
    FAILED
}

