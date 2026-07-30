package com.example.demo.service;

import com.example.demo.config.PaymentProperties;
import com.example.demo.dto.internal.ProcessingResult;
import com.example.demo.entity.Payment;
import com.example.demo.enums.PaymentErrorCode;
import com.example.demo.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkRetryServiceTest {

    @Test
    void delayAtTenSeconds_shouldSucceedOnFirstAttemptWithoutRealWaiting() {
        ProcessingResult result = serviceWithDelays(10).executeConfirmation(sentPayment());

        assertTrue(result.isSuccess());
        assertEquals(1, result.getAttemptCount());
        assertEquals(10, result.getAttempts().get(0).getSimulatedDelaySeconds());
    }

    @Test
    void delayAtElevenSeconds_shouldTimeoutThenRetry() {
        ProcessingResult result = serviceWithDelays(11, 8).executeConfirmation(sentPayment());

        assertTrue(result.isSuccess());
        assertEquals(2, result.getAttemptCount());
        assertTrue(result.getAttempts().get(0).isTimedOut());
        assertFalse(result.getAttempts().get(1).isTimedOut());
    }

    @Test
    void fourTimeouts_shouldReturnNetworkTimeout() {
        ProcessingResult result = serviceWithDelays(20, 15, 12, 11).executeConfirmation(sentPayment());

        assertFalse(result.isSuccess());
        assertEquals(PaymentErrorCode.NETWORK_TIMEOUT, result.getErrorCode());
        assertEquals(4, result.getAttemptCount());
    }

    @Test
    void zeroSeconds_shouldSucceedAtLowerBoundary() {
        ProcessingResult result = serviceWithDelays(0).executeConfirmation(sentPayment());

        assertTrue(result.isSuccess());
        assertEquals(0, result.getAttempts().get(0).getActualWaitSeconds());
    }

    private NetworkRetryService serviceWithDelays(int... delays) {
        PaymentProperties properties = new PaymentProperties();
        properties.getSimulation().setMinDelaySeconds(0);
        properties.getSimulation().setMaxDelaySeconds(20);
        properties.getSimulation().setTimeoutSeconds(10);
        properties.getSimulation().setMaxRetries(3);

        PaymentProcessingSimulator simulator = new PaymentProcessingSimulator(
                properties,
                new SequenceRandom(delays),
                seconds -> { }
        );
        return new NetworkRetryService(simulator, properties);
    }

    private Payment sentPayment() {
        Instant now = Instant.parse("2026-07-30T00:00:00Z");
        return new Payment(
                "payment-network-test",
                "ACC001",
                "ACC002",
                new BigDecimal("100.00"),
                "CNY",
                "network test",
                PaymentStatus.SENT,
                "idem-network-test",
                "fingerprint-network-test",
                now,
                now
        );
    }

    private static final class SequenceRandom extends Random {

        private final int[] values;
        private int index;

        private SequenceRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int origin, int bound) {
            int value = values[index++];
            if (value < origin || value >= bound) {
                throw new IllegalArgumentException("fixed value outside requested range");
            }
            return value;
        }
    }
}
