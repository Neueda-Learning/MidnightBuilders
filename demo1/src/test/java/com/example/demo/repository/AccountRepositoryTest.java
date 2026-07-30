package com.example.demo.repository;

import com.example.demo.entity.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void findByAccountNumber_shouldReturnSavedAccount() {
        accountRepository.save(account("account-1", "ACC-REPO-01"));

        var found = accountRepository.findByAccountNumber("ACC-REPO-01");

        assertTrue(found.isPresent());
        assertEquals("Test Account", found.get().getAccountName());
    }

    @Test
    void accountNumber_shouldBeUnique() {
        accountRepository.save(account("account-1", "ACC-UNIQUE-01"));
        accountRepository.save(account("account-2", "ACC-UNIQUE-01"));

        assertThrows(DataIntegrityViolationException.class, accountRepository::flush);
    }

    private Account account(String id, String number) {
        Instant now = Instant.parse("2026-07-30T00:00:00Z");
        return new Account(id, number, "Test Account", now, now);
    }
}
