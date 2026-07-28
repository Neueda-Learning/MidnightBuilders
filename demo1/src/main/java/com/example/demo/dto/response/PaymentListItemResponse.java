package com.example.demo.dto.response;

import java.math.BigDecimal;

/**
 * DTO for payment list item response.
 *
 * <p><b>Usage:</b> Returned by GET /api/payments (list query).
 * Contains only essential fields for display in a list, reducing data transfer and UI complexity.</p>
 *
 * <p><b>Excluded Fields:</b> sourceAccount, destinationAccount, reference, errorMessage.
 * List items show key identifiers and status; detailed error reasons are available via GET /api/payments/{id}.</p>
 *
 * <p><b>Fields:</b> id, amount, currency, status, createdAt, errorCode (nullable for non-FAILED records).</p>
 */
public class PaymentListItemResponse {

    private String id;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String createdAt;
    private String errorCode;

    // ==================== Constructors ====================

    public PaymentListItemResponse() {
    }

    public PaymentListItemResponse(String id, BigDecimal amount, String currency,
                                   String status, String createdAt, String errorCode) {
        this.id = id;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.errorCode = errorCode;
    }

    // ==================== Getters & Setters ====================

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public String toString() {
        return "PaymentListItemResponse{" +
                "id='" + id + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}

