package com.example.demo.service;

import com.example.demo.dto.request.CreatePaymentRequest;
import com.example.demo.dto.response.PaymentHistoryResponse;
import com.example.demo.entity.Account;
import com.example.demo.entity.PaymentStatusHistory;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.enums.TriggeredBy;
import com.example.demo.repository.AccountRepository;
import com.example.demo.repository.PaymentStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3 联调集成测试：验证完整支付生命周期中历史记录的正确性。
 *
 * <p><b>测试目标（角色D视角）：</b>
 * <ul>
 *   <li>每次状态变更都写了历史记录（PaymentHistoryService 与 PaymentLifecycleService 协作正确）</li>
 *   <li>历史记录条数、顺序、字段均符合设计文档</li>
 *   <li>幂等重放不产生重复历史记录</li>
 * </ul>
 *
 * <p><b>环境：</b> H2 内存库 + create-drop + Flyway 关闭（见 test/resources/application.properties）。
 * 每个测试方法使用 @Transactional 事务自动回滚，保证测试隔离。
 */
@SpringBootTest
@Transactional
class PaymentLifecycleIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentStatusHistoryRepository historyRepository;

    @Autowired
    private AccountRepository accountRepository;

    // ==================== 测试辅助方法 ====================

    /**
     * 构造一个标准的有效支付请求，每次调用都使用不同的幂等键。
     */
    private CreatePaymentRequest validRequest() {
        return new CreatePaymentRequest(
                "ACC-SOURCE-01",
                "ACC-DEST-02",
                new BigDecimal("250.00"),
                "USD",
                "integration test payment"
        );
    }

    private CreatePaymentRequest missingPayerAccountRequest() {
        return new CreatePaymentRequest(
                "ACC-NOT-FOUND-01",
                "ACC-DEST-02",
                new BigDecimal("250.00"),
                "USD",
                "missing payer account test"
        );
    }

    private void ensurePayerAccountExists(String accountNumber) {
        accountRepository.findByAccountNumber(accountNumber).orElseGet(() ->
                accountRepository.save(new Account(
                        UUID.randomUUID().toString(),
                        accountNumber,
                        "Test Payer Account",
                        Instant.now(),
                        Instant.now()
                ))
        );
    }

    /**
     * 创建一笔支付并返回其 ID（封装重复的创建步骤）。
     */
    private String createAndGetId() {
        ensurePayerAccountExists("ACC-SOURCE-01");
        String key = UUID.randomUUID().toString();
        PaymentService.CreatePaymentResult result = paymentService.createPayment(validRequest(), key);
        return result.getPaymentResponse().getId();
    }

    // ==================== 场景 1：仅创建，验证历史 ====================

    /**
     * 场景 1：仅调用 createPayment，不处理。
     * 期望：历史表中有且仅有 1 条记录，fromStatus=null，toStatus=CREATED，triggeredBy=USER。
     */
    @Test
    void createPayment_shouldRecordExactlyOneCreatedHistoryEntry() {
        // given
        String key = UUID.randomUUID().toString();

        // when
        PaymentService.CreatePaymentResult result = paymentService.createPayment(validRequest(), key);
        String paymentId = result.getPaymentResponse().getId();

        // then
        List<PaymentStatusHistory> history =
                historyRepository.findAllByPaymentIdOrderByChangedAtAsc(paymentId);

        assertEquals(1, history.size(), "创建后历史记录应有且仅有 1 条");

        PaymentStatusHistory created = history.get(0);
        assertNull(created.getFromStatus(), "初始创建 fromStatus 应为 null");
        assertEquals(PaymentStatus.CREATED, created.getToStatus(), "toStatus 应为 CREATED");
        assertEquals(TriggeredBy.USER, created.getTriggeredBy(), "触发者应为 USER");
        assertNotNull(created.getChangedAt(), "changedAt 不能为 null");
    }

    /**
     * 场景 1b：创建后返回的标志位正确（首次创建 isCreated=true）。
     */
    @Test
    void createPayment_firstTime_shouldReturnIsCreatedTrue() {
        String key = UUID.randomUUID().toString();

        PaymentService.CreatePaymentResult result = paymentService.createPayment(validRequest(), key);

        assertTrue(result.isCreated(), "首次创建 isCreated 应为 true");
        assertNotNull(result.getPaymentResponse().getId(), "返回的支付 ID 不能为 null");
        assertEquals("CREATED", result.getPaymentResponse().getStatus(), "初始状态应为 CREATED");
    }

    // ==================== 场景 2：创建后处理成功，验证完整历史链 ====================

    /**
     * 场景 2：createPayment 后调用 processPayment，期望产生 4 条历史记录。
     * 顺序：CREATED → VALIDATED → SENT → COMPLETED（升序）。
     */
    @Test
    void processPayment_success_shouldRecordFourHistoryEntriesInAscendingOrder() {
        // given
        String paymentId = createAndGetId();

        // when
        paymentService.processPayment(paymentId);

        // then
        List<PaymentStatusHistory> history =
                historyRepository.findAllByPaymentIdOrderByChangedAtAsc(paymentId);

        assertEquals(4, history.size(), "完整处理后应有 4 条历史记录");

        // 验证每条记录的 toStatus 顺序
        assertEquals(PaymentStatus.CREATED,   history.get(0).getToStatus(), "第1条应为 CREATED");
        assertEquals(PaymentStatus.VALIDATED, history.get(1).getToStatus(), "第2条应为 VALIDATED");
        assertEquals(PaymentStatus.SENT,      history.get(2).getToStatus(), "第3条应为 SENT");
        assertEquals(PaymentStatus.COMPLETED, history.get(3).getToStatus(), "第4条应为 COMPLETED");
    }

    /**
     * 场景 2b：验证每条历史记录的 fromStatus 与上一条 toStatus 衔接正确（形成审计链）。
     */
    @Test
    void processPayment_success_historyFromStatusShouldFormContinuousChain() {
        // given
        String paymentId = createAndGetId();

        // when
        paymentService.processPayment(paymentId);

        // then
        List<PaymentStatusHistory> history =
                historyRepository.findAllByPaymentIdOrderByChangedAtAsc(paymentId);

        assertEquals(4, history.size());

        // CREATED: fromStatus 为 null（初始创建无前驱状态）
        assertNull(history.get(0).getFromStatus(),                        "CREATED 的 fromStatus 应为 null");
        // VALIDATED: fromStatus = CREATED
        assertEquals(PaymentStatus.CREATED,   history.get(1).getFromStatus(), "VALIDATED 的前驱应为 CREATED");
        // SENT: fromStatus = VALIDATED
        assertEquals(PaymentStatus.VALIDATED, history.get(2).getFromStatus(), "SENT 的前驱应为 VALIDATED");
        // COMPLETED: fromStatus = SENT
        assertEquals(PaymentStatus.SENT,      history.get(3).getFromStatus(), "COMPLETED 的前驱应为 SENT");
    }

    /**
     * 场景 2c：处理成功后，最终支付状态应为 COMPLETED，error 字段为 null。
     */
    @Test
    void processPayment_success_finalStatusShouldBeCompleted() {
        // given
        String paymentId = createAndGetId();

        // when
        var result = paymentService.processPayment(paymentId);

        // then
        assertEquals("COMPLETED", result.getCurrentStatus(), "处理成功后 currentStatus 应为 COMPLETED");
        assertEquals("CREATED",   result.getPreviousStatus(), "previousStatus 应为 CREATED");
        assertNull(result.getErrorCode(),    "成功处理不应有 errorCode");
        assertNull(result.getErrorMessage(), "成功处理不应有 errorMessage");
    }

    /**
     * 场景 2d：验证系统触发的状态转换 triggeredBy=SYSTEM（VALIDATED/SENT/COMPLETED）。
     */
    @Test
    void processPayment_systemTriggeredTransitions_shouldHaveTriggeredBySystem() {
        // given
        String paymentId = createAndGetId();

        // when
        paymentService.processPayment(paymentId);

        // then
        List<PaymentStatusHistory> history =
                historyRepository.findAllByPaymentIdOrderByChangedAtAsc(paymentId);

        // USER 触发创建
        assertEquals(TriggeredBy.USER,   history.get(0).getTriggeredBy(), "CREATED 应由 USER 触发");
        // 后续三步都是 SYSTEM
        assertEquals(TriggeredBy.SYSTEM, history.get(1).getTriggeredBy(), "VALIDATED 应由 SYSTEM 触发");
        assertEquals(TriggeredBy.SYSTEM, history.get(2).getTriggeredBy(), "SENT 应由 SYSTEM 触发");
        assertEquals(TriggeredBy.SYSTEM, history.get(3).getTriggeredBy(), "COMPLETED 应由 SYSTEM 触发");
    }

    @Test
    void processPayment_missingPayerAccount_shouldFailFromValidatedToFailed() {
        String key = UUID.randomUUID().toString();
        PaymentService.CreatePaymentResult created = paymentService.createPayment(missingPayerAccountRequest(), key);
        String paymentId = created.getPaymentResponse().getId();

        var result = paymentService.processPayment(paymentId);

        assertEquals("CREATED", result.getPreviousStatus(), "处理入口 previousStatus 应为 CREATED");
        assertEquals("FAILED", result.getCurrentStatus(), "付款账户不存在时应失败");
        assertEquals("ACCOUNT_NOT_FOUND", result.getErrorCode(), "错误码应为 ACCOUNT_NOT_FOUND");
        assertEquals("Source account does not exist", result.getErrorMessage(), "错误消息应说明付款账户不存在");

        List<PaymentStatusHistory> history = historyRepository.findAllByPaymentIdOrderByChangedAtAsc(paymentId);
        assertEquals(3, history.size(), "应形成 CREATED -> VALIDATED -> FAILED 三条历史记录");
        assertEquals(PaymentStatus.CREATED, history.get(0).getToStatus());
        assertEquals(PaymentStatus.VALIDATED, history.get(1).getToStatus());
        assertEquals(PaymentStatus.FAILED, history.get(2).getToStatus());
        assertEquals(PaymentStatus.VALIDATED, history.get(2).getFromStatus(), "失败前驱状态应为 VALIDATED");
        assertEquals("ACCOUNT_NOT_FOUND", history.get(2).getErrorCode(), "失败历史应记录 ACCOUNT_NOT_FOUND");
    }

    @Test
    void processPayment_invalidBusinessField_shouldFailFromCreatedToFailed() {
        CreatePaymentRequest invalid = new CreatePaymentRequest(
                "ACC-SOURCE-01",
                "ACC-DEST-02",
                BigDecimal.ZERO,
                "USD",
                "invalid amount"
        );
        PaymentService.CreatePaymentResult created = paymentService.createPayment(
                invalid,
                UUID.randomUUID().toString()
        );

        var result = paymentService.processPayment(created.getPaymentResponse().getId());

        assertEquals("FAILED", result.getCurrentStatus());
        assertEquals("VALIDATION", result.getFailureStage());
        assertEquals("INVALID_AMOUNT", result.getErrorCode());

        List<PaymentStatusHistory> history = historyRepository.findAllByPaymentIdOrderByChangedAtAsc(
                created.getPaymentResponse().getId()
        );
        assertEquals(2, history.size());
        assertEquals(PaymentStatus.CREATED, history.get(1).getFromStatus());
        assertEquals(PaymentStatus.FAILED, history.get(1).getToStatus());
    }

    // ==================== 场景 3：历史时间戳升序 ====================

    /**
     * 场景 3：验证 findAllByPaymentIdOrderByChangedAtAsc 返回的时间戳严格升序。
     */
    @Test
    void getPaymentHistory_shouldReturnEntriesInAscendingChangedAtOrder() {
        // given
        String paymentId = createAndGetId();
        paymentService.processPayment(paymentId);

        // when
        List<PaymentStatusHistory> history =
                historyRepository.findAllByPaymentIdOrderByChangedAtAsc(paymentId);

        // then
        for (int i = 0; i < history.size() - 1; i++) {
            assertFalse(
                    history.get(i).getChangedAt().isAfter(history.get(i + 1).getChangedAt()),
                    "第 " + i + " 条的时间戳不应晚于第 " + (i + 1) + " 条"
            );
        }
    }

    /**
     * 场景 3b：通过 PaymentService.getPaymentHistory 验证 DTO 映射和升序顺序。
     */
    @Test
    void getPaymentHistory_viaService_shouldReturnMappedResponsesInOrder() {
        // given
        String paymentId = createAndGetId();
        paymentService.processPayment(paymentId);

        // when
        List<PaymentHistoryResponse> responses = paymentService.getPaymentHistory(paymentId);

        // then
        assertEquals(4, responses.size(), "应返回 4 条历史响应");
        assertNull(responses.get(0).getFromStatus(),   "第1条 fromStatus 应为 null");
        assertEquals("CREATED",   responses.get(0).getToStatus());
        assertEquals("VALIDATED", responses.get(1).getToStatus());
        assertEquals("SENT",      responses.get(2).getToStatus());
        assertEquals("COMPLETED", responses.get(3).getToStatus());
        // 时间格式：ISO 8601 / UTC（以 Z 结尾）
        assertTrue(responses.get(0).getChangedAt().endsWith("Z"),
                "changedAt 格式应为 UTC ISO 8601，以 Z 结尾");
    }

    // ==================== 场景 4：幂等重放不产生重复历史 ====================

    /**
     * 场景 4：相同幂等键+相同请求体重放，应返回原支付，不产生新的历史记录。
     */
    @Test
    void createPayment_idempotentReplay_shouldNotDuplicateHistoryEntries() {
        // given
        CreatePaymentRequest request = validRequest();
        String key = UUID.randomUUID().toString();

        // when: 两次用相同 key + 相同 body
        PaymentService.CreatePaymentResult first  = paymentService.createPayment(request, key);
        PaymentService.CreatePaymentResult second = paymentService.createPayment(request, key);

        // then: 返回的是同一笔支付
        assertEquals(first.getPaymentResponse().getId(), second.getPaymentResponse().getId(),
                "重放应返回同一个支付 ID");
        assertTrue(first.isCreated(),   "首次创建 isCreated 应为 true");
        assertFalse(second.isCreated(), "重放 isCreated 应为 false");

        // then: 历史记录仍然只有 1 条
        List<PaymentStatusHistory> history = historyRepository.findAllByPaymentIdOrderByChangedAtAsc(
                first.getPaymentResponse().getId());
        assertEquals(1, history.size(), "幂等重放不应产生重复历史记录");
    }

    // ==================== 场景 5：列表查询 ====================

    /**
     * 场景 5：查询全部支付列表，至少包含刚创建的支付，默认降序。
     */
    @Test
    void listPayments_noFilter_shouldIncludeCreatedPayment() {
        // given
        String paymentId = createAndGetId();

        // when
        var list = paymentService.listPayments(null);

        // then
        assertFalse(list.isEmpty(), "列表不应为空");
        assertTrue(list.stream().anyMatch(p -> p.getId().equals(paymentId)),
                "列表应包含刚创建的支付");
    }

    /**
     * 场景 5b：按状态筛选，只返回 CREATED 状态的支付。
     */
    @Test
    void listPayments_filterByCreated_shouldOnlyReturnCreatedPayments() {
        // given
        createAndGetId(); // 至少创建一笔 CREATED 状态的支付

        // when
        var list = paymentService.listPayments("CREATED");

        // then
        assertFalse(list.isEmpty(), "CREATED 状态的支付列表不应为空");
        assertTrue(list.stream().allMatch(p -> "CREATED".equals(p.getStatus())),
                "筛选结果中所有支付状态都应为 CREATED");
    }
}
