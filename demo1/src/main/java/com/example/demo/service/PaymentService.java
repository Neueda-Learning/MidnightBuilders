package com.example.demo.service;

import com.example.demo.dto.request.CreatePaymentRequest;
import com.example.demo.dto.response.PaymentHistoryResponse;
import com.example.demo.dto.response.PaymentListItemResponse;
import com.example.demo.dto.response.PaymentResponse;
import com.example.demo.dto.response.ProcessPaymentResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Temporary orchestration service skeleton.
 *
 * <p>This placeholder keeps the project compiling while full Role A business flow
 * implementation is still in progress.</p>
 */
@Service
public class PaymentService {

    public CreatePaymentResult createPayment(CreatePaymentRequest request, String idempotencyKey) {
        throw new UnsupportedOperationException("PaymentService.createPayment is not implemented yet");
    }

    public PaymentResponse getPayment(String paymentId) {
        throw new UnsupportedOperationException("PaymentService.getPayment is not implemented yet");
    }

    public List<PaymentListItemResponse> listPayments(String status) {
        throw new UnsupportedOperationException("PaymentService.listPayments is not implemented yet");
    }

    public ProcessPaymentResponse processPayment(String paymentId) {
        throw new UnsupportedOperationException("PaymentService.processPayment is not implemented yet");
    }

    public List<PaymentHistoryResponse> getPaymentHistory(String paymentId) {
        throw new UnsupportedOperationException("PaymentService.getPaymentHistory is not implemented yet");
    }

    /**
     * Controller-facing create result wrapper.
     */
    public static class CreatePaymentResult {
        private final PaymentResponse paymentResponse;
        private final boolean created;

        public CreatePaymentResult(PaymentResponse paymentResponse, boolean created) {
            this.paymentResponse = paymentResponse;
            this.created = created;
        }

        public PaymentResponse getPaymentResponse() {
            return paymentResponse;
        }

        public boolean isCreated() {
            return created;
        }
    }
}

