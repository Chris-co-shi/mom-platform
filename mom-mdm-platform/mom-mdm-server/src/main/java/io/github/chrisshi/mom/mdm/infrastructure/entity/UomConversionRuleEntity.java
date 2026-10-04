package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 非基准单位到基准单位的不可变换算规则版本实体。
 *
 * <p>该 Infrastructure 类型不使用逻辑删除：历史版本永久保留，仅允许把当前版本停用后新增版本。
 * lockVersion 只保护版本切换竞争，不改变业务版本号；数据库故障或竞争失败时事务整体回滚。</p>
 */
@Getter
@Setter
@TableName("mdm_uom_conversion_rule")
public class UomConversionRuleEntity extends BaseAuditEntity {
    private String uomId;
    private Integer versionNo;
    private String algorithmType;
    private BigDecimal multiplier;
    @TableField("offset_value")
    private BigDecimal offset;
    private Integer calculationPrecision;
    private String roundingMode;
    private String status;
    @Version
    @TableField("lock_version")
    private Long lockVersion;
}
