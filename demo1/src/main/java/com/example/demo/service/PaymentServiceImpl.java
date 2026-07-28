package com.example.demo.service;

/**
 * Deprecated legacy placeholder kept only to avoid breaking references.
 *
 * <p>The active business implementation is {@link PaymentService}.</p>
 */
@Service
public class PaymentServiceImpl extends PaymentService {

    @Override
    public PaymentService.CreatePaymentResult createPayment(CreatePaymentRequest request, String idempotencyKey) {
        throw new UnsupportedOperationException("createPayment 尚未实现");
    }

    @Override
    public PaymentResponse getPayment(String paymentId) {
        throw new UnsupportedOperationException("getPayment 尚未实现");
    }

    private PaymentServiceImpl() {
        // Prevent instantiation.
    }
}
