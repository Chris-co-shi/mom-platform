package io.github.chrisshi.mom.mdm.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** 质量标准业务修订；业务修订号与技术锁版本严格分离。 */
@Getter @Setter @TableName("mdm_specification_revision")
public class SpecificationRevisionEntity extends BaseAuditEntity {
    private String specificationId;
    private Integer revisionNo;
    private String status;
    @Version @TableField("lock_version") private Long lockVersion = 0L;
    private Instant releasedAt;
    private Instant obsoleteAt;
}
