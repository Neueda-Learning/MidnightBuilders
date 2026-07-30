package com.example.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for payment status history response.
 *
 * <p><b>Usage:</b> Returned by GET /api/payments/{id}/history (audit trail query).
 * Represents a single state transition event in the payment's lifecycle.</p>
 *
 * <p><b>Fields:</b> fromStatus (null for initial creation), toStatus, triggeredBy (USER or SYSTEM),
 * errorCode (for failure transitions), notes, changedAt.</p>
 *
 * <p><b>Design Note:</b> Does not include database primary key or internal relationships;
 * only business-relevant information for audit display.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentHistoryResponse {

    private String fromStatus;
    private String toStatus;
    private String triggeredBy;
    private String errorCode;
    private String notes;
    private String changedAt;

    // ==================== Constructors ====================

    public PaymentHistoryResponse() {
    }

    public PaymentHistoryResponse(String fromStatus, String toStatus, String triggeredBy,
                                  String errorCode, String notes, String changedAt) {
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.triggeredBy = triggeredBy;
        this.errorCode = errorCode;
        this.notes = notes;
        this.changedAt = changedAt;
    }

    // ==================== Getters & Setters ====================

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(String changedAt) {
        this.changedAt = changedAt;
    }

    @Override
    public String toString() {
        return "PaymentHistoryResponse{" +
                "fromStatus='" + fromStatus + '\'' +
                ", toStatus='" + toStatus + '\'' +
                ", triggeredBy='" + triggeredBy + '\'' +
                ", changedAt='" + changedAt + '\'' +
                '}';
    }
}

