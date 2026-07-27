package com.example.demo.service;

import com.example.demo.dto.request.CreatePaymentRequest;
import com.example.demo.dto.response.PaymentHistoryResponse;
import com.example.demo.dto.response.PaymentListItemResponse;
import com.example.demo.dto.response.PaymentResponse;
import com.example.demo.dto.response.ProcessPaymentResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * PaymentService 占位实现（骨架，待成员 A 完善业务逻辑）
 *
 * <p>该实现仅用于保证 Spring 容器可以启动，所有方法均抛出
 * {@link UnsupportedOperationException}，提示尚未实现。</p>
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

    @Override
    public List<PaymentListItemResponse> listPayments(String statusText) {
        throw new UnsupportedOperationException("listPayments 尚未实现");
    }

    @Override
    public ProcessPaymentResponse processPayment(String paymentId) {
        throw new UnsupportedOperationException("processPayment 尚未实现");
    }

    @Override
    public List<PaymentHistoryResponse> getPaymentHistory(String paymentId) {
        throw new UnsupportedOperationException("getPaymentHistory 尚未实现");
    }
}

