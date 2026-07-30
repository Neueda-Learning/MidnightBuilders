package com.example.demo.service;

import com.example.demo.dto.request.CreatePaymentRequest;
import com.example.demo.entity.Account;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.repository.AccountRepository;
import com.example.demo.repository.PaymentStatusHistoryRepository;
import com.example.demo.time.DelaySleeper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(NetworkFailureIntegrationTest.TimeoutSimulationConfig.class)
@Transactional
class NetworkFailureIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PaymentStatusHistoryRepository historyRepository;

    @Test
    void retryExhaustion_shouldFailFromSentToFailed() {
        accountRepository.save(new Account(
                UUID.randomUUID().toString(), "ACC-NETWORK-01", "Network Test Account",
                Instant.now(), Instant.now()
        ));
        var created = paymentService.createPayment(
                new CreatePaymentRequest(
                        "ACC-NETWORK-01", "ACC-DEST-02", new BigDecimal("100.00"),
                        "CNY", "network timeout integration"
                ),
                UUID.randomUUID().toString()
        );

        var result = paymentService.processPayment(created.getPaymentResponse().getId());

        assertEquals("FAILED", result.getCurrentStatus());
        assertEquals("NETWORK_TIMEOUT", result.getErrorCode());
        assertEquals("NETWORK", result.getFailureStage());
        assertEquals(4, result.getAttemptCount());

        var history = historyRepository.findAllByPaymentIdOrderByChangedAtAsc(
                created.getPaymentResponse().getId()
        );
        assertEquals(PaymentStatus.SENT, history.get(3).getFromStatus());
        assertEquals(PaymentStatus.FAILED, history.get(3).getToStatus());
        assertEquals("NETWORK_TIMEOUT", history.get(3).getErrorCode());
    }

    @TestConfiguration
    static class TimeoutSimulationConfig {

        @Bean
        @Primary
        RandomGenerator timeoutRandomGenerator() {
            return new Random() {
                @Override
                public int nextInt(int origin, int bound) {
                    return 20;
                }
            };
        }

        @Bean
        @Primary
        DelaySleeper noOpDelaySleeper() {
            return seconds -> { };
        }
    }
}
