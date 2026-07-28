package com.example.demo.service;

import com.example.demo.dto.internal.IdempotencyDecision;
import com.example.demo.dto.request.CreatePaymentRequest;
import com.example.demo.entity.Payment;
import com.example.demo.enums.PaymentErrorCode;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.util.RequestFingerprintGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 幂等控制服务。
 *
 * <p><b>职责：</b>围绕客户端传入的 {@code Idempotency-Key} 进行统一校验与判定，
 * 对创建付款请求返回明确的幂等决策（新建 / 重放 / 冲突）。</p>
 *
 * <p><b>设计目标：</b></p>
 * <ul>
 *   <li>同键同内容：返回 REPLAY，复用已有付款</li>
 *   <li>同键不同内容：返回 CONFLICT，交由上层转换为 409</li>
 *   <li>新键：返回 ALLOW_CREATE，允许创建</li>
 * </ul>
 *
 * <p><b>并发说明：</b>即使应用层先查不到记录，在高并发场景下仍可能因数据库唯一约束
 * 发生竞争冲突。本类通过 {@link #handleConcurrentDuplicate(String, String, RuntimeException)}
 * 对该场景进行二次判定，保证结果一致。</p>
 *
 * <p><b>中文说明：</b>当前仓库尚未落地统一 BusinessException 体系，
 * 因此临时使用 {@link IllegalArgumentException}/{@link IllegalStateException}，
 * 并在消息前缀写入稳定错误码，便于后续无缝迁移。</p>
 */
@Service
public class PaymentIdempotencyService {

    /** Idempotency-Key 最大长度（来自文档约束）。 */
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    /** 兜底唯一约束关键字（用于解析异常消息）。 */
    private static final String UNIQUE_KEYWORD = "unique";

    /** 幂等键列名关键字（用于解析异常消息）。 */
    private static final String IDEMPOTENCY_COLUMN_KEYWORD = "idempotency_key";

    private final PaymentRepository paymentRepository;
    private final RequestFingerprintGenerator requestFingerprintGenerator;

    /**
     * 构造器注入。
     *
     * @param paymentRepository 付款仓储
     * @param requestFingerprintGenerator 请求指纹生成器
     */
    @Autowired
    public PaymentIdempotencyService(PaymentRepository paymentRepository,
                                     RequestFingerprintGenerator requestFingerprintGenerator) {
        this.paymentRepository = Objects.requireNonNull(paymentRepository, "paymentRepository must not be null");
        this.requestFingerprintGenerator = Objects.requireNonNull(
                requestFingerprintGenerator,
                "requestFingerprintGenerator must not be null"
        );
    }

    /**
     * 校验并规范化幂等键。
     *
     * <p>规则：</p>
     * <ol>
     *   <li>Header 值不能为空</li>
     *   <li>去除首尾空格后不能为空</li>
     *   <li>长度不能超过 100</li>
     * </ol>
     *
     * @param idempotencyKey 原始幂等键
     * @return 去除首尾空格后的幂等键
     * @throws IllegalArgumentException 当校验失败时抛出（错误码：VALIDATION_FAILED）
     */
    public String validateKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            throw validationFailure(PaymentErrorCode.VALIDATION_FAILED, "Idempotency-Key header is required");
        }

        String normalizedKey = idempotencyKey.trim();
        if (normalizedKey.isEmpty()) {
            throw validationFailure(PaymentErrorCode.VALIDATION_FAILED, "Idempotency-Key must not be blank");
        }
        if (normalizedKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw validationFailure(
                    PaymentErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key must not exceed " + IDEMPOTENCY_KEY_MAX_LENGTH + " characters"
            );
        }

        return normalizedKey;
    }

    /**
     * 执行幂等检查并返回判决。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>校验并规范化幂等键</li>
     *   <li>基于请求内容生成当前指纹</li>
     *   <li>按幂等键查询是否已有付款</li>
     *   <li>不存在：返回 ALLOW_CREATE</li>
     *   <li>存在且指纹相同：返回 REPLAY</li>
     *   <li>存在但指纹不同：返回 CONFLICT</li>
     * </ol>
     *
     * @param idempotencyKey 请求头中的幂等键
     * @param request 创建付款请求
     * @return 幂等决策对象
     */
    public IdempotencyDecision check(String idempotencyKey, CreatePaymentRequest request) {
        String normalizedKey = validateKey(idempotencyKey);
        String currentFingerprint = requestFingerprintGenerator.generate(request);

        Optional<Payment> existingPaymentOpt = paymentRepository.findByIdempotencyKey(normalizedKey);
        if (existingPaymentOpt.isEmpty()) {
            return IdempotencyDecision.allowCreate(currentFingerprint);
        }

        Payment existingPayment = existingPaymentOpt.get();
        String existingFingerprint = normalizeNullable(existingPayment.getRequestFingerprint());

        // 同键同内容：允许重放并直接复用已有付款。
        if (currentFingerprint.equals(existingFingerprint)) {
            return IdempotencyDecision.replay(currentFingerprint, existingPayment);
        }

        // 同键不同内容：返回冲突决策，由上层映射为 409 响应。
        return IdempotencyDecision.conflict(currentFingerprint, existingFingerprint);
    }

    /**
     * 处理并发下的唯一键竞争冲突。
     *
     * <p>典型场景：两个并发请求几乎同时通过“未找到”检查，随后都尝试 insert，
     * 其中一个会因数据库唯一约束失败。此时应重新查询并按指纹做二次判定。</p>
     *
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>先确认异常确实来自幂等键唯一约束，否则原样抛出</li>
     *   <li>按幂等键重新查询已有记录</li>
     *   <li>指纹相同：返回 REPLAY</li>
     *   <li>指纹不同：返回 CONFLICT</li>
     * </ol>
     *
     * @param idempotencyKey 原始幂等键
     * @param fingerprint 当前请求指纹（由调用方生成）
     * @param databaseException 数据库异常
     * @return 幂等决策（REPLAY 或 CONFLICT）
     * @throws RuntimeException 当异常并非唯一约束冲突，或重查仍找不到记录时抛出
     */
    public IdempotencyDecision handleConcurrentDuplicate(String idempotencyKey,
                                                         String fingerprint,
                                                         RuntimeException databaseException) {
        String normalizedKey = validateKey(idempotencyKey);
        if (fingerprint == null || fingerprint.trim().isEmpty()) {
            throw validationFailure(PaymentErrorCode.VALIDATION_FAILED, "fingerprint must not be blank");
        }
        if (databaseException == null) {
            throw validationFailure(PaymentErrorCode.VALIDATION_FAILED, "databaseException must not be null");
        }

        // 若不是幂等键唯一约束导致的异常，不应伪装成重复请求，直接向上抛。
        if (!isLikelyIdempotencyUniqueViolation(databaseException)) {
            throw databaseException;
        }

        Optional<Payment> existingPaymentOpt = paymentRepository.findByIdempotencyKey(normalizedKey);
        if (existingPaymentOpt.isEmpty()) {
            throw new IllegalStateException(
                    PaymentErrorCode.PROCESSING_ERROR.name()
                            + ": unique conflict detected but no payment found for key=" + normalizedKey,
                    databaseException
            );
        }

        Payment existingPayment = existingPaymentOpt.get();
        String existingFingerprint = normalizeNullable(existingPayment.getRequestFingerprint());

        if (fingerprint.equals(existingFingerprint)) {
            return IdempotencyDecision.replay(fingerprint, existingPayment);
        }

        return IdempotencyDecision.conflict(fingerprint, existingFingerprint);
    }

    /**
     * 判断异常是否“很可能”是幂等键唯一约束冲突。
     *
     * <p>策略：</p>
     * <ul>
     *   <li>优先识别 {@link DataIntegrityViolationException}</li>
     *   <li>识别 SQLState = 23505（常见唯一键冲突）</li>
     *   <li>兜底解析异常消息中的关键字（unique + idempotency_key）</li>
     * </ul>
     *
     * <p>中文说明：不同数据库/驱动异常结构差异较大，当前实现采用“多信号”判断，
     * 后续可在统一异常体系中改为更精确的数据库方言识别。</p>
     *
     * @param exception 数据库异常
     * @return true 表示疑似幂等键唯一冲突
     */
    private boolean isLikelyIdempotencyUniqueViolation(RuntimeException exception) {
        if (exception instanceof DataIntegrityViolationException) {
            Throwable root = rootCause(exception);
            if (root instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if ("23505".equals(sqlState)) {
                    return true;
                }
            }
        }

        String fullMessage = buildExceptionMessageChain(exception).toLowerCase(Locale.ROOT);
        return fullMessage.contains(UNIQUE_KEYWORD) && fullMessage.contains(IDEMPOTENCY_COLUMN_KEYWORD);
    }

    /**
     * 获取最底层 cause。
     *
     * @param throwable 异常对象
     * @return 根因异常
     */
    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * 拼接异常链消息，便于关键词匹配。
     *
     * @param throwable 异常对象
     * @return 异常链消息
     */
    private String buildExceptionMessageChain(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    /**
     * 把可空字符串规范化为非空字符串。
     *
     * @param value 原值
     * @return 非空字符串（null -> 空串）
     */
    private String normalizeNullable(String value) {
        return value == null ? "" : value;
    }

    /**
     * 统一创建校验异常。
     *
     * @param errorCode 错误码
     * @param message 错误描述
     * @return IllegalArgumentException
     */
    private IllegalArgumentException validationFailure(PaymentErrorCode errorCode, String message) {
        return new IllegalArgumentException(errorCode.name() + ": " + message);
    }
}

