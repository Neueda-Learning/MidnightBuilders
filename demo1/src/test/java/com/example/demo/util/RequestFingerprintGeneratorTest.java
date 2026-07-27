package com.example.demo.util;

import com.example.demo.dto.request.CreatePaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RequestFingerprintGenerator 单元测试
 *
 * 覆盖以下场景：
 * 1. 相同内容的请求产生相同指纹（幂等基础）
 * 2. 金额尾零等价（100.0 == 100.00）
 * 3. 币种大小写等价（usd == USD）
 * 4. 账户/备注首尾空格等价
 * 5. 不同金额产生不同指纹
 * 6. 不同账户产生不同指纹
 * 7. reference 为 null 与空字符串等价
 * 8. 指纹格式固定为 64 位十六进制
 * 9. 入参为 null 时抛出异常
 */
@DisplayName("RequestFingerprintGenerator 测试")
class RequestFingerprintGeneratorTest {

    private RequestFingerprintGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new RequestFingerprintGenerator();
    }

    // ==================== 相同内容 → 相同指纹 ====================

    @Test
    @DisplayName("完全相同的请求应产生相同指纹")
    void sameRequest_shouldProduceSameFingerprint() {
        CreatePaymentRequest r1 = request("ACC001", "ACC002", "1000.00", "CNY", "Invoice");
        CreatePaymentRequest r2 = request("ACC001", "ACC002", "1000.00", "CNY", "Invoice");

        assertEquals(generator.generate(r1), generator.generate(r2));
    }

    // ==================== 金额尾零等价 ====================

    @Test
    @DisplayName("100.0 与 100.00 应产生相同指纹")
    void amountTrailingZeros_shouldBeEquivalent() {
        CreatePaymentRequest r1 = request("ACC001", "ACC002", "100.0",  "USD", "ref");
        CreatePaymentRequest r2 = request("ACC001", "ACC002", "100.00", "USD", "ref");

        assertEquals(generator.generate(r1), generator.generate(r2));
    }

    @Test
    @DisplayName("100 与 100.00 应产生相同指纹")
    void amountNoDecimal_shouldBeEquivalent() {
        CreatePaymentRequest r1 = request("ACC001", "ACC002", "100",    "USD", "ref");
        CreatePaymentRequest r2 = request("ACC001", "ACC002", "100.00", "USD", "ref");

        assertEquals(generator.generate(r1), generator.generate(r2));
    }

    // ==================== 币种大小写等价 ====================

    @Test
    @DisplayName("币种 usd 与 USD 应产生相同指纹")
    void currencyCase_shouldBeEquivalent() {
        CreatePaymentRequest r1 = request("ACC001", "ACC002", "500.00", "usd", "ref");
        CreatePaymentRequest r2 = request("ACC001", "ACC002", "500.00", "USD", "ref");

        assertEquals(generator.generate(r1), generator.generate(r2));
    }

    @Test
    @DisplayName("币种带空格 ' USD ' 与 'USD' 应产生相同指纹")
    void currencyWhitespace_shouldBeEquivalent() {
        CreatePaymentRequest r1 = request("ACC001", "ACC002", "500.00", " USD ", "ref");
        CreatePaymentRequest r2 = request("ACC001", "ACC002", "500.00", "USD",   "ref");

        assertEquals(generator.generate(r1), generator.generate(r2));
    }

    // ==================== 账户首尾空格等价 ====================

    @Test
    @DisplayName("账户首尾空格应被忽略")
    void accountWhitespace_shouldBeEquivalent() {
        CreatePaymentRequest r1 = request(" ACC001 ", " ACC002 ", "200.00", "GBP", "ref");
        CreatePaymentRequest r2 = request("ACC001",   "ACC002",   "200.00", "GBP", "ref");

        assertEquals(generator.generate(r1), generator.generate(r2));
    }

    // ==================== reference 空值等价 ====================

    @Test
    @DisplayName("reference 为 null 与空字符串应产生相同指纹")
    void referenceNullVsEmpty_shouldBeEquivalent() {
        CreatePaymentRequest r1 = request("ACC001", "ACC002", "300.00", "EUR", null);
        CreatePaymentRequest r2 = request("ACC001", "ACC002", "300.00", "EUR", "");

        assertEquals(generator.generate(r1), generator.generate(r2));
    }

    @Test
    @DisplayName("reference 带空格 '  ' 与空字符串应产生相同指纹")
    void referenceBlankVsEmpty_shouldBeEquivalent() {
        CreatePaymentRequest r1 = request("ACC001", "ACC002", "300.00", "EUR", "   ");
        CreatePaymentRequest r2 = request("ACC001", "ACC002", "300.00", "EUR", "");

        assertEquals(generator.generate(r1), generator.generate(r2));
    }

    // ==================== 不同内容 → 不同指纹 ====================

    @Test
    @DisplayName("不同金额应产生不同指纹")
    void differentAmount_shouldProduceDifferentFingerprint() {
        CreatePaymentRequest r1 = request("ACC001", "ACC002", "100.00", "CNY", "ref");
        CreatePaymentRequest r2 = request("ACC001", "ACC002", "200.00", "CNY", "ref");

        assertNotEquals(generator.generate(r1), generator.generate(r2));
    }

    @Test
    @DisplayName("不同来源账户应产生不同指纹")
    void differentSourceAccount_shouldProduceDifferentFingerprint() {
        CreatePaymentRequest r1 = request("ACC001", "ACC002", "100.00", "CNY", "ref");
        CreatePaymentRequest r2 = request("ACC999", "ACC002", "100.00", "CNY", "ref");

        assertNotEquals(generator.generate(r1), generator.generate(r2));
    }

    @Test
    @DisplayName("不同目标账户应产生不同指纹")
    void differentDestinationAccount_shouldProduceDifferentFingerprint() {
        CreatePaymentRequest r1 = request("ACC001", "ACC002", "100.00", "CNY", "ref");
        CreatePaymentRequest r2 = request("ACC001", "ACC999", "100.00", "CNY", "ref");

        assertNotEquals(generator.generate(r1), generator.generate(r2));
    }

    @Test
    @DisplayName("不同备注应产生不同指纹")
    void differentReference_shouldProduceDifferentFingerprint() {
        CreatePaymentRequest r1 = request("ACC001", "ACC002", "100.00", "CNY", "Invoice A");
        CreatePaymentRequest r2 = request("ACC001", "ACC002", "100.00", "CNY", "Invoice B");

        assertNotEquals(generator.generate(r1), generator.generate(r2));
    }

    // ==================== 指纹格式验证 ====================

    @Test
    @DisplayName("指纹应为 64 位十六进制字符串")
    void fingerprint_shouldBe64HexChars() {
        CreatePaymentRequest r = request("ACC001", "ACC002", "100.00", "CNY", "ref");
        String fp = generator.generate(r);

        assertNotNull(fp);
        assertEquals(64, fp.length(), "SHA-256 指纹应为 64 个字符");
        assertTrue(fp.matches("[0-9a-f]{64}"), "指纹应只包含小写十六进制字符");
    }

    // ==================== 入参校验 ====================

    @Test
    @DisplayName("request 为 null 时应抛出 IllegalArgumentException")
    void nullRequest_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(null));
    }

    @Test
    @DisplayName("amount 为 null 时应抛出 IllegalArgumentException")
    void nullAmount_shouldThrow() {
        CreatePaymentRequest r = new CreatePaymentRequest("ACC001", "ACC002", null, "CNY", "ref");
        assertThrows(IllegalArgumentException.class, () -> generator.generate(r));
    }

    @Test
    @DisplayName("currency 为 null 时应抛出 IllegalArgumentException")
    void nullCurrency_shouldThrow() {
        CreatePaymentRequest r = new CreatePaymentRequest("ACC001", "ACC002", new BigDecimal("100.00"), null, "ref");
        assertThrows(IllegalArgumentException.class, () -> generator.generate(r));
    }

    // ==================== 辅助方法 ====================

    /** 快速构造测试用请求对象 */
    private CreatePaymentRequest request(String src, String dst, String amount, String currency, String ref) {
        return new CreatePaymentRequest(src, dst, new BigDecimal(amount), currency, ref);
    }
}

