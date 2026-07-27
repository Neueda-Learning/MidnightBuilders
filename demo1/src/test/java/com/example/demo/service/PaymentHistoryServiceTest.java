package com.example.demo.service;

import com.example.demo.entity.Payment;
import com.example.demo.entity.PaymentStatusHistory;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.enums.TriggeredBy;
import com.example.demo.repository.PaymentStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentHistoryServiceTest {

    @Mock
    private PaymentStatusHistoryRepository historyRepository;

    private PaymentHistoryService paymentHistoryService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC);
        paymentHistoryService = new PaymentHistoryService(historyRepository, fixedClock);
    }

    @Test
    void recordCreation_savesCreatedEntryWithUserTrigger() {
        Payment payment = buildPayment("pay-1");
        Instant changedAt = Instant.parse("2026-07-27T10:00:00Z");

        paymentHistoryService.recordCreation(payment, changedAt);

        ArgumentCaptor<PaymentStatusHistory> captor = ArgumentCaptor.forClass(PaymentStatusHistory.class);
        verify(historyRepository).save(captor.capture());

        PaymentStatusHistory saved = captor.getValue();
        assertNull(saved.getFromStatus());
        assertEquals(PaymentStatus.CREATED, saved.getToStatus());
        assertEquals(TriggeredBy.USER, saved.getTriggeredBy());
        assertEquals(payment.getId(), saved.getPaymentId());
        assertEquals(changedAt, saved.getChangedAt());
    }

    @Test
    void recordTransition_savesTransitionEntry() {
        Payment payment = buildPayment("pay-2");
        Instant changedAt = Instant.parse("2026-07-27T10:01:00Z");

        paymentHistoryService.recordTransition(
                payment,
                PaymentStatus.CREATED,
                PaymentStatus.VALIDATED,
                TriggeredBy.SYSTEM,
                "validated",
                changedAt
        );

        ArgumentCaptor<PaymentStatusHistory> captor = ArgumentCaptor.forClass(PaymentStatusHistory.class);
        verify(historyRepository).save(captor.capture());

        PaymentStatusHistory saved = captor.getValue();
        assertEquals(PaymentStatus.CREATED, saved.getFromStatus());
        assertEquals(PaymentStatus.VALIDATED, saved.getToStatus());
        assertEquals(TriggeredBy.SYSTEM, saved.getTriggeredBy());
        assertEquals("validated", saved.getNotes());
        assertEquals(changedAt, saved.getChangedAt());
    }

    @Test
    void recordFailure_savesFailedEntryWithErrorCode() {
        Payment payment = buildPayment("pay-3");
        Instant changedAt = Instant.parse("2026-07-27T10:02:00Z");

        paymentHistoryService.recordFailure(
                payment,
                PaymentStatus.SENT,
                "NETWORK_ERROR",
                "timeout",
                "send failed",
                changedAt
        );

        ArgumentCaptor<PaymentStatusHistory> captor = ArgumentCaptor.forClass(PaymentStatusHistory.class);
        verify(historyRepository).save(captor.capture());

        PaymentStatusHistory saved = captor.getValue();
        assertEquals(PaymentStatus.SENT, saved.getFromStatus());
        assertEquals(PaymentStatus.FAILED, saved.getToStatus());
        assertEquals(TriggeredBy.SYSTEM, saved.getTriggeredBy());
        assertEquals("NETWORK_ERROR", saved.getErrorCode());
        assertEquals(changedAt, saved.getChangedAt());
    }

    @Test
    void getHistory_returnsAscendingHistoryList() {
        List<PaymentStatusHistory> expected = List.of(
                buildHistory("h1", "pay-x", null, PaymentStatus.CREATED, Instant.parse("2026-07-27T10:00:00Z")),
                buildHistory("h2", "pay-x", PaymentStatus.CREATED, PaymentStatus.VALIDATED, Instant.parse("2026-07-27T10:01:00Z"))
        );
        when(historyRepository.findAllByPaymentIdOrderByChangedAtAsc("pay-x")).thenReturn(expected);

        List<PaymentStatusHistory> actual = paymentHistoryService.getHistory("pay-x");

        assertEquals(2, actual.size());
        assertEquals("h1", actual.get(0).getId());
        assertEquals("h2", actual.get(1).getId());
    }

    @Test
    void recordTransition_throwsWhenRequiredArgumentsAreNull() {
        Payment payment = buildPayment("pay-4");
        Instant changedAt = Instant.parse("2026-07-27T10:00:00Z");

        assertThrows(NullPointerException.class,
                () -> paymentHistoryService.recordTransition(null, PaymentStatus.CREATED, PaymentStatus.VALIDATED, TriggeredBy.SYSTEM, "n", changedAt));
        assertThrows(NullPointerException.class,
                () -> paymentHistoryService.recordTransition(payment, PaymentStatus.CREATED, null, TriggeredBy.SYSTEM, "n", changedAt));
        assertThrows(NullPointerException.class,
                () -> paymentHistoryService.recordTransition(payment, PaymentStatus.CREATED, PaymentStatus.VALIDATED, null, "n", changedAt));
        assertThrows(NullPointerException.class,
                () -> paymentHistoryService.recordTransition(payment, PaymentStatus.CREATED, PaymentStatus.VALIDATED, TriggeredBy.SYSTEM, "n", null));
    }

    @Test
    void recordFailure_throwsWhenRequiredArgumentsAreNull() {
        Payment payment = buildPayment("pay-5");
        Instant changedAt = Instant.parse("2026-07-27T10:00:00Z");

        assertThrows(NullPointerException.class,
                () -> paymentHistoryService.recordFailure(payment, PaymentStatus.CREATED, null, "msg", "n", changedAt));
        assertThrows(NullPointerException.class,
                () -> paymentHistoryService.recordFailure(payment, PaymentStatus.CREATED, "ERR", "msg", "n", null));
        assertThrows(NullPointerException.class,
                () -> paymentHistoryService.recordFailure(null, PaymentStatus.CREATED, "ERR", "msg", "n", changedAt));
    }

    private Payment buildPayment(String id) {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");
        return new Payment(
                id,
                "ACC001",
                "ACC002",
                new BigDecimal("100.00"),
                "USD",
                "ref",
                PaymentStatus.CREATED,
                "idem-" + id,
                "fp-" + id,
                now,
                now
        );
    }

    private PaymentStatusHistory buildHistory(String id, String paymentId, PaymentStatus from, PaymentStatus to, Instant changedAt) {
        return new PaymentStatusHistory(id, paymentId, from, to, TriggeredBy.SYSTEM, null, "note", changedAt);
    }
}
