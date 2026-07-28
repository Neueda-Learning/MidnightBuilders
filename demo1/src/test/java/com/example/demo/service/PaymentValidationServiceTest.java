package com.example.demo.service;

import com.example.demo.config.PaymentProperties;
import com.example.demo.dto.request.CreatePaymentRequest;
import com.example.demo.entity.Payment;
import com.example.demo.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * `PaymentValidationService` 单元测试。
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>创建请求完整校验</li>
 *   <li>处理前付款校验</li>
 *   <li>金额边界与小数位规则</li>
 *   <li>账户格式、空白和相同账户规则</li>
 *   <li>币种大小写、空白、支持集合规则</li>
 *   <li>规范化方法的行为</li>
 * </ul>
 */
@DisplayName("PaymentValidationService 测试")
class PaymentValidationServiceTest {

    private PaymentValidationService service;

    @BeforeEach
    void setUp() {
        PaymentProperties.Validation validation = new PaymentProperties.Validation();
        validation.setMaxAmount(new BigDecimal("1000000.00"));
        validation.setSupportedCurrencies(new LinkedHashSet<>(Set.of("USD", "EUR", "GBP", "CNY")));
        validation.setAccountPattern("^[A-Za-z0-9]{3,50}$");
        service = new PaymentValidationService(validation);
    }

    @Test
    @DisplayName("有效创建请求应通过校验")
    void validateCreateRequest_shouldPassForValidRequest() {
        CreatePaymentRequest request = new CreatePaymentRequest(
                "ACC001",
                "ACC002",
                new BigDecimal("100.00"),
                "USD",
                "Invoice"
        );

        assertDoesNotThrow(() -> service.validateCreateRequest(request));
    }

    @Test
    @DisplayName("有效付款实体应通过处理前校验")
    void validatePaymentForProcessing_shouldPassForValidPayment() {
        Payment payment = new Payment(
                "pay-1",
                "ACC001",
                "ACC002",
                new BigDecimal("100.00"),
                "USD",
                "ref",
                PaymentStatus.CREATED,
                "idem-1",
                "fingerprint-1",
                java.time.Instant.parse("2026-07-27T10:00:00Z"),
                java.time.Instant.parse("2026-07-27T10:00:00Z")
        );

        assertDoesNotThrow(() -> service.validatePaymentForProcessing(payment));
    }

    @Test
    @DisplayName("金额为 0、负数、超上限或超过两位小数时应失败")
    void validateAmount_shouldRejectInvalidAmounts() {
        assertThrows(IllegalArgumentException.class, () -> service.validateAmount(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> service.validateAmount(new BigDecimal("-1.00")));
        assertThrows(IllegalArgumentException.class, () -> service.validateAmount(new BigDecimal("1000000.01")));
        assertThrows(IllegalArgumentException.class, () -> service.validateAmount(new BigDecimal("100.001")));
    }

    @Test
    @DisplayName("金额边界值 1000000.00 应通过校验")
    void validateAmount_shouldAllowUpperBoundary() {
        assertDoesNotThrow(() -> service.validateAmount(new BigDecimal("1000000.00")));
    }

    @Test
    @DisplayName("账户首尾空格应被忽略，且相同账户应报错")
    void validateAccounts_shouldTrimAndRejectSameAccount() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAccounts(" ACC001 ", "ACC001"));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAccounts("   ", "ACC002"));
    }

    @Test
    @DisplayName("账户格式非法时应报错")
    void validateAccounts_shouldRejectInvalidFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validateAccounts("ACC-001", "ACC002"));
    }

    @Test
    @DisplayName("币种 usd 与 USD 等价，且支持集合外币种应报错")
    void validateCurrency_shouldNormalizeAndCheckSupport() {
        assertDoesNotThrow(() -> service.validateCurrency("usd"));
        assertDoesNotThrow(() -> service.validateCurrency(" USD "));
        assertThrows(IllegalArgumentException.class, () -> service.validateCurrency("AUD"));
    }

    @Test
    @DisplayName("币种格式不合法时应报错")
    void validateCurrency_shouldRejectInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> service.validateCurrency("US"));
        assertThrows(IllegalArgumentException.class, () -> service.validateCurrency("USDD"));
    }

    @Test
    @DisplayName("normalizeCurrency 应去空格并转大写")
    void normalizeCurrency_shouldTrimAndUppercase() {
        assertEquals("USD", service.normalizeCurrency(" usd "));
    }

    @Test
    @DisplayName("处理前付款校验应拒绝空实体或空状态")
    void validatePaymentForProcessing_shouldRejectInvalidPayment() {
        assertThrows(IllegalArgumentException.class, () -> service.validatePaymentForProcessing(null));

        Payment payment = new Payment(
                "pay-1",
                "ACC001",
                "ACC002",
                new BigDecimal("100.00"),
                "USD",
                "ref",
                PaymentStatus.CREATED,
                "idem-1",
                "fingerprint-1",
                java.time.Instant.parse("2026-07-27T10:00:00Z"),
                java.time.Instant.parse("2026-07-27T10:00:00Z")
        );
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
        payment.setStatus(null);
        assertThrows(IllegalArgumentException.class, () -> service.validatePaymentForProcessing(payment));
    }
}

