package io.github.chrisshi.mom.iam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import io.github.chrisshi.mom.data.entity.BaseAuditEntity;
import io.github.chrisshi.mom.iam.domain.type.ClientChannel;
import io.github.chrisshi.mom.iam.domain.type.UserSessionStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 用户授权 Session 权威记录；主键未来直接作为 JWT sid。 */
@Getter
@Setter
@TableName("iam_user_session")
public class IamUserSessionEntity extends BaseAuditEntity {
    /** 所属用户 ID。 */ @TableField("user_id") private String userId;
    /** 所属 Client ID。 */ @TableField("client_id") private String clientId;
    /** WEB 或 MOBILE 渠道。 */ @TableField("channel") private ClientChannel channel;
    /** Session 状态。 */ @TableField("status") private UserSessionStatus status;
    /** 登录建立 UTC 时间。 */ @TableField("login_at") private Instant loginAt;
    /** 最近刷新 UTC 时间。 */ @TableField("last_refresh_at") private Instant lastRefreshAt;
    /** 绝对过期 UTC 时间。 */ @TableField("absolute_expires_at") private Instant absoluteExpiresAt;
    /** 最近 Access Token 过期 UTC 时间。 */ @TableField("latest_access_token_expires_at") private Instant latestAccessTokenExpiresAt;
    /** 来源 IP。 */ @TableField("ip_address") private String ipAddress;
    /** User-Agent 摘要。 */ @TableField("user_agent") private String userAgent;
    /** 可选设备名称。 */ @TableField("device_name") private String deviceName;
    /** 撤销 UTC 时间。 */ @TableField("revoked_at") private Instant revokedAt;
    /** 撤销用户 ID 或 SYSTEM Actor Code。 */ @TableField("revoked_by") private String revokedBy;
    /** 受控撤销原因。 */ @TableField("revoke_reason") private String revokeReason;
    /** 乐观锁版本号。 */ @Version @TableField("version") private Long version = 0L;
}
