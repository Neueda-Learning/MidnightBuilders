package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;
import com.example.demo.enums.AccountStatus;
import com.example.demo.enums.AccountType;
import java.time.Instant;

/**
 * Minimal payer account master data used for payment source account existence checks.
 *
 * <p>This iteration only needs enough account information to determine whether a
 * sourceAccount belongs to the local bank/account domain. It is intentionally not a
 * full user/login/balance model.</p>
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "account_number", length = 50, nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "account_name", length = 100, nullable = false)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 20, nullable = false)
    private AccountType accountType = AccountType.BUSINESS;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency = "CNY";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Account() {
    }

    public Account(String id, String accountNumber, String accountName, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
