package com.example.demo.service;

import com.example.demo.entity.Payment;
import com.example.demo.entity.PaymentStatusHistory;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.enums.TriggeredBy;
import com.example.demo.repository.PaymentStatusHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for recording and retrieving payment status history.
 *
 * <p><b>Responsibility:</b> Manage audit trail of payment state transitions.
 * This service encapsulates the logic for creating history records and ensures
 * that all status changes are properly documented for compliance and debugging.</p>
 *
 * <p><b>Key Points:</b></p>
 * <ul>
 *   <li>Every payment status change must be recorded as a history entry</li>
 *   <li>History records are immutable once persisted</li>
 *   <li>The initial creation record has no fromStatus (represents initial entry to CREATED state)</li>
 *   <li>All timestamps are UTC and should be consistent with Payment entity timestamps</li>
 *   <li>Methods should participate in the same transaction as the Payment update for ACID compliance</li>
 * </ul>
 *
 * <p><b>Transaction Scope:</b> Most methods do not declare their own @Transactional boundaries.
 * They are designed to be called from transactional service methods (e.g., PaymentService.processPayment).
 * This ensures that Payment update and history recording happen atomically.</p>
 */
@Service
public class PaymentHistoryService {

    private final PaymentStatusHistoryRepository historyRepository;
    private final Clock clock;

    /**
     * Constructor with dependency injection.
     *
     * @param historyRepository repository for persisting history records
     * @param clock system clock for generating timestamps (can be mocked in tests)
     */
    @Autowired
    public PaymentHistoryService(PaymentStatusHistoryRepository historyRepository, Clock clock) {
        this.historyRepository = historyRepository;
        this.clock = clock;
    }

    /**
     * Record the initial creation of a payment.
     * This creates the first history entry with fromStatus=null and toStatus=CREATED, triggered by USER.
     *
     * <p><b>Precondition:</b> Payment must be in CREATED state with valid ID, source/destination/amount/currency.
     *
     * <p><b>Transactional Behavior:</b> Does not declare @Transactional; caller must ensure transaction scope.</p>
     *
     * @param payment the newly created payment (must have valid ID and be in CREATED state)
     * @param changedAt the creation timestamp (UTC)
     */
    public void recordCreation(Payment payment, Instant changedAt) {
        String historyId = UUID.randomUUID().toString();
        PaymentStatusHistory history = new PaymentStatusHistory(
                historyId,
                payment.getId(),
                null,  // fromStatus is null for creation record
                PaymentStatus.CREATED,
                TriggeredBy.USER,
                null,  // no error code for successful creation
                "Payment created",
                changedAt
        );
        historyRepository.save(history);
    }

    /**
     * Record a state transition from one status to another.
     * Used for normal transitions (CREATED -> VALIDATED, VALIDATED -> SENT, SENT -> COMPLETED).
     *
     * <p><b>Precondition:</b> Payment must have valid ID and be in a valid state before transition.</p>
     *
     * <p><b>Validation:</b> Null checks for non-optional fields (Payment, toStatus, triggeredBy, changedAt).
     * fromStatus may be null only if this is the initial creation record.</p>
     *
     * <p><b>Transactional Behavior:</b> Does not declare @Transactional; caller must ensure transaction scope.</p>
     *
     * @param payment the payment entity
     * @param fromStatus the status before transition (may be null only for creation)
     * @param toStatus the target status (must not be null)
     * @param triggeredBy who initiated the transition (must not be null)
     * @param notes optional descriptive notes about the transition
     * @param changedAt when the transition occurred (UTC, must not be null)
     * @throws NullPointerException if Payment, toStatus, triggeredBy, or changedAt is null
     */
    public void recordTransition(Payment payment, PaymentStatus fromStatus, PaymentStatus toStatus,
                                 TriggeredBy triggeredBy, String notes, Instant changedAt) {
        if (payment == null) {
            throw new NullPointerException("payment must not be null");
        }
        if (toStatus == null) {
            throw new NullPointerException("toStatus must not be null");
        }
        if (triggeredBy == null) {
            throw new NullPointerException("triggeredBy must not be null");
        }
        if (changedAt == null) {
            throw new NullPointerException("changedAt must not be null");
        }

        String historyId = UUID.randomUUID().toString();
        PaymentStatusHistory history = new PaymentStatusHistory(
                historyId,
                payment.getId(),
                fromStatus,
                toStatus,
                triggeredBy,
                null,  // no error code for successful transitions
                notes,
                changedAt
        );
        historyRepository.save(history);
    }

    /**
     * Record a payment failure with error details.
     * This creates a history record with toStatus=FAILED and captures the error information.
     *
     * <p><b>Precondition:</b> Payment must have valid ID and be in a state that can transition to FAILED.</p>
     *
     * <p><b>Validation:</b> errorCode and changedAt must not be null.
     * fromStatus may be null only in edge cases (typically records indicate prior valid state).</p>
     *
     * <p><b>Transactional Behavior:</b> Does not declare @Transactional; caller must ensure transaction scope.</p>
     *
     * @param payment the payment entity
     * @param fromStatus the status before failure (may be null, but typically populated)
     * @param errorCode the error code (must not be null; e.g., "NETWORK_ERROR")
     * @param errorMessage the human-readable error message (additional context beyond error code)
     * @param notes optional audit notes (should include debugging context but not sensitive PII)
     * @param changedAt when the failure occurred (UTC, must not be null)
     * @throws NullPointerException if errorCode or changedAt is null
     */
    public void recordFailure(Payment payment, PaymentStatus fromStatus, String errorCode,
                             String errorMessage, String notes, Instant changedAt) {
        if (errorCode == null) {
            throw new NullPointerException("errorCode must not be null");
        }
        if (changedAt == null) {
            throw new NullPointerException("changedAt must not be null");
        }
        if (payment == null) {
            throw new NullPointerException("payment must not be null");
        }

        String historyId = UUID.randomUUID().toString();
        PaymentStatusHistory history = new PaymentStatusHistory(
                historyId,
                payment.getId(),
                fromStatus,
                PaymentStatus.FAILED,
                TriggeredBy.SYSTEM,  // failures are recorded by system
                errorCode,
                notes,
                changedAt
        );
        historyRepository.save(history);
    }

    /**
     * Retrieve the complete status transition history for a payment, ordered chronologically.
     * Used to display an audit timeline to users and for consistency validation.
     *
     * <p><b>Ordering:</b> Results are sorted by changedAt in ascending order (oldest to newest).
     * This gives a chronological view of how the payment progressed through states.</p>
     *
     * <p><b>Transactional Behavior:</b> Marked as read-only transaction for query optimization.</p>
     *
     * @param paymentId the payment ID
     * @return list of history records ordered by time (earliest first), empty list if no history
     */
    @Transactional(readOnly = true)
    public List<PaymentStatusHistory> getHistory(String paymentId) {
        return historyRepository.findAllByPaymentIdOrderByChangedAtAsc(paymentId);
    }
}

