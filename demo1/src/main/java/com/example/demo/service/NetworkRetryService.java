package com.example.demo.service;

import com.example.demo.config.PaymentProperties;
import com.example.demo.dto.internal.ProcessingAttempt;
import com.example.demo.dto.internal.ProcessingResult;
import com.example.demo.entity.Payment;
import com.example.demo.enums.PaymentErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Runs one confirmation attempt plus the configured number of retries. */
@Service
public class NetworkRetryService {

    private final PaymentProcessingSimulator simulator;
    private final int maxRetries;

    public NetworkRetryService(PaymentProcessingSimulator simulator, PaymentProperties paymentProperties) {
        this.simulator = simulator;
        this.maxRetries = paymentProperties.getSimulation().getMaxRetries();
    }

    public ProcessingResult executeConfirmation(Payment payment) {
        List<ProcessingAttempt> attempts = new ArrayList<>();
        int maxAttempts = maxRetries + 1;

        for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
            ProcessingAttempt attempt = simulator.attemptConfirmation(payment, attemptNumber);
            attempts.add(attempt);
            if (!attempt.isTimedOut()) {
                return ProcessingResult.success(attempts);
            }
        }

        return ProcessingResult.failure(
                PaymentErrorCode.NETWORK_TIMEOUT,
                "Network delay exceeded the timeout on " + maxAttempts + " attempts",
                attempts
        );
    }
}
