package com.example.demo.service;

import com.example.demo.entity.Account;
import com.example.demo.entity.Payment;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class AccountValidationServiceTest {

    @Autowired
    private AccountValidationService service;

    @Autowired
    private AccountRepository repository;

    @Test
    void existingPayerAccount_shouldPass() {
        repository.save(new Account(
                UUID.randomUUID().toString(), "ACC-VALID-01", "Valid Account",
                Instant.now(), Instant.now()
        ));

        Account account = service.validatePayerAccount(payment("ACC-VALID-01"));

        assertEquals("ACC-VALID-01", account.getAccountNumber());
    }

    @Test
    void missingPayerAccount_shouldReturnStableErrorCode() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.validatePayerAccount(payment("ACC-MISSING-01"))
        );

        assertEquals("ACCOUNT_NOT_FOUND: Source account does not exist", ex.getMessage());
    }

    private Payment payment(String sourceAccount) {
        Instant now = Instant.parse("2026-07-30T00:00:00Z");
        return new Payment(
                UUID.randomUUID().toString(), sourceAccount, "ACC-DEST-02",
                new BigDecimal("100.00"), "CNY", "account validation",
                PaymentStatus.VALIDATED, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), now, now
        );
    }
}
