package io.github.chrisshi.mom.mdm.application.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * UOM Slice 的不可变 Application 视图集合。
 *
 * <p>这些记录隔离 Infrastructure Entity 与 HTTP Result，不包含 MyBatis 类型或逻辑删除字段。记录本身
 * 线程安全且无副作用；换算结果显式携带规则身份，便于调用方持久化业务事实快照。</p>
 */
public final class UomMasterDataViews {
    private UomMasterDataViews() {
    }

    /**
     * 固定七维 SI 指数向量的量纲视图。
     *
     * @param id 技术主键
     * @param code 不可普通修改的业务编码
     * @param name 业务名称
     * @param timeExponent 时间指数 T
     * @param lengthExponent 长度指数 L
     * @param massExponent 质量指数 M
     * @param electricCurrentExponent 电流指数 I
     * @param temperatureExponent 温度指数 Theta
     * @param amountExponent 物质的量指数 N
     * @param luminousIntensityExponent 发光强度指数 J
     * @param status 生命周期状态
     * @param createdAt 创建时间
     * @param createdBy 创建 Actor
     * @param updatedAt 最近修改时间
     * @param updatedBy 最近修改 Actor
     * @param version 乐观锁版本
     */
    public record DimensionView(String id, String code, String name,
                                Integer timeExponent, Integer lengthExponent, Integer massExponent,
                                Integer electricCurrentExponent, Integer temperatureExponent,
                                Integer amountExponent, Integer luminousIntensityExponent,
                                String status, Instant createdAt, String createdBy,
                                Instant updatedAt, String updatedBy, Long version) {
    }

    /**
     * 业务计量单位类别视图；referenceUomId 指向类别唯一基准单位。
     *
     * @param id 技术主键
     * @param code 唯一业务编码
     * @param name 业务名称
     * @param dimensionId 所属量纲 ID
     * @param referenceUomId 唯一基准单位 ID
     * @param status 生命周期状态
     * @param createdAt 创建时间
     * @param createdBy 创建 Actor
     * @param updatedAt 最近修改时间
     * @param updatedBy 最近修改 Actor
     * @param version 乐观锁版本
     */
    public record CategoryView(String id, String code, String name,
                               String dimensionId, String referenceUomId, String status,
                               Instant createdAt, String createdBy, Instant updatedAt,
                               String updatedBy, Long version) {
    }

    /**
     * 权威计量单位目录视图。
     *
     * @param id 技术主键
     * @param code 优先采用 UCUM 的权威编码
     * @param name 业务名称
     * @param symbol 显示符号
     * @param categoryId 唯一所属类别 ID
     * @param referenceUnit 是否类别基准单位
     * @param ucumNotApplicableReason 企业扩展单位的治理说明
     * @param status 生命周期状态
     * @param createdAt 创建时间
     * @param createdBy 创建 Actor
     * @param updatedAt 最近修改时间
     * @param updatedBy 最近修改 Actor
     * @param version 乐观锁版本
     */
    public record UomView(String id, String code, String name, String symbol,
                          String categoryId, Boolean referenceUnit, String ucumNotApplicableReason,
                          String status, Instant createdAt, String createdBy, Instant updatedAt,
                          String updatedBy, Long version) {
    }

    /**
     * 不可变换算规则版本视图。
     *
     * @param id 规则技术主键
     * @param uomId 非基准单位 ID
     * @param versionNo 单位内递增业务版本
     * @param algorithmType 受控算法类型，V1 为 AFFINE
     * @param multiplier 到基准单位的正乘数
     * @param offset 乘法后的偏移量
     * @param calculationPrecision 计算有效数字精度
     * @param roundingMode Java BigDecimal 舍入模式
     * @param status ENABLED 当前版或 DISABLED 历史版
     * @param createdAt 创建时间
     * @param createdBy 创建 Actor
     * @param updatedAt 最近状态修改时间
     * @param updatedBy 最近状态修改 Actor
     * @param lockVersion 状态切换乐观锁版本
     */
    public record RuleView(String id, String uomId, Integer versionNo, String algorithmType,
                           BigDecimal multiplier, BigDecimal offset, Integer calculationPrecision,
                           String roundingMode, String status, Instant createdAt, String createdBy,
                           Instant updatedAt, String updatedBy, Long lockVersion) {
    }

    /**
     * 换算结果；基准单位一侧没有规则，因此对应规则 ID 与版本允许为 null。
     *
     * @param sourceValue 调用方输入的规范十进制字符串
     * @param sourceUomId 源单位技术主键
     * @param targetValue 换算结果十进制字符串
     * @param targetUomId 目标单位技术主键
     * @param sourceRuleId 实际使用的源规则 ID
     * @param sourceRuleVersion 实际使用的源规则版本
     * @param targetRuleId 实际使用的目标规则 ID
     * @param targetRuleVersion 实际使用的目标规则版本
     */
    public record ConversionView(String sourceValue, String sourceUomId, String targetValue,
                                 String targetUomId, String sourceRuleId, Integer sourceRuleVersion,
                                 String targetRuleId, Integer targetRuleVersion) {
    }

    /**
     * 单位是否属于同一业务换算类别的识别结果。
     *
     * @param sourceUomId 源单位技术主键
     * @param targetUomId 目标单位技术主键
     * @param compatible 是否允许通过共同基准单位换算
     * @param categoryCode 兼容时的类别编码
     * @param dimensionCode 兼容时的量纲编码
     */
    public record CompatibilityView(String sourceUomId, String targetUomId, boolean compatible,
                                    String categoryCode, String dimensionCode) {
    }
}
