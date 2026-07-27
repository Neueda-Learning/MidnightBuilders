package com.example.demo.statemachine;

import com.example.demo.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Centralized payment state transition rules.
 *
 * <p>This class only validates legal transitions in memory and does not read/write database.
 * All services that change payment status should validate transitions through this class first,
 * so state rules are maintained in one place.</p>
 *
 * <p>Current allowed transitions:</p>
 * <ul>
 *   <li>CREATED -> VALIDATED, FAILED</li>
 *   <li>VALIDATED -> SENT, FAILED</li>
 *   <li>SENT -> COMPLETED, FAILED</li>
 *   <li>COMPLETED -> (no next state)</li>
 *   <li>FAILED -> (no next state)</li>
 * </ul>
 */
@Component
public class PaymentStateMachine {

    /**
     * Immutable transition table keyed by source status.
     */
    private final Map<PaymentStatus, Set<PaymentStatus>> transitionTable;

    /**
     * Build the immutable transition table once.
     */
//    {
//      CREATED:
//     [VALIDATED, FAILED],
//
//      VALIDATED:
//     [SENT, FAILED],
//
//      SENT:
//     [COMPLETED, FAILED],
//
//      COMPLETED:
//     [],
//
//      FAILED:
//     []
//    }
    public PaymentStateMachine() {
        EnumMap<PaymentStatus, Set<PaymentStatus>> table = new EnumMap<>(PaymentStatus.class);
        table.put(PaymentStatus.CREATED, Set.of(PaymentStatus.VALIDATED, PaymentStatus.FAILED));
        table.put(PaymentStatus.VALIDATED, Set.of(PaymentStatus.SENT, PaymentStatus.FAILED));
        table.put(PaymentStatus.SENT, Set.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED));
        table.put(PaymentStatus.COMPLETED, Set.of());
        table.put(PaymentStatus.FAILED, Set.of());

        this.transitionTable = Map.copyOf(table);
    }

    /**
     * Check whether a transition is legal.
     *
     * @param fromStatus current payment status
     * @param toStatus target payment status
     * @return true if and only if target status is in the allowed next statuses set
     */
    public boolean canTransition(PaymentStatus fromStatus, PaymentStatus toStatus) {
        if (fromStatus == null || toStatus == null) {
            return false;
        }
        return transitionTable.getOrDefault(fromStatus, Set.of()).contains(toStatus);
    }

    /**
     * Validate a transition and throw when transition is illegal.
     *
     * <p>For iteration-1 this method throws standard runtime exceptions.
     * In later steps this should be replaced by project-specific business exceptions
     * (for example INVALID_STATUS_TRANSITION).</p>
     *
     * @param fromStatus current payment status, must not be null
     * @param toStatus target payment status, must not be null
     * @throws IllegalArgumentException if either status is null
     * @throws IllegalStateException if transition is not allowed
     */
    public void validateTransition(PaymentStatus fromStatus, PaymentStatus toStatus) {
        Objects.requireNonNull(fromStatus, "fromStatus must not be null");
        Objects.requireNonNull(toStatus, "toStatus must not be null");

        if (!canTransition(fromStatus, toStatus)) {
            throw new IllegalStateException(
                    "Invalid payment status transition: " + fromStatus + " -> " + toStatus
            );
        }
    }

    /**
     * Return immutable allowed next statuses for a given status.
     *
     * @param status current status, must not be null
     * @return an unmodifiable set; empty set means terminal/no next transitions
     * @throws NullPointerException if status is null
     */
    public Set<PaymentStatus> getAllowedNextStatuses(PaymentStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return transitionTable.getOrDefault(status, Set.of());
    }

    /**
     * Determine whether a status is terminal.
     *
     * @param status status to check
     * @return true when status is COMPLETED or FAILED
     */
    public boolean isTerminalStatus(PaymentStatus status) {
        return status == PaymentStatus.COMPLETED || status == PaymentStatus.FAILED;
    }
}

