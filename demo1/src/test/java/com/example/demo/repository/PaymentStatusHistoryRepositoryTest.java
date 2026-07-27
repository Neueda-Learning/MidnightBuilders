package com.example.demo.repository;

import com.example.demo.entity.Payment;
import com.example.demo.entity.PaymentStatusHistory;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.enums.TriggeredBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PaymentStatusHistoryRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentStatusHistoryRepository historyRepository;

    @Test
    void findAllByPaymentIdOrderByChangedAtAsc_returnsAscendingTimeline() {
        Payment payment = paymentRepository.save(buildPayment("pay-h-1", "idem-h-1"));

        historyRepository.save(buildHistory(
                "his-2", payment.getId(), PaymentStatus.CREATED, PaymentStatus.VALIDATED,
                Instant.parse("2026-07-27T10:01:00Z")));
        historyRepository.save(buildHistory(
                "his-1", payment.getId(), null, PaymentStatus.CREATED,
                Instant.parse("2026-07-27T10:00:00Z")));

        List<PaymentStatusHistory> result = historyRepository.findAllByPaymentIdOrderByChangedAtAsc(payment.getId());

        assertEquals(2, result.size());
        assertEquals("his-1", result.get(0).getId());
        assertEquals("his-2", result.get(1).getId());
    }

    @Test
    void findFirstByPaymentIdOrderByChangedAtDesc_returnsLatestRecord() {
        Payment payment = paymentRepository.save(buildPayment("pay-h-2", "idem-h-2"));

        historyRepository.save(buildHistory(
                "his-a", payment.getId(), null, PaymentStatus.CREATED,
                Instant.parse("2026-07-27T10:00:00Z")));
        historyRepository.save(buildHistory(
                "his-b", payment.getId(), PaymentStatus.CREATED, PaymentStatus.VALIDATED,
                Instant.parse("2026-07-27T10:05:00Z")));

        Optional<PaymentStatusHistory> latest = historyRepository.findFirstByPaymentIdOrderByChangedAtDesc(payment.getId());

        assertTrue(latest.isPresent());
        assertEquals("his-b", latest.get().getId());
        assertEquals(PaymentStatus.VALIDATED, latest.get().getToStatus());
    }

    private Payment buildPayment(String id, String idempotencyKey) {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");
        return new Payment(
                id,
                "ACC001",
                "ACC002",
                new BigDecimal("100.00"),
                "USD",
                "test-ref",
                PaymentStatus.CREATED,
                idempotencyKey,
                "fp-" + id,
                now,
                now
        );
    }

    private PaymentStatusHistory buildHistory(String id, String paymentId, PaymentStatus from, PaymentStatus to, Instant changedAt) {
        return new PaymentStatusHistory(
                id,
                paymentId,
                from,
                to,
                TriggeredBy.SYSTEM,
                null,
                "test-history",
                changedAt
        );
    }
}
