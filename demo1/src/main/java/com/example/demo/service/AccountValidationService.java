package com.example.demo.service;

import com.example.demo.entity.Account;
import com.example.demo.entity.Payment;
import com.example.demo.enums.PaymentErrorCode;
import com.example.demo.repository.AccountRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates whether a payer/source account exists in local account master data.
 */
@Service
public class AccountValidationService {

    private final AccountRepository accountRepository;

    public AccountValidationService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public Account validatePayerAccount(Payment payment) {
        Objects.requireNonNull(payment, "payment must not be null");

        String sourceAccount = payment.getSourceAccount();
        if (sourceAccount == null || sourceAccount.trim().isEmpty()) {
            throw new IllegalArgumentException(PaymentErrorCode.INVALID_ACCOUNT.name());
        }

        String normalizedAccountNumber = sourceAccount.trim();
        return accountRepository.findByAccountNumber(normalizedAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        PaymentErrorCode.ACCOUNT_NOT_FOUND.name() + ": Source account does not exist"
                ));
    }
}

