package com.example.demo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 付款业务配置属性。
 *
 * <p><b>用途：</b>把第一轮迭代中“可变的业务规则”集中在一个配置对象中，
 * 避免把金额上限、支持币种、账户格式、幂等键长度等常量分散在多个类里。</p>
 *
 * <p><b>绑定前缀：</b>{@code payment}</p>
 *
 * <p><b>示例配置：</b></p>
 * <pre>{@code
 * payment:
 *   validation:
 *     max-amount: 1000000.00
 *     supported-currencies: [USD, EUR, GBP, CNY]
 *     account-pattern: "^[A-Za-z0-9]{3,50}$"
 *   idempotency:
 *     key-max-length: 100
 * }</pre>
 *
 * <p><b>中文说明：</b>该类本身不直接做业务校验流程编排，
 * 只是提供统一规则来源；实际校验仍由 Service 层执行。</p>
 */
@Component
@Validated
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    /**
     * 创建/处理付款相关的校验配置。
     */
    @Valid
    @NotNull
    private Validation validation = new Validation();

    /**
     * 幂等控制相关配置。
     */
    @Valid
    @NotNull
    private Idempotency idempotency = new Idempotency();

    /**
     * 获取校验配置。
     *
     * @return 校验配置对象
     */
    public Validation getValidation() {
        return validation;
    }

    /**
     * 设置校验配置。
     *
     * @param validation 校验配置对象
     */
    public void setValidation(Validation validation) {
        this.validation = validation;
    }

    /**
     * 获取幂等配置。
     *
     * @return 幂等配置对象
     */
    public Idempotency getIdempotency() {
        return idempotency;
    }

    /**
     * 设置幂等配置。
     *
     * @param idempotency 幂等配置对象
     */
    public void setIdempotency(Idempotency idempotency) {
        this.idempotency = idempotency;
    }

    /**
     * 业务校验规则配置分组。
     */
    @Validated
    public static class Validation {

        /**
         * 最大允许付款金额。
         *
         * <p>默认值 1,000,000.00，来自接口契约文档的第一轮约束。</p>
         */
        @NotNull
        @DecimalMin(value = "0.01", inclusive = true)
        private BigDecimal maxAmount = new BigDecimal("1000000.00");

        /**
         * 支持币种集合。
         *
         * <p>中文说明：文档中存在示例差异（GBP/USD/EUR 与 CNY），
         * 这里默认保留四种常见值，并允许通过配置覆盖。</p>
         */
        @NotEmpty
        private Set<@NotBlank String> supportedCurrencies =
                new LinkedHashSet<>(Set.of("USD", "EUR", "GBP", "CNY"));

        /**
         * 账户格式正则。
         *
         * <p>默认：3~50 位字母数字。若后续需求明确更严格格式，可直接在配置中替换。</p>
         */
        @NotBlank
        private String accountPattern = "^[A-Za-z0-9]{3,50}$";

        /**
         * 账户最小长度（用于快速边界判断，正则仍是最终格式规则）。
         */
        @Min(1)
        private int accountMinLength = 3;

        /**
         * 账户最大长度。
         */
        @Min(1)
        private int accountMaxLength = 50;

        public BigDecimal getMaxAmount() {
            return maxAmount;
        }

        public void setMaxAmount(BigDecimal maxAmount) {
            this.maxAmount = maxAmount;
        }

        public Set<String> getSupportedCurrencies() {
            return supportedCurrencies;
        }

        public void setSupportedCurrencies(Set<String> supportedCurrencies) {
            this.supportedCurrencies = supportedCurrencies;
        }

        public String getAccountPattern() {
            return accountPattern;
        }

        public void setAccountPattern(String accountPattern) {
            this.accountPattern = accountPattern;
        }

        public int getAccountMinLength() {
            return accountMinLength;
        }

        public void setAccountMinLength(int accountMinLength) {
            this.accountMinLength = accountMinLength;
        }

        public int getAccountMaxLength() {
            return accountMaxLength;
        }

        public void setAccountMaxLength(int accountMaxLength) {
            this.accountMaxLength = accountMaxLength;
        }

        /**
         * 规范化币种：去空格并转大写。
         *
         * @param currency 原始币种
         * @return 规范化后的币种；若入参为 null，返回空字符串
         */
        public String normalizeCurrency(String currency) {
            if (currency == null) {
                return "";
            }
            return currency.trim().toUpperCase(Locale.ROOT);
        }

        /**
         * 规范化账户：去除首尾空格。
         *
         * @param account 原始账户
         * @return 规范化账户；若入参为 null，返回空字符串
         */
        public String normalizeAccount(String account) {
            if (account == null) {
                return "";
            }
            return account.trim();
        }
    }

    /**
     * 幂等规则配置分组。
     */
    @Validated
    public static class Idempotency {

        /**
         * Idempotency-Key 最大长度。
         *
         * <p>默认值 100，和接口契约及数据库字段定义保持一致。</p>
         */
        @Min(1)
        private int keyMaxLength = 100;

        public int getKeyMaxLength() {
            return keyMaxLength;
        }

        public void setKeyMaxLength(int keyMaxLength) {
            this.keyMaxLength = keyMaxLength;
        }

        /**
         * 规范化幂等键：去除首尾空格。
         *
         * @param key 原始幂等键
         * @return 规范化后的键；若入参为 null，返回空字符串
         */
        public String normalizeKey(String key) {
            if (key == null) {
                return "";
            }
            return key.trim();
        }
    }
}

