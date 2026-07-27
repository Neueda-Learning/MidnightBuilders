package com.example.demo.mapper;

import com.example.demo.dto.request.CreatePaymentRequest;
import com.example.demo.dto.response.PaymentHistoryResponse;
import com.example.demo.dto.response.PaymentListItemResponse;
import com.example.demo.dto.response.PaymentResponse;
import com.example.demo.entity.Payment;
import com.example.demo.entity.PaymentStatusHistory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Component responsible for converting between Entity, Request DTO, and Response DTO objects.
 *
 * <p><b>Responsibility:</b> Encapsulate all object transformations between persistence and API layers.
 * This separation ensures that response structure can evolve independently from database schema.</p>
 *
 * <p><b>Key Principles:</b></p>
 * <ul>
 *   <li>Response DTOs never include internal fields (idempotency key, request fingerprint, database IDs)</li>
 *   <li>Entity-to-Response conversions are the primary direction (read-heavy operations)</li>
 *   <li>Request-to-Entity conversion is used only during payment creation</li>
 *   <li>All timestamp conversions use UTC and ISO 8601 format for API responses</li>
 *   <li>BigDecimal amounts are preserved (not converted to strings or doubles)</li>
 * </ul>
 *
 * <p><b>Timestamp Format:</b> Instants are formatted as ISO 8601 strings in UTC (e.g., "2026-07-25T10:00:00Z").
 * This format is timezone-agnostic and directly comparable across systems.</p>
 */
@Component
public class PaymentMapper {

    /**
     * ISO 8601 formatter for timestamps in UTC.
     * Produces strings like "2026-07-25T10:00:00Z" which are universally understood.
     */
    private static final DateTimeFormatter ISO_8601_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * Convert a Create Payment Request to a Payment Entity for first-time storage.
     *
     * <p><b>Behavior:</b></p>
     * <ul>
     *   <li>Generates a new UUID as the payment ID</li>
     *   <li>Forces initial status to CREATED (ignores any client-provided status)</li>
     *   <li>Sets createdAt and updatedAt to the same timestamp (now)</li>
     *   <li>Stores the idempotency key and request fingerprint for deduplication</li>
     *   <li>Does NOT set error fields (those are null in newly created payments)</li>
     * </ul>
     *
     * <p><b>Important:</b> Account/amount/currency normalization must occur in PaymentValidationService
     * before this mapper is called. The mapper stores normalized values as-is.</p>
     *
     * @param request the validated CreatePaymentRequest
     * @param idempotencyKey the idempotency key from HTTP header
     * @param requestFingerprint the stable fingerprint of request content
     * @param now the creation timestamp (UTC). Typically obtained from Clock bean
     * @return a new Payment entity ready for persistence
     */
    public Payment toEntity(CreatePaymentRequest request, String idempotencyKey,
                           String requestFingerprint, Instant now) {
        String paymentId = UUID.randomUUID().toString();

        return new Payment(
                paymentId,
                request.getSourceAccount(),
                request.getDestinationAccount(),
                request.getAmount(),
                request.getCurrency(),
                request.getReference(),
                com.example.demo.enums.PaymentStatus.CREATED,  // force initial status
                idempotencyKey,
                requestFingerprint,
                now,  // createdAt
                now   // updatedAt (same as created for new payments)
        );
    }

    /**
     * Convert a Payment Entity to a detailed response DTO.
     *
     * <p><b>Usage:</b> GET /api/payments/{id} and POST /api/payments response.
     * Includes all payment business fields plus current status and timestamps.</p>
     *
     * <p><b>Excluded Fields:</b> idempotencyKey, requestFingerprint (internal).
     * errorCode and errorMessage are included only if payment is in FAILED state.</p>
     *
     * <p><b>Timestamp Format:</b> Instant fields are converted to ISO 8601 strings for JSON serialization.</p>
     *
     * @param payment the Payment entity
     * @return a PaymentResponse ready for JSON serialization
     */
    public PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getSourceAccount(),
                payment.getDestinationAccount(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getReference(),
                payment.getStatus().toString(),
                payment.getErrorCode(),
                payment.getErrorMessage(),
                formatInstant(payment.getCreatedAt()),
                formatInstant(payment.getUpdatedAt())
        );
    }

    /**
     * Convert a Payment Entity to a list item response DTO.
     *
     * <p><b>Usage:</b> GET /api/payments (list query).
     * Includes only essential fields for compact list display: id, amount, currency, status, createdAt, errorCode.</p>
     *
     * <p><b>Rationale:</b> List responses exclude account details and full error messages to reduce payload size
     * and API coupling. Detailed information is fetched via the detail endpoint.</p>
     *
     * @param payment the Payment entity
     * @return a PaymentListItemResponse ready for JSON serialization
     */
    public PaymentListItemResponse toListItemResponse(Payment payment) {
        return new PaymentListItemResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().toString(),
                formatInstant(payment.getCreatedAt()),
                payment.getErrorCode()
        );
    }

    /**
     * Convert a Payment Status History Entity to a history response DTO.
     *
     * <p><b>Usage:</b> GET /api/payments/{id}/history (audit trail query).
     * Represents a single transition event in the payment's lifecycle.</p>
     *
     * <p><b>Excluded Fields:</b> history record ID, payment ID (these are internal identifiers).
     * Only business-relevant data is exposed: state transition info, who triggered it, when, and why.</p>
     *
     * <p><b>Note:</b> fromStatus may be null for the initial creation record.
     * The JSON serialization respects null values (see @JsonInclude annotation on DTO).</p>
     *
     * @param history the PaymentStatusHistory entity
     * @return a PaymentHistoryResponse ready for JSON serialization
     */
    public PaymentHistoryResponse toHistoryResponse(PaymentStatusHistory history) {
        return new PaymentHistoryResponse(
                history.getFromStatus() != null ? history.getFromStatus().toString() : null,
                history.getToStatus().toString(),
                history.getTriggeredBy().toString(),
                history.getErrorCode(),
                history.getNotes(),
                formatInstant(history.getChangedAt())
        );
    }

    /**
     * Format an Instant to ISO 8601 string in UTC.
     *
     * <p>This is a utility method to ensure consistent timestamp formatting across all DTOs.
     * Examples: "2026-07-25T10:00:00Z", "2026-07-27T15:30:45.123Z"</p>
     *
     * @param instant the Instant to format (must not be null in practice)
     * @return ISO 8601 formatted string, or null if instant is null
     */
    private String formatInstant(Instant instant) {
        if (instant == null) {
            return null;
        }
        return ISO_8601_FORMATTER.format(instant);
    }
}

