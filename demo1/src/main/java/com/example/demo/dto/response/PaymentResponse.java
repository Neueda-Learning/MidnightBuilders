package com.example.demo.dto.response;

import java.math.BigDecimal;

/**
 * DTO for payment detail response.
 *
 * <p><b>Usage:</b> Returned by GET /api/payments/{id} and POST /api/payments (on successful creation).
 * Contains complete payment information but excludes internal fields like idempotency key and request fingerprint.</p>
 *
 * <p><b>Fields:</b> All payment business fields plus timestamps and optional error details.</p>
 *
 * <p><b>JSON Serialization:</b> nullable fields (reference, errorCode, errorMessage) are kept in the response
 * contract and may be null depending on payment state.</p>
 */
public class PaymentResponse {

    private String id;
    private String sourceAccount;
    private String destinationAccount;
    private BigDecimal amount;
    private String currency;
    private String reference;
    private String status;
    private String errorCode;
    private String errorMessage;
    private String createdAt;
    private String updatedAt;

    // ==================== Constructors ====================

    public PaymentResponse() {
    }

    public PaymentResponse(String id, String sourceAccount, String destinationAccount,
                          BigDecimal amount, String currency, String reference,
                          String status, String errorCode, String errorMessage,
                          String createdAt, String updatedAt) {
        this.id = id;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ==================== Getters & Setters ====================

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "PaymentResponse{" +
                "id='" + id + '\'' +
                ", sourceAccount='" + sourceAccount + '\'' +
                ", destinationAccount='" + destinationAccount + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}

