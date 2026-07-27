package com.example.demo.mapper;

import com.example.demo.dto.request.CreatePaymentRequest;
import com.example.demo.dto.response.PaymentHistoryResponse;
import com.example.demo.dto.response.PaymentListItemResponse;
import com.example.demo.dto.response.PaymentResponse;
import com.example.demo.entity.Payment;
import com.example.demo.entity.PaymentStatusHistory;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.enums.TriggeredBy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMapperTest {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final PaymentMapper paymentMapper = new PaymentMapper();

    @Test
    void toEntity_generatesIdAndCreatedStatus() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                "ACC001",
                "ACC002",
                new BigDecimal("100.00"),
                "USD",
                "invoice"
        );
        Instant now = Instant.parse("2026-07-27T10:00:00Z");

        Payment entity = paymentMapper.toEntity(request, "idem-1", "fp-1", now);

        assertNotNull(entity.getId());
        assertTrue(UUID_PATTERN.matcher(entity.getId()).matches());
        assertEquals(PaymentStatus.CREATED, entity.getStatus());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
        assertEquals("idem-1", entity.getIdempotencyKey());
        assertEquals("fp-1", entity.getRequestFingerprint());
    }

    @Test
    void toPaymentResponse_mapsBusinessFieldsOnly() {
        Payment payment = buildPayment();

        PaymentResponse response = paymentMapper.toPaymentResponse(payment);

        assertEquals(payment.getId(), response.getId());
        assertEquals(payment.getSourceAccount(), response.getSourceAccount());
        assertEquals(payment.getDestinationAccount(), response.getDestinationAccount());
        assertEquals(payment.getAmount(), response.getAmount());
        assertEquals(payment.getCurrency(), response.getCurrency());
        assertEquals(payment.getStatus().name(), response.getStatus());
        assertEquals("2026-07-27T10:00:00Z", response.getCreatedAt());
        assertEquals("2026-07-27T10:01:00Z", response.getUpdatedAt());
    }

    @Test
    void toListItemResponse_mapsOnlyListFields() {
        Payment payment = buildPayment();

        PaymentListItemResponse listItem = paymentMapper.toListItemResponse(payment);

        assertEquals(payment.getId(), listItem.getId());
        assertEquals(payment.getAmount(), listItem.getAmount());
        assertEquals(payment.getCurrency(), listItem.getCurrency());
        assertEquals(payment.getStatus().name(), listItem.getStatus());
        assertEquals("2026-07-27T10:00:00Z", listItem.getCreatedAt());
        assertEquals(payment.getErrorCode(), listItem.getErrorCode());
    }

    @Test
    void toHistoryResponse_supportsNullFromStatusAndIsoTime() {
        PaymentStatusHistory history = new PaymentStatusHistory(
                "h-1",
                "pay-1",
                null,
                PaymentStatus.CREATED,
                TriggeredBy.USER,
                null,
                "created",
                Instant.parse("2026-07-27T10:00:00Z")
        );

        PaymentHistoryResponse response = paymentMapper.toHistoryResponse(history);

        assertNull(response.getFromStatus());
        assertEquals("CREATED", response.getToStatus());
        assertEquals("USER", response.getTriggeredBy());
        assertEquals("created", response.getNotes());
        assertEquals("2026-07-27T10:00:00Z", response.getChangedAt());
    }

    private Payment buildPayment() {
        Payment payment = new Payment(
                "pay-1",
                "ACC001",
                "ACC002",
                new BigDecimal("100.00"),
                "USD",
                "invoice",
                PaymentStatus.FAILED,
                "idem-1",
                "fp-1",
                Instant.parse("2026-07-27T10:00:00Z"),
                Instant.parse("2026-07-27T10:01:00Z")
        );
        payment.setErrorCode("PROCESSING_ERROR");
        payment.setErrorMessage("failed");
        return payment;
    }
}

