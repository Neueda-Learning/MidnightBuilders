package com.example.demo.dto.response;

/**
 * DTO for payment processing result response.
 *
 * <p><b>Usage:</b> Returned by POST /api/payments/{id}/process.</p>
 *
 * <p><b>Fields:</b> id, previousStatus, currentStatus, message,
 * errorCode (nullable), errorMessage (nullable).</p>
 *
 * <p><b>JSON Serialization:</b> nullable error fields are part of the response
 * contract and may be null when processing succeeds.</p>
 */
public class ProcessPaymentResponse {

    private String id;
    private String previousStatus;
    private String currentStatus;
    private String message;
    private String errorCode;
    private String errorMessage;

    public ProcessPaymentResponse() {
    }

    public ProcessPaymentResponse(String id, String previousStatus, String currentStatus,
                                  String message, String errorCode, String errorMessage) {
        this.id = id;
        this.previousStatus = previousStatus;
        this.currentStatus = currentStatus;
        this.message = message;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(String previousStatus) {
        this.previousStatus = previousStatus;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
}

