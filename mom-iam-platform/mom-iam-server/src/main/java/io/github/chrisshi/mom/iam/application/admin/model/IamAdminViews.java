package io.github.chrisshi.mom.iam.application.admin.model;

import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;

import java.time.Instant;
import java.util.Set;

/**
 * IAM 管理端稳定查询模型集合。
 *
 * <p>这些模型位于应用边界，不依赖 MyBatis、JDBC 或数据库结果集。Controller 可以直接序列化它们，
 * 因而持久化实现替换不会改变既有 JSON 字段；所有投影均刻意排除密码摘要、Token、授权码、Client
 * Secret 与密钥材料。</p>
 */
public final class IamAdminViews {

    private IamAdminViews() {
    }

    /** 不包含密码摘要的用户管理投影。 */
    public record UserView(
            String id,
            String username,
            String displayName,
            UserType userType,
            IamRecordStatus status,
            int failedLoginCount,
            Instant lockedUntil,
            boolean passwordChangeRequired,
            Instant lastLoginAt,
            long version) {
    }

    /** 角色目录管理投影。 */
    public record RoleView(
            String id,
            String code,
            String name,
            UserType applicableUserType,
            IamRecordStatus status,
            boolean builtIn,
            String description,
            long version) {
    }

    /** Permission 目录只读投影。 */
    public record PermissionView(
            String id,
            String code,
            String name,
            String domainCode,
            String resourceCode,
            String actionCode,
            String riskLevel,
            IamRecordStatus status,
            String description) {
    }

    /** 外部用户当前 Party Binding 的非敏感投影。 */
    public record PartyBindingView(
            String id,
            PartyType partyType,
            String partyId,
            IamRecordStatus status,
            long version) {
    }

    /** 管理端 Session 投影，不包含 Refresh Token 或摘要。 */
    public record SessionView(
            String id,
            String userId,
            String clientId,
            String channel,
            String status,
            Instant loginAt,
            Instant lastRefreshAt,
            Instant absoluteExpiresAt,
            Instant latestAccessTokenExpiresAt,
            String deviceName,
            Instant revokedAt,
            String revokeReason) {
    }

    /** MOM Client Policy 与官方 Registered Client 的只读联合投影。 */
    public record ClientView(
            String clientId,
            String applicationCode,
            String channel,
            String allowedUserType,
            boolean mobileAccessRequired,
            IamRecordStatus status,
            String description,
            long version,
            String clientName,
            String redirectUris,
            String postLogoutRedirectUris,
            String scopes) {
    }

    /** 追加型安全审计的非敏感查询投影。 */
    public record SecurityAuditView(
            String id,
            String eventType,
            String eventCategory,
            String riskLevel,
            String result,
            String actorType,
            String actorUserId,
            String actorClientId,
            String targetType,
            String targetId,
            String sessionId,
            String reasonCode,
            String reasonDetail,
            String changeSummary,
            String correlationId,
            Instant occurredAt) {
    }

    /**
     * 用户授权聚合的完整管理快照。
     *
     * @param userVersion 后续全量替换命令唯一允许使用的用户聚合并发版本
     */
    public record UserAuthorizationView(
            String userId,
            long userVersion,
            Set<String> roleIds,
            Set<String> factoryIds,
            boolean mobileAccessEnabled,
            PartyBindingView partyBinding) {
        public UserAuthorizationView {
            roleIds = Set.copyOf(roleIds);
            factoryIds = Set.copyOf(factoryIds);
        }
    }

    /**
     * 角色 Permission 聚合的完整管理快照。
     *
     * @param roleVersion 后续全量替换命令唯一允许使用的角色聚合并发版本
     */
    public record RolePermissionView(
            String roleId,
            long roleVersion,
            Set<String> permissionIds) {
        public RolePermissionView {
            permissionIds = Set.copyOf(permissionIds);
        }
    }
}
