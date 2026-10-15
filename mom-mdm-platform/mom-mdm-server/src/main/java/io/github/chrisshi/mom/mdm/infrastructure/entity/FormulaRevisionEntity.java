package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 配方业务修订与独立技术乐观锁；已发布内容不可再编辑。 */
@Getter
@Setter
@TableName("mdm_formula_revision")
public class FormulaRevisionEntity extends BaseAuditEntity {
    private String formulaId;
    private Integer revisionNo;
    private String status;
    @Version
    @TableField("lock_version")
    private Long lockVersion = 0L;
    private Instant releasedAt;
    private Instant obsoleteAt;
}
