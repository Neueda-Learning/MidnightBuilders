package com.example.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO returned after invoking payment processing.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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

