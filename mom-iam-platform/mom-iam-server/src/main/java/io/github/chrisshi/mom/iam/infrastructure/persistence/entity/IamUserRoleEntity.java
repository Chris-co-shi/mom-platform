package io.github.chrisshi.mom.iam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 用户与角色分配关系实体；用户类型匹配由领域规则校验。 */
@Getter
@Setter
@TableName("iam_user_role")
public class IamUserRoleEntity extends BaseAuditEntity {
    /** 被分配角色的用户 ID。 */
    @TableField("user_id") private String userId;
    /** 分配的角色 ID。 */
    @TableField("role_id") private String roleId;
    /** 分配状态。 */
    @TableField("status") private IamRecordStatus status;
    /** 可选生效 UTC 时间。 */
    @TableField("valid_from") private Instant validFrom;
    /** 可选失效 UTC 时间。 */
    @TableField("valid_until") private Instant validUntil;
    /** 乐观锁版本号。 */
    @Version @TableField("version") private Long version = 0L;
}
