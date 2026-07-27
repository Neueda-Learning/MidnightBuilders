package com.example.demo.dto.request;

import jakarta.validation.constraints.*;

/**
 * DTO for payment creation request.
 *
 * <p><b>Usage:</b> Request body for POST /api/payments.
 * Only contains fields that clients are allowed to provide; does not include
 * id, status, errorCode, createdAt, updatedAt, or other system-managed fields.</p>
 *
 * <p><b>Field-Level Validation:</b> Uses Jakarta Bean Validation annotations for basic type checking.
 * Business rules (e.g., "currency must be supported", "accounts must differ") are validated in Service layer.</p>
 *
 * <p><b>Important:</b> Min/max values here are constraints; PaymentValidationService performs
 * additional business validation (e.g., comparing against configured payment limits).</p>
 */
public class CreatePaymentRequest {

    @NotBlank(message = "sourceAccount is required")
    @Size(max = 50, message = "sourceAccount must not exceed 50 characters")
    private String sourceAccount;

    @NotBlank(message = "destinationAccount is required")
    @Size(max = 50, message = "destinationAccount must not exceed 50 characters")
    private String destinationAccount;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "amount must be within valid decimal range")
    private java.math.BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be exactly 3 characters")
    private String currency;

    @Size(max = 255, message = "reference must not exceed 255 characters")
    private String reference;

    // ==================== Constructors ====================

    public CreatePaymentRequest() {
    }

    public CreatePaymentRequest(String sourceAccount, String destinationAccount,
                                java.math.BigDecimal amount, String currency, String reference) {
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
    }

    // ==================== Getters & Setters ====================

    public String getSourceAccount() {
        return sourceAccount;
    }

    public void setSourceAccount(String sourceAccount) {
        this.sourceAccount = sourceAccount;
    }

    public String getDestinationAccount() {
        return destinationAccount;
    }

    public void setDestinationAccount(String destinationAccount) {
        this.destinationAccount = destinationAccount;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(java.math.BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    @Override
    public String toString() {
        return "CreatePaymentRequest{" +
                "sourceAccount='" + sourceAccount + '\'' +
                ", destinationAccount='" + destinationAccount + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                '}';
    }
}

