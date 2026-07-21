package io.github.chrisshi.mom.iam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseIdEntity;
import io.github.chrisshi.mom.iam.domain.type.RefreshTokenStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Refresh Token 轮换链安全状态实体。
 *
 * <p>只保存 HMAC-SHA-256 摘要，Server Pepper 不在数据库；不生成包含字段的 toString。</p>
 */
@Getter
@Setter
@TableName("iam_refresh_token")
public class IamRefreshTokenEntity extends BaseIdEntity {
    /** 所属 Session ID。 */ @TableField("session_id") private String sessionId;
    /** Token HMAC-SHA-256 摘要，绝不是明文。 */ @TableField("token_digest") private String tokenDigest;
    /** Session 内轮换序号。 */ @TableField("sequence_no") private Long sequenceNo;
    /** Token 状态。 */ @TableField("status") private RefreshTokenStatus status;
    /** 签发 UTC 时间。 */ @TableField("issued_at") private Instant issuedAt;
    /** 过期 UTC 时间。 */ @TableField("expires_at") private Instant expiresAt;
    /** 消费 UTC 时间。 */ @TableField("consumed_at") private Instant consumedAt;
    /** 后继 Token ID。 */ @TableField("replaced_by_token_id") private String replacedByTokenId;
    /** 撤销 UTC 时间。 */ @TableField("revoked_at") private Instant revokedAt;
    /** 记录持久化 UTC 时间。 */ @TableField("created_at") private Instant createdAt;
}
