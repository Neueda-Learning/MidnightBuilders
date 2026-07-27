package com.example.demo.repository;

import com.example.demo.entity.Payment;
import com.example.demo.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void findByIdempotencyKey_returnsExistingPayment() {
        Payment saved = paymentRepository.save(buildPayment(
                "pay-1", "idem-1", PaymentStatus.CREATED, Instant.parse("2026-07-27T10:00:00Z")));

        Optional<Payment> found = paymentRepository.findByIdempotencyKey("idem-1");

        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
    }

    @Test
    void findByIdempotencyKey_returnsEmptyWhenNotFound() {
        Optional<Payment> found = paymentRepository.findByIdempotencyKey("missing-key");
        assertTrue(found.isEmpty());
    }

    @Test
    void findAllByOrderByCreatedAtDesc_returnsNewestFirst() {
        paymentRepository.save(buildPayment(
                "pay-old", "idem-old", PaymentStatus.CREATED, Instant.parse("2026-07-27T09:00:00Z")));
        paymentRepository.save(buildPayment(
                "pay-new", "idem-new", PaymentStatus.CREATED, Instant.parse("2026-07-27T11:00:00Z")));

        List<Payment> result = paymentRepository.findAllByOrderByCreatedAtDesc();

        assertEquals(2, result.size());
        assertEquals("pay-new", result.get(0).getId());
        assertEquals("pay-old", result.get(1).getId());
    }

    @Test
    void findAllByStatusOrderByCreatedAtDesc_filtersAndSorts() {
        paymentRepository.save(buildPayment(
                "pay-completed-old", "idem-c1", PaymentStatus.COMPLETED, Instant.parse("2026-07-27T08:00:00Z")));
        paymentRepository.save(buildPayment(
                "pay-created", "idem-created", PaymentStatus.CREATED, Instant.parse("2026-07-27T09:00:00Z")));
        paymentRepository.save(buildPayment(
                "pay-completed-new", "idem-c2", PaymentStatus.COMPLETED, Instant.parse("2026-07-27T10:00:00Z")));

        List<Payment> completed = paymentRepository.findAllByStatusOrderByCreatedAtDesc(PaymentStatus.COMPLETED);

        assertEquals(2, completed.size());
        assertEquals("pay-completed-new", completed.get(0).getId());
        assertEquals("pay-completed-old", completed.get(1).getId());
    }

    @Test
    void save_throwsWhenIdempotencyKeyDuplicated() {
        paymentRepository.save(buildPayment(
                "pay-dup-1", "idem-dup", PaymentStatus.CREATED, Instant.parse("2026-07-27T10:00:00Z")));

        paymentRepository.save(buildPayment(
                "pay-dup-2", "idem-dup", PaymentStatus.CREATED, Instant.parse("2026-07-27T10:01:00Z")));

        assertThrows(DataIntegrityViolationException.class, paymentRepository::flush);
    }

    private Payment buildPayment(String id, String idempotencyKey, PaymentStatus status, Instant createdAt) {
        return new Payment(
                id,
                "ACC001",
                "ACC002",
                new BigDecimal("100.00"),
                "USD",
                "test-ref",
                status,
                idempotencyKey,
                "fp-" + id,
                createdAt,
                createdAt
        );
    }
}
