package io.github.chrisshi.mom.iam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 用户 Factory Scope 关系实体；factoryId 只是 MDM 引用，不建立跨 Schema 外键。 */
@Getter
@Setter
@TableName("iam_user_factory_scope")
public class IamUserFactoryScopeEntity extends BaseAuditEntity {
    /** 用户 ID。 */
    @TableField("user_id") private String userId;
    /** MDM Factory 引用 ID。 */
    @TableField("factory_id") private String factoryId;
    /** Scope 状态。 */
    @TableField("status") private IamRecordStatus status;
    /** 可选生效 UTC 时间。 */
    @TableField("valid_from") private Instant validFrom;
    /** 可选失效 UTC 时间。 */
    @TableField("valid_until") private Instant validUntil;
    /** 乐观锁版本号。 */
    @Version @TableField("version") private Long version = 0L;
}
