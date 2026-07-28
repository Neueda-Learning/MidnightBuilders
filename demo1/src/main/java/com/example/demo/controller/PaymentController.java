package com.example.demo.controller;

import com.example.demo.dto.request.CreatePaymentRequest;
import com.example.demo.dto.response.PaymentHistoryResponse;
import com.example.demo.dto.response.PaymentListItemResponse;
import com.example.demo.dto.response.PaymentResponse;
import com.example.demo.dto.response.ProcessPaymentResponse;
import com.example.demo.service.PaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
@Validated
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        log.info("Received create payment request: idempotencyKey={}, amount={}, currency={}, sourceAccount={}, destinationAccount={}, referenceLength={}",
                maskToken(idempotencyKey),
                request.getAmount(),
                request.getCurrency(),
                maskAccount(request.getSourceAccount()),
                maskAccount(request.getDestinationAccount()),
                request.getReference() == null ? 0 : request.getReference().trim().length());

        PaymentService.CreatePaymentResult result = paymentService.createPayment(request, idempotencyKey);

        if (result.isCreated()) {
            log.info("Create payment request created new payment: paymentId={}, idempotencyKey={}",
                    result.getPaymentResponse().getId(),
                    maskToken(idempotencyKey));
            URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(result.getPaymentResponse().getId())
                    .toUri();
            return ResponseEntity.created(location).body(result.getPaymentResponse());
        }

        log.info("Create payment request returned existing payment due to idempotent replay: paymentId={}, idempotencyKey={}",
                result.getPaymentResponse().getId(),
                maskToken(idempotencyKey));
        return ResponseEntity.ok(result.getPaymentResponse());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable("id") String paymentId) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId));
    }

    @GetMapping
    public ResponseEntity<List<PaymentListItemResponse>> listPayments(
            @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(paymentService.listPayments(status));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<ProcessPaymentResponse> processPayment(@PathVariable("id") String paymentId) {
        return ResponseEntity.ok(paymentService.processPayment(paymentId));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<PaymentHistoryResponse>> getPaymentHistory(@PathVariable("id") String paymentId) {
        return ResponseEntity.ok(paymentService.getPaymentHistory(paymentId));
    }

    private String maskToken(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "<empty>";
        }
        String normalized = value.trim();
        if (normalized.length() <= 10) {
            return normalized.substring(0, Math.min(2, normalized.length())) + "***"
                    + normalized.substring(Math.max(0, normalized.length() - 2));
        }
        return normalized.substring(0, 6) + "***" + normalized.substring(normalized.length() - 4);
    }

    private String maskAccount(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "<empty>";
        }
        String normalized = value.trim();
        return "***" + normalized.substring(Math.max(0, normalized.length() - 4));
    }
}

