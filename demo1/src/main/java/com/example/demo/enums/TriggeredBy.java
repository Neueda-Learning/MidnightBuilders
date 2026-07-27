package com.example.demo.enums;

/**
 * Indicates who triggered a payment state transition.
 *
 * <p>This field is recorded in audit history to distinguish between:
 * - USER: Operations initiated directly by end-user (e.g., creating or processing a payment)
 * - SYSTEM: Operations initiated by internal system logic (e.g., automated state transitions during processing)</p>
 *
 * <p>Used in PaymentStatusHistory to support compliance and debugging.</p>
 */
public enum TriggeredBy {

    /**
     * The state transition was triggered by an end-user action or request.
     * Example: User submits payment creation or clicks "Process Payment" button.
     */
    USER,

    /**
     * The state transition was triggered by internal system logic.
     * Example: PaymentLifecycleService automatically transitions from CREATED to VALIDATED.
     */
    SYSTEM
}

