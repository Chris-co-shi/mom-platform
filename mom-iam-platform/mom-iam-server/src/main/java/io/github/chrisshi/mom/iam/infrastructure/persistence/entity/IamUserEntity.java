package io.github.chrisshi.mom.iam.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.chrisshi.mom.data.entity.BaseEntity;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 统一内部、供应商和客户登录账号的 IAM 持久化实体。
 *
 * <p>仅在 IAM Server 内使用，不作为 API DTO；passwordHash 禁止进入 API、日志或安全审计。</p>
 */
@Getter
@Setter
@TableName("iam_user")
public class IamUserEntity extends BaseEntity {
    /** 全局唯一登录名，逻辑删除后默认仍不可复用。 */
    @TableField("username") private String username;
    /** 密码摘要，只允许认证基础设施读取。 */
    @TableField("password_hash") private String passwordHash;
    /** 用户展示名称。 */
    @TableField("display_name") private String displayName;
    /** 用户类型 INTERNAL、SUPPLIER 或 CUSTOMER。 */
    @TableField("user_type") private UserType userType;
    /** 账号状态 ENABLED 或 DISABLED。 */
    @TableField("status") private IamRecordStatus status;
    /** 连续登录失败计数。 */
    @TableField("failed_login_count") private Integer failedLoginCount;
    /** 临时锁定截止 UTC 时间。 */
    @TableField("locked_until") private Instant lockedUntil;
    /** 是否要求后续认证后修改密码。 */
    @TableField("password_change_required") private Boolean passwordChangeRequired;
    /** 最近一次成功登录 UTC 时间。 */
    @TableField("last_login_at") private Instant lastLoginAt;
}
