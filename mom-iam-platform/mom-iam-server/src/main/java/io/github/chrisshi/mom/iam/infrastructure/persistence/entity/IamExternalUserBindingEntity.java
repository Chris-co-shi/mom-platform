package io.github.chrisshi.mom.iam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 外部账号唯一 Party Binding 持久化实体；不建立到业务 Schema 的外键。 */
@Getter
@Setter
@TableName("iam_external_user_binding")
public class IamExternalUserBindingEntity extends BaseAuditEntity {
    /** 外部用户 ID，一个用户最多一条绑定。 */
    @TableField("user_id") private String userId;
    /** 主体类型 SUPPLIER 或 CUSTOMER。 */
    @TableField("party_type") private PartyType partyType;
    /** 供应商或客户主体引用 ID。 */
    @TableField("party_id") private String partyId;
    /** 绑定状态。 */
    @TableField("status") private IamRecordStatus status;
    /** 可选生效 UTC 时间。 */
    @TableField("valid_from") private Instant validFrom;
    /** 可选失效 UTC 时间。 */
    @TableField("valid_until") private Instant validUntil;
    /** 乐观锁版本号。 */
    @Version @TableField("version") private Long version = 0L;
}
