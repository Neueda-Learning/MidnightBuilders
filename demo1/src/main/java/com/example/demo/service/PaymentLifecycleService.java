package com.example.demo.service;

import com.example.demo.entity.Payment;
import com.example.demo.enums.PaymentErrorCode;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.enums.TriggeredBy;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.statemachine.PaymentStateMachine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Service responsible for applying payment lifecycle state changes.
 *
 * <p><b>Responsibility:</b> This service is the single write path for payment status updates
 * after creation. It enforces state-machine rules, updates the {@link Payment} entity,
 * and records audit history through {@link PaymentHistoryService} in the same transaction.</p>
 *
 * <p><b>Why this layer exists:</b></p>
 * <ul>
 *   <li>Prevents direct status mutation from controllers or orchestration code</li>
 *   <li>Keeps transition validation centralized via {@link PaymentStateMachine}</li>
 *   <li>Guarantees payment update and history insertion are committed atomically</li>
 *   <li>Standardizes SYSTEM-triggered notes and error recording</li>
 * </ul>
 *
 * <p><b>Transaction model:</b> Each {@code mark...} method is transactional. If repository save
 * or history write fails, the whole transition is rolled back to avoid payment/history mismatch.</p>
 */
@Service
public class PaymentLifecycleService {

    private final PaymentStateMachine stateMachine;
    private final PaymentRepository paymentRepository;
    private final PaymentHistoryService paymentHistoryService;
    private final Clock clock;

    /**
     * Constructor with required collaborators.
     *
     * @param stateMachine transition rule validator
     * @param paymentRepository payment persistence access
     * @param paymentHistoryService audit trail recording service
     * @param clock UTC/system clock (replaceable in tests)
     */
    @Autowired
    public PaymentLifecycleService(PaymentStateMachine stateMachine,
                                   PaymentRepository paymentRepository,
                                   PaymentHistoryService paymentHistoryService,
                                   Clock clock) {
        this.stateMachine = stateMachine;
        this.paymentRepository = paymentRepository;
        this.paymentHistoryService = paymentHistoryService;
        this.clock = clock;
    }

    /**
     * Move payment from CREATED to VALIDATED.
     *
     * <p><b>Expected caller:</b> payment processing orchestration flow after business validation passes.</p>
     *
     * @param payment payment to update; must not be null
     * @return persisted payment after transition
     * @throws NullPointerException if payment is null
     * @throws IllegalStateException if CREATED -> VALIDATED is not allowed for current state
     */
    @Transactional
    public Payment markValidated(Payment payment) {
        return markSuccessTransition(payment, PaymentStatus.VALIDATED, "Payment validation passed");
    }

    /**
     * Move payment from VALIDATED to SENT.
     *
     * <p><b>Expected caller:</b> orchestration flow after simulator/external send step succeeds.</p>
     *
     * @param payment payment to update; must not be null
     * @return persisted payment after transition
     * @throws NullPointerException if payment is null
     * @throws IllegalStateException if VALIDATED -> SENT is not allowed for current state
     */
    @Transactional
    public Payment markSent(Payment payment) {
        return markSuccessTransition(payment, PaymentStatus.SENT, "Payment sent to target system");
    }

    /**
     * Move payment from SENT to COMPLETED.
     *
     * <p>Any stale failure fields are cleared because COMPLETED is a success terminal state.</p>
     *
     * @param payment payment to update; must not be null
     * @return persisted payment after transition
     * @throws NullPointerException if payment is null
     * @throws IllegalStateException if SENT -> COMPLETED is not allowed for current state
     */
    @Transactional
    public Payment markCompleted(Payment payment) {
        return markSuccessTransition(payment, PaymentStatus.COMPLETED, "Payment processing completed");
    }

    /**
     * Move payment to FAILED and store error details.
     *
     * <p><b>Usage:</b> called when processing cannot continue due to validation, simulator,
     * or downstream failures.</p>
     *
     * <p><b>Validation:</b> error code and message are required so failures remain actionable
     * in API responses and audit logs.</p>
     *
     * @param payment payment to update; must not be null
     * @param errorCode stable business error code; must not be null
     * @param errorMessage human-readable failure reason; must not be blank
     * @param notes optional audit context
     * @return persisted payment in FAILED state
     * @throws NullPointerException if payment or errorCode is null
     * @throws IllegalArgumentException if errorMessage is blank
     * @throws IllegalStateException if transition to FAILED is not allowed for current state
     */
    @Transactional
    public Payment markFailed(Payment payment,
                              PaymentErrorCode errorCode,
                              String errorMessage,
                              String notes) {
        Objects.requireNonNull(payment, "payment must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");

        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("errorMessage must not be blank");
        }

        PaymentStatus fromStatus = Objects.requireNonNull(payment.getStatus(), "payment status must not be null");
        stateMachine.validateTransition(fromStatus, PaymentStatus.FAILED);

        Instant now = Instant.now(clock);
        payment.markFailed(errorCode.name(), errorMessage.trim(), now);
        Payment saved = paymentRepository.save(payment);

        paymentHistoryService.recordFailure(
                saved,
                fromStatus,
                errorCode.name(),
                errorMessage.trim(),
                notes,
                now
        );

        return saved;
    }

    /**
     * Shared implementation for successful transitions (VALIDATED, SENT, COMPLETED).
     *
     * <p>This helper validates transition legality, clears stale failure fields,
     * updates status/timestamp, persists payment, and writes one transition history record.</p>
     */
    private Payment markSuccessTransition(Payment payment, PaymentStatus targetStatus, String notes) {
        Objects.requireNonNull(payment, "payment must not be null");
        Objects.requireNonNull(targetStatus, "targetStatus must not be null");

        PaymentStatus fromStatus = Objects.requireNonNull(payment.getStatus(), "payment status must not be null");
        stateMachine.validateTransition(fromStatus, targetStatus);

        Instant now = Instant.now(clock);
        payment.clearFailure();
        payment.changeStatus(targetStatus, now);
        Payment saved = paymentRepository.save(payment);

        paymentHistoryService.recordTransition(
                saved,
                fromStatus,
                targetStatus,
                TriggeredBy.SYSTEM,
                notes,
                now
        );

        return saved;
    }
}

