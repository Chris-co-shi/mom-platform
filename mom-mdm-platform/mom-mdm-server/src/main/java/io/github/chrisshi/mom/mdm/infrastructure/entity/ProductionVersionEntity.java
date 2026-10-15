package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** 已发布制造定义组合及工厂业务日期有效期；技术锁版本不充当业务版本。 */
@Getter
@Setter
@TableName("mdm_production_version")
public class ProductionVersionEntity extends BaseAuditEntity {
    private String code;
    private String name;
    private String businessVersion;
    private String plantId;
    private String outputMaterialId;
    private String formulaRevisionId;
    private String routingRevisionId;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String status;
    @Version
    @TableField("lock_version")
    private Long lockVersion = 0L;
    private Instant releasedAt;
    private Instant obsoleteAt;
}
