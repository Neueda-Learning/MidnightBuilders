package com.example.demo.service;

import com.example.demo.dto.request.CreatePaymentRequest;
import com.example.demo.entity.Payment;
import com.example.demo.enums.PaymentErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 付款业务校验服务
 *
 * <p>职责：统一校验创建付款与处理付款前的业务规则，保证金额、账户和币种
 * 在进入后续幂等控制、持久化和状态流转之前已经通过基础约束检查。</p>
 *
 * <p>该类遵循“失败即停止”策略：一旦发现首个不合法字段，立即抛出异常，
 * 不继续向下执行其他校验，以便调用方快速得到明确错误原因。</p>
 *
 * <p>中文说明：当前实现不依赖数据库，只负责纯业务规则判断；配置项通过
 * 构造器注入，方便在测试中替换为固定值。</p>
 */
@Service
@SuppressWarnings("unused")
public class PaymentValidationService {

    /**
     * 默认最大付款金额。
     *
     * <p>根据设计文档，第一轮迭代默认上限为 1,000,000.00。</p>
     */
    private static final BigDecimal DEFAULT_MAX_AMOUNT = new BigDecimal("1000000.00");

    /**
     * 默认最大金额文本，供 Spring @Value 注解引用。
     */
    private static final String DEFAULT_MAX_AMOUNT_TEXT = "1000000.00";

    /**
     * 默认支持币种文本，供 Spring @Value 注解引用。
     */
    private static final String DEFAULT_SUPPORTED_CURRENCIES_TEXT = "USD,EUR,GBP,CNY";

    /**
     * 最大允许金额。
     */
    private final BigDecimal maxAmount;

    /**
     * 支持币种集合，统一使用大写保存。
     */
    private final Set<String> supportedCurrencies;

    /**
     * 账户格式正则。
     */
    private final Pattern accountPattern;

    /**
     * Spring 注入构造器。
     *
     * <p>中文说明：如果配置项不存在，则使用文档给出的默认值，保证项目在未补齐
     * 配置文件时依然可以启动和测试。</p>
     *
     * @param maxAmountText 最大金额配置，默认 1000000.00
     * @param supportedCurrenciesText 支持币种配置，逗号分隔，例如 USD,EUR,GBP,CNY
     * @param accountPatternText 账户格式正则
     */
    @Autowired
    public PaymentValidationService(
            @Value("${payment.validation.max-amount:" + DEFAULT_MAX_AMOUNT_TEXT + "}") String maxAmountText,
            @Value("${payment.validation.supported-currencies:" + DEFAULT_SUPPORTED_CURRENCIES_TEXT + "}") String supportedCurrenciesText,
            @Value("${payment.validation.account-pattern:^[A-Za-z0-9]{3,50}$}") String accountPatternText) {
        this(parseAmount(maxAmountText), parseSupportedCurrencies(supportedCurrenciesText), Pattern.compile(accountPatternText));
    }

    /**
     * 测试/内部构造器。
     *
     * <p>中文说明：单元测试可以直接传入固定配置，避免依赖 Spring 容器。</p>
     *
     * @param maxAmount 最大金额
     * @param supportedCurrencies 支持币种集合
     * @param accountPattern 账户格式正则
     */
    PaymentValidationService(BigDecimal maxAmount, Set<String> supportedCurrencies, Pattern accountPattern) {
        this.maxAmount = Objects.requireNonNull(maxAmount, "maxAmount must not be null");
        this.supportedCurrencies = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(supportedCurrencies, "supportedCurrencies must not be null")));
        String patternText = accountPattern == null ? "^[A-Za-z0-9]{3,50}$" : accountPattern.pattern();
        this.accountPattern = Pattern.compile(patternText);
    }

    /**
     * 校验创建付款请求。
     *
     * <p>校验顺序：</p>
     * <ol>
     *   <li>账户字段</li>
     *   <li>金额字段</li>
     *   <li>币种字段</li>
     * </ol>
     *
     * <p>备注字段的长度和空值约束由请求 DTO 的 Bean Validation 负责，
     * 这里不重复校验。</p>
     *
     * @param request 创建请求，不能为空
     */
    @SuppressWarnings("unused")
    public void validateCreateRequest(CreatePaymentRequest request) {
        if (request == null) {
            throw validationFailure(PaymentErrorCode.VALIDATION_FAILED, "CreatePaymentRequest must not be null");
        }

        validateAccounts(request.getSourceAccount(), request.getDestinationAccount());
        validateAmount(request.getAmount());
        validateCurrency(request.getCurrency());
    }

    /**
     * 校验付款在处理前是否仍满足业务规则。
     *
     * <p>中文说明：即使创建阶段已经校验过，也建议在进入处理流程前再次校验，
     * 避免历史脏数据或未来扩展导致的非法状态进入处理环节。</p>
     *
     * @param payment 已持久化付款实体，不能为空
     */
    @SuppressWarnings("unused")
    public void validatePaymentForProcessing(Payment payment) {
        if (payment == null) {
            throw validationFailure(PaymentErrorCode.VALIDATION_FAILED, "Payment must not be null");
        }

        if (payment.getStatus() == null) {
            throw validationFailure(PaymentErrorCode.VALIDATION_FAILED, "Payment status must not be null");
        }

        validateAccounts(payment.getSourceAccount(), payment.getDestinationAccount());
        validateAmount(payment.getAmount());
        validateCurrency(payment.getCurrency());
    }

    /**
     * 校验金额。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>不能为空</li>
     *   <li>必须大于 0</li>
     *   <li>不得超过配置上限</li>
     *   <li>最多两位小数</li>
     * </ul>
     *
     * @param amount 金额
     */
    public void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw validationFailure(PaymentErrorCode.INVALID_AMOUNT, "amount must not be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw validationFailure(PaymentErrorCode.INVALID_AMOUNT, "amount must be greater than 0");
        }
        if (amount.compareTo(maxAmount) > 0) {
            throw validationFailure(PaymentErrorCode.INVALID_AMOUNT,
                    "amount must not exceed " + maxAmount.toPlainString());
        }
        if (amount.scale() > 2) {
            throw validationFailure(PaymentErrorCode.INVALID_AMOUNT, "amount must have at most 2 decimal places");
        }
    }

    /**
     * 校验来源账户和目标账户。
     *
     * <p>规则：</p>
     * <ol>
     *   <li>不能为空或纯空白</li>
     *   <li>去除首尾空格后必须符合账户格式</li>
     *   <li>规范化后两个账户不能相同</li>
     * </ol>
     *
     * <p>中文说明：先校验各自格式，再比较是否相同，可以让错误提示更清晰。
     * 如果两个字段都合法但相同，则返回 {@link PaymentErrorCode#SAME_SOURCE_AND_DESTINATION}；
     * 否则返回 {@link PaymentErrorCode#INVALID_ACCOUNT}。</p>
     *
     * @param source 来源账户
     * @param destination 目标账户
     */
    public void validateAccounts(String source, String destination) {
        String normalizedSource = normalizeAccount(source);
        String normalizedDestination = normalizeAccount(destination);

        if (normalizedSource.isEmpty() || normalizedDestination.isEmpty()) {
            throw validationFailure(PaymentErrorCode.INVALID_ACCOUNT, "sourceAccount and destinationAccount must not be blank");
        }
        if (!accountPattern.matcher(normalizedSource).matches() || !accountPattern.matcher(normalizedDestination).matches()) {
            throw validationFailure(PaymentErrorCode.INVALID_ACCOUNT, "account format is invalid");
        }
        if (normalizedSource.equals(normalizedDestination)) {
            throw validationFailure(PaymentErrorCode.SAME_SOURCE_AND_DESTINATION,
                    "sourceAccount and destinationAccount must be different");
        }
    }

    /**
     * 校验币种。
     *
     * <p>规则：</p>
     * <ol>
     *   <li>不能为空或纯空白</li>
     *   <li>去除首尾空格后转为大写</li>
     *   <li>必须为三位字母代码</li>
     *   <li>必须属于支持币种集合</li>
     * </ol>
     *
     * @param currency 币种
     */
    public void validateCurrency(String currency) {
        String normalizedCurrency = normalizeCurrency(currency);

        if (normalizedCurrency.isEmpty()) {
            throw validationFailure(PaymentErrorCode.INVALID_CURRENCY, "currency must not be blank");
        }
        if (!normalizedCurrency.matches("^[A-Z]{3}$")) {
            throw validationFailure(PaymentErrorCode.INVALID_CURRENCY, "currency must be a 3-letter uppercase code");
        }
        if (!supportedCurrencies.contains(normalizedCurrency)) {
            throw validationFailure(PaymentErrorCode.INVALID_CURRENCY,
                    "currency is not supported: " + normalizedCurrency);
        }
    }

    /**
     * 规范化币种。
     *
     * <p>中文说明：该方法仅负责格式化，不做支持集合检查，便于校验、保存和指纹生成
     * 使用同一套标准化结果。</p>
     *
     * @param currency 原始币种
     * @return 去空格并转大写后的币种；若入参为 null，则返回空字符串以便上层统一报错
     */
    public String normalizeCurrency(String currency) {
        if (currency == null) {
            return "";
        }
        return currency.trim().toUpperCase();
    }

    /**
     * 规范化账户字符串。
     *
     * @param account 原始账户
     * @return 去除首尾空格后的账户；若为 null，则返回空字符串
     */
    private String normalizeAccount(String account) {
        if (account == null) {
            return "";
        }
        return account.trim();
    }

    /**
     * 解析金额配置。
     *
     * @param amountText 金额文本
     * @return BigDecimal 金额
     */
    private static BigDecimal parseAmount(String amountText) {
        if (amountText == null || amountText.trim().isEmpty()) {
            return DEFAULT_MAX_AMOUNT;
        }
        return new BigDecimal(amountText.trim());
    }

    /**
     * 解析支持币种集合。
     *
     * <p>中文说明：配置值以英文逗号分隔，解析后统一转大写并去重。</p>
     *
     * @param supportedCurrenciesText 配置文本
     * @return 支持币种集合
     */
    private static Set<String> parseSupportedCurrencies(String supportedCurrenciesText) {
        String raw = (supportedCurrenciesText == null || supportedCurrenciesText.trim().isEmpty())
                ? DEFAULT_SUPPORTED_CURRENCIES_TEXT
                : supportedCurrenciesText;

        Set<String> currencies = new LinkedHashSet<>();
        for (String item : raw.split(",")) {
            String normalized = item.trim().toUpperCase();
            if (!normalized.isEmpty()) {
                currencies.add(normalized);
            }
        }

        if (currencies.isEmpty()) {
            for (String item : DEFAULT_SUPPORTED_CURRENCIES_TEXT.split(",")) {
                currencies.add(item.trim().toUpperCase());
            }
        }

        return currencies;
    }

    /**
     * 构造业务校验失败异常。
     *
     * <p>由于当前仓库还没有统一的 BusinessException 实现，这里使用
     * IllegalArgumentException，并把稳定错误码放在消息前缀中，方便后续统一异常处理器复用。</p>
     *
     * @param errorCode 稳定错误码
     * @param message 错误描述
     * @return 非法参数异常
     */
    private IllegalArgumentException validationFailure(PaymentErrorCode errorCode, String message) {
        return new IllegalArgumentException(errorCode.name() + ": " + message);
    }
}

