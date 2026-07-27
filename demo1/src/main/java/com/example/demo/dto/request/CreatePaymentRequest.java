package com.example.demo.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO for payment creation request.
 *
 * <p><b>Usage:</b> Request body for POST /api/payments.
 * Only contains fields that clients are allowed to provide; does not include
 * id, status, errorCode, createdAt, updatedAt, or other system-managed fields.</p>
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
    @DecimalMax(value = "1000000.00", message = "amount must not exceed 1000000.00")
    @Digits(integer = 7, fraction = 2, message = "amount must have up to 2 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter uppercase ISO code")
    private String currency;

    @Size(max = 255, message = "reference must not exceed 255 characters")
    private String reference;

    public CreatePaymentRequest() {
    }

    public CreatePaymentRequest(String sourceAccount, String destinationAccount,
                                BigDecimal amount, String currency, String reference) {
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
    }

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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
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
