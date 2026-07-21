package io.github.chrisshi.mom.iam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.iam.domain.type.ApplicationCode;
import io.github.chrisshi.mom.iam.domain.type.ClientChannel;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import lombok.Getter;
import lombok.Setter;

/** MOM 对 OAuth Client 的应用级安全策略；不重复保存 Redirect URI 或 TokenSettings。 */
@Getter
@Setter
@TableName("iam_oauth_client_policy")
public class IamOauthClientPolicyEntity extends BaseAuditEntity {
    /** OAuth Client ID。 */
    @TableField("client_id") private String clientId;
    /** MOM 应用编码。 */
    @TableField("application_code") private ApplicationCode applicationCode;
    /** 客户端渠道。 */
    @TableField("channel") private ClientChannel channel;
    /** 允许登录的用户类型。 */
    @TableField("allowed_user_type") private UserType allowedUserType;
    /** 是否要求独立 Mobile Access。 */
    @TableField("mobile_access_required") private Boolean mobileAccessRequired;
    /** Client Policy 状态。 */
    @TableField("status") private IamRecordStatus status;
    /** 访问矩阵与边界说明。 */
    @TableField("description") private String description;
    /** 乐观锁版本号。 */
    @Version @TableField("version") private Long version = 0L;
}
