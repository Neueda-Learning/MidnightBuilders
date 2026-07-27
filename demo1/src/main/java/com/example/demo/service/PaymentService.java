package com.example.demo.service;

import com.example.demo.dto.internal.IdempotencyDecision;
import com.example.demo.dto.internal.ProcessingResult;
import com.example.demo.dto.request.CreatePaymentRequest;
import com.example.demo.dto.response.PaymentHistoryResponse;
import com.example.demo.dto.response.PaymentListItemResponse;
import com.example.demo.dto.response.PaymentResponse;
import com.example.demo.dto.response.ProcessPaymentResponse;
import com.example.demo.entity.Payment;
import com.example.demo.entity.PaymentStatusHistory;
import com.example.demo.enums.PaymentErrorCode;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.mapper.PaymentMapper;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.util.RequestFingerprintGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment orchestration service.
 *
 * <p>This service is the single business entry used by PaymentController.</p>
 */
@Service
public class PaymentService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP", "CNY");

    private final PaymentRepository paymentRepository;
    private final PaymentHistoryService paymentHistoryService;
    private final PaymentLifecycleService paymentLifecycleService;
    private final PaymentProcessingSimulator paymentProcessingSimulator;
    private final PaymentMapper paymentMapper;
    private final RequestFingerprintGenerator requestFingerprintGenerator;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentHistoryService paymentHistoryService,
                          PaymentLifecycleService paymentLifecycleService,
                          PaymentProcessingSimulator paymentProcessingSimulator,
                          PaymentMapper paymentMapper,
                          RequestFingerprintGenerator requestFingerprintGenerator,
                          Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentHistoryService = paymentHistoryService;
        this.paymentLifecycleService = paymentLifecycleService;
        this.paymentProcessingSimulator = paymentProcessingSimulator;
        this.paymentMapper = paymentMapper;
        this.requestFingerprintGenerator = requestFingerprintGenerator;
        this.clock = clock;
    }

    @Transactional
    public CreatePaymentResult createPayment(CreatePaymentRequest request, String idempotencyKey) {
        String normalizedKey = normalizeAndValidateIdempotencyKey(idempotencyKey);
        normalizeAndValidateCreateRequest(request);

        IdempotencyDecision decision = checkIdempotency(normalizedKey, request);
        if (decision.isReplay()) {
            return new CreatePaymentResult(paymentMapper.toPaymentResponse(decision.getExistingPayment()), false);
        }
        if (decision.isConflict()) {
            throw new IllegalStateException(PaymentErrorCode.DUPLICATE_PAYMENT.name());
        }

        Instant now = Instant.now(clock);
        Payment payment = paymentMapper.toEntity(request, normalizedKey, decision.getFingerprint(), now);
        try {
            Payment saved = paymentRepository.save(payment);
            paymentHistoryService.recordCreation(saved, now);
            return new CreatePaymentResult(paymentMapper.toPaymentResponse(saved), true);
        } catch (DataIntegrityViolationException ex) {
            return handleConcurrentDuplicate(normalizedKey, decision.getFingerprint(), ex);
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(String paymentId) {
        Payment payment = findPaymentOrThrow(paymentId);
        return paymentMapper.toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentListItemResponse> listPayments(String status) {
        List<Payment> payments;
        if (status == null || status.trim().isEmpty()) {
            payments = paymentRepository.findAllByOrderByCreatedAtDesc();
        } else {
            PaymentStatus parsedStatus = parseStatus(status);
            payments = paymentRepository.findAllByStatusOrderByCreatedAtDesc(parsedStatus);
        }

        return payments.stream()
                .map(paymentMapper::toListItemResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProcessPaymentResponse processPayment(String paymentId) {
        Payment current = findPaymentOrThrow(paymentId);
        PaymentStatus previousStatus = current.getStatus();

        if (previousStatus != PaymentStatus.CREATED) {
            throw new IllegalStateException(PaymentErrorCode.INVALID_STATUS_TRANSITION.name());
        }

        try {
            validatePaymentForProcessing(current);
        } catch (RuntimeException ex) {
            Payment failed = paymentLifecycleService.markFailed(
                    current,
                    extractErrorCode(ex),
                    "Payment validation failed",
                    ex.getMessage()
            );
            return toFailedResponse(failed, previousStatus);
        }

        try {
            current = paymentLifecycleService.markValidated(current);

            ProcessingResult sendResult = paymentProcessingSimulator.sendPayment(current);
            if (!sendResult.isSuccess()) {
                Payment failed = paymentLifecycleService.markFailed(
                        current,
                        sendResult.getErrorCode(),
                        sendResult.getErrorMessage(),
                        "Payment send stage failed"
                );
                return toFailedResponse(failed, previousStatus);
            }

            current = paymentLifecycleService.markSent(current);

            ProcessingResult confirmResult = paymentProcessingSimulator.confirmPayment(current);
            if (!confirmResult.isSuccess()) {
                Payment failed = paymentLifecycleService.markFailed(
                        current,
                        confirmResult.getErrorCode(),
                        confirmResult.getErrorMessage(),
                        "Payment confirm stage failed"
                );
                return toFailedResponse(failed, previousStatus);
            }

            current = paymentLifecycleService.markCompleted(current);
            return new ProcessPaymentResponse(
                    current.getId(),
                    previousStatus.name(),
                    current.getStatus().name(),
                    "Payment processed successfully",
                    null,
                    null
            );
        } catch (RuntimeException ex) {
            Payment failed = paymentLifecycleService.markFailed(
                    current,
                    PaymentErrorCode.PROCESSING_ERROR,
                    "Payment processing failed",
                    ex.getMessage()
            );
            return toFailedResponse(failed, previousStatus);
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getPaymentHistory(String paymentId) {
        findPaymentOrThrow(paymentId);

        List<PaymentStatusHistory> history = paymentHistoryService.getHistory(paymentId);
        return history.stream()
                .map(paymentMapper::toHistoryResponse)
                .collect(Collectors.toList());
    }

    private Payment findPaymentOrThrow(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NoSuchElementException(PaymentErrorCode.PAYMENT_NOT_FOUND.name()));
    }

    private PaymentStatus parseStatus(String statusText) {
        try {
            return PaymentStatus.valueOf(statusText.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(PaymentErrorCode.VALIDATION_FAILED.name());
        }
    }

    private String normalizeAndValidateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException(PaymentErrorCode.VALIDATION_FAILED.name());
        }

        String normalized = idempotencyKey.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new IllegalArgumentException(PaymentErrorCode.VALIDATION_FAILED.name());
        }
        return normalized;
    }

    private void normalizeAndValidateCreateRequest(CreatePaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(PaymentErrorCode.VALIDATION_FAILED.name());
        }

        String source = normalizeAccount(request.getSourceAccount());
        String destination = normalizeAccount(request.getDestinationAccount());
        if (source.equals(destination)) {
            throw new IllegalArgumentException(PaymentErrorCode.SAME_SOURCE_AND_DESTINATION.name());
        }

        validateAmount(request.getAmount());

        String normalizedCurrency = normalizeCurrency(request.getCurrency());
        if (!SUPPORTED_CURRENCIES.contains(normalizedCurrency)) {
            throw new IllegalArgumentException(PaymentErrorCode.INVALID_CURRENCY.name());
        }

        String reference = request.getReference() == null ? null : request.getReference().trim();
        if (reference != null && reference.length() > 255) {
            throw new IllegalArgumentException(PaymentErrorCode.VALIDATION_FAILED.name());
        }

        request.setSourceAccount(source);
        request.setDestinationAccount(destination);
        request.setCurrency(normalizedCurrency);
        request.setReference(reference);
    }

    private void validatePaymentForProcessing(Payment payment) {
        Objects.requireNonNull(payment, "payment must not be null");

        normalizeAccount(payment.getSourceAccount());
        normalizeAccount(payment.getDestinationAccount());
        if (payment.getSourceAccount().trim().equals(payment.getDestinationAccount().trim())) {
            throw new IllegalArgumentException(PaymentErrorCode.SAME_SOURCE_AND_DESTINATION.name());
        }

        validateAmount(payment.getAmount());

        String normalizedCurrency = normalizeCurrency(payment.getCurrency());
        if (!SUPPORTED_CURRENCIES.contains(normalizedCurrency)) {
            throw new IllegalArgumentException(PaymentErrorCode.INVALID_CURRENCY.name());
        }
    }

    private String normalizeAccount(String account) {
        if (account == null) {
            throw new IllegalArgumentException(PaymentErrorCode.INVALID_ACCOUNT.name());
        }
        String normalized = account.trim();
        if (normalized.isEmpty() || normalized.length() > 50) {
            throw new IllegalArgumentException(PaymentErrorCode.INVALID_ACCOUNT.name());
        }
        return normalized;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null) {
            throw new IllegalArgumentException(PaymentErrorCode.INVALID_CURRENCY.name());
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3 || !normalized.chars().allMatch(Character::isLetter)) {
            throw new IllegalArgumentException(PaymentErrorCode.INVALID_CURRENCY.name());
        }
        return normalized;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.compareTo(MAX_AMOUNT) > 0
                || amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(PaymentErrorCode.INVALID_AMOUNT.name());
        }
    }

    private IdempotencyDecision checkIdempotency(String normalizedKey, CreatePaymentRequest request) {
        String requestFingerprint = requestFingerprintGenerator.generate(request);
        Payment existing = paymentRepository.findByIdempotencyKey(normalizedKey).orElse(null);
        if (existing == null) {
            return IdempotencyDecision.allowCreate(requestFingerprint);
        }
        if (requestFingerprint.equals(existing.getRequestFingerprint())) {
            return IdempotencyDecision.replay(requestFingerprint, existing);
        }
        return IdempotencyDecision.conflict(requestFingerprint, existing.getRequestFingerprint());
    }

    private CreatePaymentResult handleConcurrentDuplicate(String normalizedKey,
                                                          String currentFingerprint,
                                                          DataIntegrityViolationException ex) {
        Payment existing = paymentRepository.findByIdempotencyKey(normalizedKey).orElse(null);
        if (existing == null) {
            throw ex;
        }

        if (currentFingerprint.equals(existing.getRequestFingerprint())) {
            return new CreatePaymentResult(paymentMapper.toPaymentResponse(existing), false);
        }

        throw new IllegalStateException(PaymentErrorCode.DUPLICATE_PAYMENT.name());
    }

    private PaymentErrorCode extractErrorCode(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null) {
            return PaymentErrorCode.VALIDATION_FAILED;
        }

        try {
            return PaymentErrorCode.valueOf(ex.getMessage().trim());
        } catch (IllegalArgumentException ignored) {
            return PaymentErrorCode.VALIDATION_FAILED;
        }
    }

    private ProcessPaymentResponse toFailedResponse(Payment failed, PaymentStatus previousStatus) {
        return new ProcessPaymentResponse(
                failed.getId(),
                previousStatus.name(),
                failed.getStatus().name(),
                "Payment processing failed",
                failed.getErrorCode(),
                failed.getErrorMessage()
        );
    }

    /**
     * Controller-facing create result wrapper.
     */
    public static class CreatePaymentResult {
        private final PaymentResponse paymentResponse;
        private final boolean created;

        public CreatePaymentResult(PaymentResponse paymentResponse, boolean created) {
            this.paymentResponse = paymentResponse;
            this.created = created;
        }

        public PaymentResponse getPaymentResponse() {
            return paymentResponse;
        }

        public boolean isCreated() {
            return created;
        }
    }
}
