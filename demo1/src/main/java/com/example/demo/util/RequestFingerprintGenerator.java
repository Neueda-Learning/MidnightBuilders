package com.example.demo.util;

import com.example.demo.dto.request.CreatePaymentRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 请求指纹生成器
 *
 * <p><b>用途：</b>为 {@link CreatePaymentRequest} 的业务内容生成一个稳定、固定长度的摘要字符串（指纹）。
 * 该指纹与幂等键（Idempotency-Key）配合使用，用于判断同一个幂等键对应的两次请求内容是否完全相同。</p>
 *
 * <p><b>使用场景：</b></p>
 * <ul>
 *   <li>同一个幂等键 + 相同指纹 → 判定为重放，直接返回已有付款（HTTP 200）</li>
 *   <li>同一个幂等键 + 不同指纹 → 判定为冲突，拒绝请求（HTTP 409 DUPLICATE_PAYMENT）</li>
 * </ul>
 *
 * <p><b>稳定性保证：</b>指纹计算遵循固定的规范化规则和字段顺序，
 * 确保逻辑等价的请求（如金额 {@code 100.0} 与 {@code 100.00}）产生相同的指纹，
 * 而语义不同的请求始终产生不同的指纹。</p>
 *
 * <p><b>安全说明：</b>指纹仅用于请求内容等价判断，不用于身份认证或签名验证。
 * 使用 SHA-256 算法是为了避免哈希碰撞导致的误判，而非加密目的。</p>
 *
 * <p><b>与幂等键的关系：</b>幂等键由客户端生成并通过 HTTP Header 传入，
 * 指纹由服务端根据请求体内容计算。两者共同构成幂等控制机制。</p>
 *
 * @author Payment Processing System Team
 * @version 1.0.0
 * @since 2026-07
 * @see com.example.demo.service.PaymentIdempotencyService
 */
@Component
public class RequestFingerprintGenerator {

    /**
     * 指纹各字段之间的分隔符
     *
     * <p>使用 {@code |} 作为分隔符，避免字段拼接时出现歧义。
     * 例如：sourceAccount="A", destinationAccount="B|C" 不会与
     * sourceAccount="A|B", destinationAccount="C" 产生相同结果。</p>
     */
    private static final String FIELD_SEPARATOR = "|";

    /**
     * 摘要算法名称
     *
     * <p>使用 SHA-256 生成 256 位（64 个十六进制字符）固定长度指纹，
     * 碰撞概率极低，满足幂等等价判断的安全性要求。</p>
     */
    private static final String DIGEST_ALGORITHM = "SHA-256";

    /**
     * 为付款创建请求生成稳定的内容指纹
     *
     * <p>指纹的计算过程：</p>
     * <ol>
     *   <li>对 {@code sourceAccount} 和 {@code destinationAccount} 去除首尾空格</li>
     *   <li>对 {@code currency} 去除首尾空格并转换为大写（确保 {@code "usd"} 与 {@code "USD"} 等价）</li>
     *   <li>对 {@code amount} 去除尾零后转为字符串，确保 {@code 100.0} 与 {@code 100.00} 产生相同指纹</li>
     *   <li>对 {@code reference} 去除首尾空格；若为 {@code null} 则视为空字符串</li>
     *   <li>按固定顺序（sourceAccount, destinationAccount, amount, currency, reference）拼接字段</li>
     *   <li>对拼接结果计算 SHA-256 摘要，以十六进制字符串返回</li>
     * </ol>
     *
     * <p><b>注意：</b>规范化逻辑必须与 {@code PaymentValidationService} 和
     * {@code PaymentMapper} 中使用的规范化保持一致，确保同一请求在校验、
     * 指纹计算和保存三个阶段使用相同的规范化结果。</p>
     *
     * @param request 付款创建请求，不能为 {@code null}
     * @return 64 个字符的十六进制 SHA-256 指纹字符串，不为 {@code null}
     * @throws IllegalArgumentException 如果 {@code request} 为 {@code null}，
     *                                  或 {@code amount}、{@code sourceAccount}、
     *                                  {@code destinationAccount}、{@code currency} 任一为 {@code null}
     * @throws IllegalStateException    如果当前 JVM 不支持 SHA-256 算法（实际不应发生）
     */
    public String generate(CreatePaymentRequest request) {
        // 入参空值校验
        if (request == null) {
            throw new IllegalArgumentException("请求对象不能为空");
        }
        if (request.getSourceAccount() == null) {
            throw new IllegalArgumentException("来源账户不能为空");
        }
        if (request.getDestinationAccount() == null) {
            throw new IllegalArgumentException("目标账户不能为空");
        }
        if (request.getAmount() == null) {
            throw new IllegalArgumentException("付款金额不能为空");
        }
        if (request.getCurrency() == null) {
            throw new IllegalArgumentException("币种不能为空");
        }

        // 第一步：规范化各字段
        // 账户字段：去除首尾空格
        String normalizedSource      = request.getSourceAccount().trim();
        String normalizedDestination = request.getDestinationAccount().trim();

        // 金额字段：去除尾零，使 100.0 与 100.00 等价
        // 使用 stripTrailingZeros + toPlainString 避免科学计数法（如 1E+2）
        String normalizedAmount = stripTrailingZerosToPlainString(request.getAmount());

        // 币种字段：去除首尾空格并转大写，使 "usd" 与 "USD" 等价
        String normalizedCurrency = request.getCurrency().trim().toUpperCase();

        // 备注字段：可选字段，null 视为空字符串；去除首尾空格
        String normalizedReference = (request.getReference() == null)
                ? ""
                : request.getReference().trim();

        // 第二步：按固定顺序拼接，使用分隔符避免字段边界歧义
        // 字段顺序：sourceAccount | destinationAccount | amount | currency | reference
        String rawContent = normalizedSource
                + FIELD_SEPARATOR + normalizedDestination
                + FIELD_SEPARATOR + normalizedAmount
                + FIELD_SEPARATOR + normalizedCurrency
                + FIELD_SEPARATOR + normalizedReference;

        // 第三步：计算 SHA-256 摘要，返回十六进制字符串
        return computeSha256Hex(rawContent);
    }

    /**
     * 将 BigDecimal 去除尾零后转换为普通十进制字符串
     *
     * <p>示例：</p>
     * <ul>
     *   <li>{@code 100.00} → {@code "100"}</li>
     *   <li>{@code 100.10} → {@code "100.1"}</li>
     *   <li>{@code 0.50}   → {@code "0.5"}</li>
     *   <li>{@code 1000}   → {@code "1000"}（使用 toPlainString 避免 1E+3）</li>
     * </ul>
     *
     * <p>使用 {@link BigDecimal#stripTrailingZeros()} 去除尾零后，
     * 再用 {@link BigDecimal#toPlainString()} 防止出现科学计数法表示。</p>
     *
     * @param amount 待转换的金额，不能为 {@code null}
     * @return 规范化后的金额字符串
     */
    private String stripTrailingZerosToPlainString(BigDecimal amount) {
        // stripTrailingZeros() 去除尾部零，例如 100.00 → 1E+2
        // toPlainString() 将科学计数法还原为普通小数表示，例如 1E+2 → 100
        return amount.stripTrailingZeros().toPlainString();
    }

    /**
     * 计算字符串的 SHA-256 摘要并以十六进制字符串返回
     *
     * <p>输入字符串使用 UTF-8 编码转换为字节数组后计算摘要，
     * 保证跨平台结果一致。</p>
     *
     * <p>返回的字符串长度固定为 64 个字符（SHA-256 产生 32 字节 = 64 个十六进制字符）。</p>
     *
     * @param input 待摘要的原始字符串，不能为 {@code null}
     * @return 64 字符的十六进制摘要字符串，全小写
     * @throws IllegalStateException 如果 JVM 不支持 SHA-256（标准 JVM 不应发生）
     */
    private String computeSha256Hex(String input) {
        try {
            // 获取 SHA-256 MessageDigest 实例
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);

            // 使用 UTF-8 编码，确保跨平台一致性
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // 将字节数组转换为十六进制字符串
            return bytesToHex(hashBytes);

        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 标准算法，所有符合规范的 JVM 都必须支持
            // 理论上不会到达此分支，若发生则是运行环境异常
            throw new IllegalStateException(
                    "当前 JVM 不支持 SHA-256 算法，这不应该发生：" + e.getMessage(), e);
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * <p>每个字节转换为两个十六进制字符（不足两位时前补零），
     * 结果为全小写的十六进制字符串。</p>
     *
     * <p>示例：字节 {@code 0x0A} 转换为 {@code "0a"}，而非 {@code "a"}。</p>
     *
     * @param bytes 待转换的字节数组
     * @return 十六进制字符串，长度为字节数组长度的两倍
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            // 0xFF 按位与操作确保将 byte（有符号）转换为无符号整数（0-255）
            // format("%02x") 保证两位输出，不足时前补零
            hexString.append(String.format("%02x", b & 0xFF));
        }
        return hexString.toString();
    }
}

