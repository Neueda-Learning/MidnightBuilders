package com.example.demo.service;

import com.example.demo.dto.internal.ProcessingResult;
import com.example.demo.entity.Payment;
import com.example.demo.enums.PaymentErrorCode;
import com.example.demo.enums.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * Simulates external payment processing interactions for iteration-1.
 *
 * <p><b>Responsibility:</b> Provide a deterministic service boundary that mimics
 * external send/confirm steps while keeping the current iteration simple.
 * This service does not write database state and does not perform lifecycle
 * transitions directly; it only returns {@link ProcessingResult}.</p>
 *
 * <p><b>Current strategy:</b> Default behavior is success for both steps when input
 * is valid. Failures can still be produced for invalid inputs or unexpected runtime
 * errors, and are mapped to stable business error codes.</p>
 *
 * <p><b>Error mapping policy:</b></p>
 * <ul>
 *   <li>Network-like exceptions map to {@link PaymentErrorCode#NETWORK_ERROR}</li>
 *   <li>Other exceptions map to {@link PaymentErrorCode#PROCESSING_ERROR}</li>
 * </ul>
 */
@Service
public class PaymentProcessingSimulator {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingSimulator.class);

    /**
     * Simulate sending a payment to an external target system.
     *
     * <p>This method is intentionally side-effect free: it does not update payment status,
     * write history, or call repositories. Lifecycle transitions are handled by
     * {@code PaymentLifecycleService} after this result is returned.</p>
     *
     * @param payment payment context for simulation, must not be null
     * @return successful result when input is valid in current strategy, otherwise failed result
     */
    public ProcessingResult sendPayment(Payment payment) {
        return simulateStep("send", payment, PaymentStatus.VALIDATED);
    }

    /**
     * Simulate final confirmation from an external target system.
     *
     * <p>Like {@link #sendPayment(Payment)}, this method returns an outcome only and does not
     * mutate persistent state directly.</p>
     *
     * @param payment payment context for simulation, must not be null
     * @return successful result when input is valid in current strategy, otherwise failed result
     */
    public ProcessingResult confirmPayment(Payment payment) {
        return simulateStep("confirm", payment, PaymentStatus.SENT);
    }

    /**
     * Convert internal simulator exceptions to stable processing failures.
     *
     * <p>Returned messages are safe for client exposure and avoid leaking stack traces.</p>
     *
     * @param stage logical simulator stage, such as "send" or "confirm"
     * @param cause root cause exception
     * @return mapped failure result
     */
    public ProcessingResult toFailureResult(String stage, Throwable cause) {
        String normalizedStage = (stage == null || stage.trim().isEmpty()) ? "processing" : stage.trim();

        if (isNetworkException(cause)) {
            String msg = "Payment " + normalizedStage + " failed due to network issue";
            return ProcessingResult.failure(PaymentErrorCode.NETWORK_ERROR, msg);
        }

        String msg = "Payment " + normalizedStage + " failed due to internal processing error";
        return ProcessingResult.failure(PaymentErrorCode.PROCESSING_ERROR, msg);
    }

    /**
     * Shared simulator implementation for send/confirm stages.
     */
    private ProcessingResult simulateStep(String stage, Payment payment, PaymentStatus expectedStatus) {
        try {
            validateInput(payment, expectedStatus, stage);
            return ProcessingResult.success();
        } catch (Exception ex) {
            log.warn("Payment simulator {} stage failed: {}", stage, ex.getMessage());
            log.debug("Payment simulator {} stage exception", stage, ex);
            return toFailureResult(stage, ex);
        }
    }

    /**
     * Validate required input semantics for the current stage.
     *
     * <p>The simulator expects lifecycle orchestration to call steps in order:
     * send after VALIDATED, confirm after SENT.</p>
     */
    private void validateInput(Payment payment, PaymentStatus expectedStatus, String stage) {
        Objects.requireNonNull(payment, "payment must not be null");
        Objects.requireNonNull(expectedStatus, "expectedStatus must not be null");

        if (payment.getId() == null || payment.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("payment id must not be blank");
        }

        PaymentStatus actualStatus = payment.getStatus();
        if (actualStatus == null) {
            throw new IllegalArgumentException("payment status must not be null");
        }

        if (actualStatus != expectedStatus) {
            throw new IllegalStateException(
                    "cannot " + stage + " payment in status " + actualStatus +
                            "; expected " + expectedStatus
            );
        }
    }

    /**
     * Determine whether an exception should be classified as network-related.
     */
    private boolean isNetworkException(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof UnknownHostException
                    || current instanceof SocketException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
