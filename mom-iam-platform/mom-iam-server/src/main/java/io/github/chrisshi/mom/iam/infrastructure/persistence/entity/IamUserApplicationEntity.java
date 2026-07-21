package io.github.chrisshi.mom.iam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.iam.domain.type.ApplicationCode;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 用户级应用访问能力实体；P1.5 只用于 INTERNAL 用户的 MOM_MOBILE_PDA。 */
@Getter
@Setter
@TableName("iam_user_application")
public class IamUserApplicationEntity extends BaseAuditEntity {
    /** 获得访问能力的用户 ID。 */
    @TableField("user_id") private String userId;
    /** 应用编码。 */
    @TableField("application_code") private ApplicationCode applicationCode;
    /** 授权状态。 */
    @TableField("status") private IamRecordStatus status;
    /** 可选生效 UTC 时间。 */
    @TableField("valid_from") private Instant validFrom;
    /** 可选失效 UTC 时间。 */
    @TableField("valid_until") private Instant validUntil;
    /** 乐观锁版本号。 */
    @Version @TableField("version") private Long version = 0L;
}
