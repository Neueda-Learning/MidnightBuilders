package com.example.demo.service;

import com.example.demo.dto.request.CreatePaymentRequest;
import com.example.demo.dto.response.PaymentHistoryResponse;
import com.example.demo.dto.response.PaymentListItemResponse;
import com.example.demo.dto.response.PaymentResponse;
import com.example.demo.dto.response.ProcessPaymentResponse;

import java.util.List;

/**
 * 付款业务服务接口骨架（待成员 A 完善）
 */
public interface PaymentService {

    CreatePaymentResult createPayment(CreatePaymentRequest request, String idempotencyKey);

    PaymentResponse getPayment(String paymentId);

    List<PaymentListItemResponse> listPayments(String statusText);

    ProcessPaymentResponse processPayment(String paymentId);

    List<PaymentHistoryResponse> getPaymentHistory(String paymentId);

    /** createPayment 的内部结果对象，供 Controller 区分 201 与 200 */
    class CreatePaymentResult {
        private final PaymentResponse paymentResponse;
        private final boolean created;

        public CreatePaymentResult(PaymentResponse paymentResponse, boolean created) {
            this.paymentResponse = paymentResponse;
            this.created = created;
        }

        public PaymentResponse getPaymentResponse() { return paymentResponse; }
        public boolean isCreated() { return created; }
    }
}

