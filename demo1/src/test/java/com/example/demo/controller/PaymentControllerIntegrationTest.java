package com.example.demo.controller;

import com.example.demo.dto.request.CreatePaymentRequest;
import com.example.demo.dto.response.PaymentHistoryResponse;
import com.example.demo.dto.response.PaymentListItemResponse;
import com.example.demo.dto.response.PaymentResponse;
import com.example.demo.dto.response.ProcessPaymentResponse;
import com.example.demo.entity.Account;
import com.example.demo.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3 联调测试：直接调用 Controller Bean，验证 API 出参契约。
 *
 * <p>当前 Spring Boot 版本测试依赖未包含 MockMvc 自动装配类，
 * 因此本测试使用 controller 直接调用方式验证端到端业务链路。
 */
@SpringBootTest
@Transactional
class PaymentControllerIntegrationTest {

    @Autowired
    private PaymentController paymentController;

    @Autowired
    private AccountRepository accountRepository;

    private CreatePaymentRequest validRequest() {
        return new CreatePaymentRequest(
                "ACC-SOURCE-01",
                "ACC-DEST-02",
                new BigDecimal("100.00"),
                "USD",
                "controller integration test"
        );
    }

    private CreatePaymentRequest missingPayerAccountRequest() {
        return new CreatePaymentRequest(
                "ACC-NOT-FOUND-01",
                "ACC-DEST-02",
                new BigDecimal("100.00"),
                "USD",
                "controller missing payer account test"
        );
    }

    private void ensurePayerAccountExists(String accountNumber) {
        accountRepository.findByAccountNumber(accountNumber).orElseGet(() ->
                accountRepository.save(new Account(
                        UUID.randomUUID().toString(),
                        accountNumber,
                        "Controller Test Payer Account",
                        Instant.now(),
                        Instant.now()
                ))
        );
    }

    @Test
    void createPayment_firstTime_shouldReturn201AndLocationHeader() {
        ensurePayerAccountExists("ACC-SOURCE-01");
        String key = UUID.randomUUID().toString();

        ResponseEntity<PaymentResponse> response = paymentController.createPayment(validRequest(), key);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getHeaders().getLocation());
        assertNotNull(response.getBody());
        assertEquals("CREATED", response.getBody().getStatus());
    }

    @Test
    void createPayment_replay_shouldReturn200WithSamePaymentId() {
        ensurePayerAccountExists("ACC-SOURCE-01");
        String key = UUID.randomUUID().toString();

        ResponseEntity<PaymentResponse> first = paymentController.createPayment(validRequest(), key);
        ResponseEntity<PaymentResponse> replay = paymentController.createPayment(validRequest(), key);

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertNotNull(first.getBody());
        assertNotNull(replay.getBody());
        assertEquals(first.getBody().getId(), replay.getBody().getId());
    }

    @Test
    void getPayment_existingId_shouldReturn200() {
        ensurePayerAccountExists("ACC-SOURCE-01");
        String key = UUID.randomUUID().toString();
        ResponseEntity<PaymentResponse> created = paymentController.createPayment(validRequest(), key);

        assertNotNull(created.getBody());
        String paymentId = created.getBody().getId();

        ResponseEntity<PaymentResponse> detail = paymentController.getPayment(paymentId);

        assertEquals(HttpStatus.OK, detail.getStatusCode());
        assertNotNull(detail.getBody());
        assertEquals(paymentId, detail.getBody().getId());
        assertEquals("USD", detail.getBody().getCurrency());
    }

    @Test
    void listPayments_noFilter_shouldReturnArray() {
        ensurePayerAccountExists("ACC-SOURCE-01");
        paymentController.createPayment(validRequest(), UUID.randomUUID().toString());

        ResponseEntity<List<PaymentListItemResponse>> response = paymentController.listPayments(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
    }

    @Test
    void listPayments_filterByCreated_shouldReturnOnlyCreated() {
        ensurePayerAccountExists("ACC-SOURCE-01");
        paymentController.createPayment(validRequest(), UUID.randomUUID().toString());

        ResponseEntity<List<PaymentListItemResponse>> response = paymentController.listPayments("CREATED");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());
        assertTrue(response.getBody().stream().allMatch(i -> "CREATED".equals(i.getStatus())));
    }

    @Test
    void processPayment_success_shouldReturnCompleted() {
        ensurePayerAccountExists("ACC-SOURCE-01");
        ResponseEntity<PaymentResponse> created = paymentController.createPayment(validRequest(), UUID.randomUUID().toString());
        assertNotNull(created.getBody());

        String paymentId = created.getBody().getId();
        ResponseEntity<ProcessPaymentResponse> processed = paymentController.processPayment(paymentId);

        assertEquals(HttpStatus.OK, processed.getStatusCode());
        assertNotNull(processed.getBody());
        assertEquals("CREATED", processed.getBody().getPreviousStatus());
        assertEquals("COMPLETED", processed.getBody().getCurrentStatus());
    }

    @Test
    void getHistory_afterProcess_shouldReturnFourEntriesInOrder() {
        ensurePayerAccountExists("ACC-SOURCE-01");
        ResponseEntity<PaymentResponse> created = paymentController.createPayment(validRequest(), UUID.randomUUID().toString());
        assertNotNull(created.getBody());

        String paymentId = created.getBody().getId();
        paymentController.processPayment(paymentId);

        ResponseEntity<List<PaymentHistoryResponse>> historyResp = paymentController.getPaymentHistory(paymentId);

        assertEquals(HttpStatus.OK, historyResp.getStatusCode());
        assertNotNull(historyResp.getBody());
        assertEquals(4, historyResp.getBody().size());
        assertEquals("CREATED", historyResp.getBody().get(0).getToStatus());
        assertEquals("VALIDATED", historyResp.getBody().get(1).getToStatus());
        assertEquals("SENT", historyResp.getBody().get(2).getToStatus());
        assertEquals("COMPLETED", historyResp.getBody().get(3).getToStatus());
    }

    @Test
    void endToEnd_create_process_detail_history_shouldBeConsistent() {
        ensurePayerAccountExists("ACC-SOURCE-01");
        String key = UUID.randomUUID().toString();

        ResponseEntity<PaymentResponse> created = paymentController.createPayment(validRequest(), key);
        assertNotNull(created.getBody());

        String paymentId = created.getBody().getId();
        ResponseEntity<ProcessPaymentResponse> processed = paymentController.processPayment(paymentId);
        ResponseEntity<PaymentResponse> detail = paymentController.getPayment(paymentId);
        ResponseEntity<List<PaymentHistoryResponse>> history = paymentController.getPaymentHistory(paymentId);

        assertNotNull(processed.getBody());
        assertNotNull(detail.getBody());
        assertNotNull(history.getBody());

        assertEquals("COMPLETED", processed.getBody().getCurrentStatus());
        assertEquals("COMPLETED", detail.getBody().getStatus());
        assertEquals(4, history.getBody().size());
    }

    @Test
    void processPayment_missingPayerAccount_shouldReturnFailedBusinessResult() {
        ResponseEntity<PaymentResponse> created = paymentController.createPayment(
                missingPayerAccountRequest(),
                UUID.randomUUID().toString()
        );
        assertNotNull(created.getBody());

        String paymentId = created.getBody().getId();
        ResponseEntity<ProcessPaymentResponse> processed = paymentController.processPayment(paymentId);
        ResponseEntity<List<PaymentHistoryResponse>> history = paymentController.getPaymentHistory(paymentId);

        assertEquals(HttpStatus.OK, processed.getStatusCode());
        assertNotNull(processed.getBody());
        assertEquals("CREATED", processed.getBody().getPreviousStatus());
        assertEquals("FAILED", processed.getBody().getCurrentStatus());
        assertEquals("ACCOUNT_NOT_FOUND", processed.getBody().getErrorCode());
        assertEquals("Source account does not exist", processed.getBody().getErrorMessage());

        assertNotNull(history.getBody());
        assertEquals(3, history.getBody().size());
        assertEquals("CREATED", history.getBody().get(0).getToStatus());
        assertEquals("VALIDATED", history.getBody().get(1).getToStatus());
        assertEquals("FAILED", history.getBody().get(2).getToStatus());
        assertEquals("ACCOUNT_NOT_FOUND", history.getBody().get(2).getErrorCode());
    }
}
