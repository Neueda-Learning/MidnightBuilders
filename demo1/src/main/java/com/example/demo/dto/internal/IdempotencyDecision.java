package com.example.demo.dto.internal;

import com.example.demo.entity.Payment;
import java.util.Objects;

/**
 * 幂等性判决结果对象
 *
 * 用途：PaymentIdempotencyService 对幂等键的检查结果。
 * 该内部 DTO 在服务间传递幂等性判决，不参与前端 API 契约。
 *
 * 该类表示幂等检查后的三种互斥决策：
 * 1. 新建：当前幂等键首次提交
 * 2. 重放：同幂等键同请求指纹的重复提交
 * 3. 冲突：同幂等键但不同请求指纹的冲突提交（导致 409 Conflict）
 *
 * @author Payment Processing System Team
 * @version 1.0.0
 * @since 2026-07
 */
public class IdempotencyDecision {

    /**
     * 判决类型枚举
     *
     * 标识幂等检查后的结果分类：
     * - ALLOW_CREATE: 允许创建新付款，幂等键未曾出现
     * - REPLAY: 重放决策，幂等键已存在且请求指纹相同
     * - CONFLICT: 冲突决策，幂等键已存在但请求指纹不同
     */
    public enum DecisionType {
        /**
         * 允许创建新的付款
         *
         * 场景：客户端首次使用该幂等键提交创建请求。
         * 返回值中 payment 为空，fingerprint 包含当前请求计算的指纹。
         */
        ALLOW_CREATE,

        /**
         * 重放决策 - 返回已有付款实体
         *
         * 场景：客户端使用相同幂等键和相同请求内容重新提交。
         * 返回值中 payment 为该幂等键对应的已有 Payment，
         * fingerprint 为原请求的指纹（与当前请求相同）。
         */
        REPLAY,

        /**
         * 冲突决策 - 拒绝提交
         *
         * 场景：客户端使用相同幂等键但提交了不同的请求内容。
         * 此时服务应抛 DuplicatePaymentException，
         * Controller 最终返回 409 Conflict 错误响应。
         */
        CONFLICT
    }

    /**
     * 幂等决策的类型
     *
     * 决定了当前幂等检查后的处理方向。
     */
    private final DecisionType decisionType;

    /**
     * 当前请求的指纹哈希值
     *
     * 当前请求的稳定摘要，通过 RequestFingerprintGenerator 计算。
     * 用于：
     * - ALLOW_CREATE: 供后续保存，与 Payment 关联
     * - REPLAY: 应与数据库中已有指纹一致
     * - CONFLICT: 用于日志和诊断
     *
     * 不可为空。
     */
    private final String fingerprint;

    /**
     * 已存在的 Payment 实体（仅在 REPLAY 时非空）
     *
     * 当决策为 REPLAY 时，包含该幂等键对应的已有 Payment 实体。
     * 其他决策类型时为 null。
     *
     * 该 Payment 不应被修改，仅作为查询返回或一致性判断。
     * 对应状态应为 CREATED（第一轮中处理后的状态会变更）。
     */
    private final Payment existingPayment;

    /**
     * 数据库原始指纹（仅在 CONFLICT 诊断时有用）
     *
     * 当决策为 CONFLICT 时，包含数据库中该幂等键对应的旧指纹。
     * 用于诊断和日志记录，帮助追踪幂等冲突的原因。
     *
     * 其他决策类型通常不关心此字段。
     */
    private final String existingFingerprint;

    /**
     * 私有构造器 - 禁止直接实例化
     *
     * 所有实例化必须通过静态工厂方法创建，
     * 以确保对象的不变性和一致性。
     *
     * @param decisionType 幂等决策类型
     * @param fingerprint 当前请求指纹
     * @param existingPayment 已有付款实体（nullable）
     * @param existingFingerprint 已有指纹（nullable）
     */
    private IdempotencyDecision(
            DecisionType decisionType,
            String fingerprint,
            Payment existingPayment,
            String existingFingerprint) {
        this.decisionType = Objects.requireNonNull(decisionType, "判决类型不能为空");
        this.fingerprint = Objects.requireNonNull(fingerprint, "请求指纹不能为空");
        this.existingPayment = existingPayment;
        this.existingFingerprint = existingFingerprint;
    }

    /**
     * 创建"允许创建"决策
     *
     * 用于当前幂等键首次出现的场景。
     * PaymentService 应根据此决策新建 Payment 并保存。
     *
     * @param fingerprint 当前请求的指纹哈希值，不能为空
     * @return 表示"允许创建"的 IdempotencyDecision 实例
     * @throws NullPointerException 如果 fingerprint 为 null
     */
    public static IdempotencyDecision allowCreate(String fingerprint) {
        Objects.requireNonNull(fingerprint, "指纹不能为空");
        return new IdempotencyDecision(
                DecisionType.ALLOW_CREATE,
                fingerprint,
                null,  // 新建时无已有 Payment
                null   // 新建时无已有指纹
        );
    }

    /**
     * 创建"重放"决策
     *
     * 用于同幂等键、同请求指纹的重复提交场景。
     * PaymentService 应直接返回已有 Payment，无需新建。
     * Controller 返回 200 而非 201。
     *
     * @param fingerprint 当前请求的指纹哈希值，不能为空
     * @param existingPayment 该幂等键对应的已有 Payment 实体，不能为空
     * @return 表示"重放"的 IdempotencyDecision 实例
     * @throws NullPointerException 如果参数为 null
     */
    public static IdempotencyDecision replay(String fingerprint, Payment existingPayment) {
        Objects.requireNonNull(fingerprint, "指纹不能为空");
        Objects.requireNonNull(existingPayment, "已有付款不能为空");
        return new IdempotencyDecision(
                DecisionType.REPLAY,
                fingerprint,
                existingPayment,
                null  // 重放时无需原有指纹（已通过 existingPayment 获得内容）
        );
    }

    /**
     * 创建"冲突"决策
     *
     * 用于同幂等键但请求指纹不同（即请求内容不同）的场景。
     * PaymentService 应抛 DuplicatePaymentException，
     * 最终向客户端返回 409 Conflict。
     *
     * @param currentFingerprint 当前请求的指纹哈希值，不能为空
     * @param existingFingerprint 数据库中该幂等键对应的已有指纹，不能为空
     * @return 表示"冲突"的 IdempotencyDecision 实例
     * @throws NullPointerException 如果参数为 null
     */
    public static IdempotencyDecision conflict(
            String currentFingerprint,
            String existingFingerprint) {
        Objects.requireNonNull(currentFingerprint, "当前指纹不能为空");
        Objects.requireNonNull(existingFingerprint, "已有指纹不能为空");
        return new IdempotencyDecision(
                DecisionType.CONFLICT,
                currentFingerprint,
                null,  // 冲突时无需返回 Payment（会拒绝请求）
                existingFingerprint
        );
    }

    // ==================== Getter 方法 ====================

    /**
     * 获取幂等决策类型
     *
     * @return 决策类型（ALLOW_CREATE | REPLAY | CONFLICT）
     */
    public DecisionType getDecisionType() {
        return decisionType;
    }

    /**
     * 获取当前请求的指纹
     *
     * @return 请求指纹哈希值，不为空
     */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * 获取已存在的 Payment
     *
     * 仅在 REPLAY 决策时非空。
     *
     * @return 已有 Payment 实体，或 null（如果决策不是 REPLAY）
     */
    public Payment getExistingPayment() {
        return existingPayment;
    }

    /**
     * 获取数据库中已有的指纹
     *
     * 仅在 CONFLICT 决策时非空，用于诊断。
     *
     * @return 数据库中的指纹哈希值，或 null（如果决策不是 CONFLICT）
     */
    public String getExistingFingerprint() {
        return existingFingerprint;
    }

    // ==================== 业务判断方法 ====================

    /**
     * 判断是否允许创建新付款
     *
     * @return true 当决策为 ALLOW_CREATE
     */
    public boolean isAllowCreate() {
        return decisionType == DecisionType.ALLOW_CREATE;
    }

    /**
     * 判断是否为重放请求
     *
     * @return true 当决策为 REPLAY
     */
    public boolean isReplay() {
        return decisionType == DecisionType.REPLAY;
    }

    /**
     * 判断是否为冲突请求
     *
     * @return true 当决策为 CONFLICT
     */
    public boolean isConflict() {
        return decisionType == DecisionType.CONFLICT;
    }

    // ==================== Object 方法 ====================

    /**
     * 对象字符串表示
     *
     * 用于日志和诊断，不显示敏感数据（如 Payment 对象），
     * 仅展示决策类型和指纹摘要。
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return "IdempotencyDecision{" +
                "decisionType=" + decisionType +
                ", fingerprint='" + (fingerprint != null ? fingerprint.substring(0, Math.min(8, fingerprint.length())) + "..." : "null") + '\'' +
                ", hasExistingPayment=" + (existingPayment != null) +
                ", existingFingerprint='" + (existingFingerprint != null ? existingFingerprint.substring(0, Math.min(8, existingFingerprint.length())) + "..." : "null") + '\'' +
                '}';
    }

    /**
     * 对象相等性判断
     *
     * 两个 IdempotencyDecision 拥有相同的决策类型、当前指纹和已有指纹时，
     * 认为相等。不比较 Payment 实体的细节（因为决策是基于指纹）。
     *
     * @param o 待比较对象
     * @return true 当两对象逻辑相等
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyDecision)) return false;
        IdempotencyDecision that = (IdempotencyDecision) o;
        return decisionType == that.decisionType &&
                Objects.equals(fingerprint, that.fingerprint) &&
                Objects.equals(existingFingerprint, that.existingFingerprint);
    }

    /**
     * 对象哈希码
     *
     * 基于决策类型和指纹值计算，使该类实例可用于 HashMap、HashSet 等。
     *
     * @return 哈希码值
     */
    @Override
    public int hashCode() {
        return Objects.hash(decisionType, fingerprint, existingFingerprint);
    }
}

