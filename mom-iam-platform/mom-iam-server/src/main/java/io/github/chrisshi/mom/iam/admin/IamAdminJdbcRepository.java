package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** S07 IAM 管理 API 的事务 JDBC 仓储；不返回密码摘要或任何 Token 材料。 */
public final class IamAdminJdbcRepository {
    private final JdbcTemplate jdbc;

    public IamAdminJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UserRow> listUsers(String userType, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,username,display_name,user_type,status,failed_login_count,locked_until,
                       password_change_required,last_login_at,version
                  FROM iam_user
                 WHERE deleted=false
                """);
        List<Object> arguments = new ArrayList<>();
        if (userType != null && !userType.isBlank()) {
            sql.append(" AND user_type=?");
            arguments.add(userType.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=?");
            arguments.add(status.trim());
        }
        sql.append(" ORDER BY username LIMIT ? OFFSET ?");
        arguments.add(limit);
        arguments.add(offset);
        return jdbc.query(sql.toString(), (rs, row) -> new UserRow(
                rs.getString("id"), rs.getString("username"), rs.getString("display_name"),
                UserType.valueOf(rs.getString("user_type")),
                IamRecordStatus.valueOf(rs.getString("status")),
                rs.getInt("failed_login_count"), instant(rs.getTimestamp("locked_until")),
                rs.getBoolean("password_change_required"), instant(rs.getTimestamp("last_login_at")),
                rs.getLong("version")), arguments.toArray());
    }

    public Optional<UserRow> findUser(String userId) {
        return queryUser("WHERE id=? AND deleted=false", userId).stream().findFirst();
    }

    public Optional<UserRow> lockUser(String userId) {
        return queryUser("WHERE id=? AND deleted=false FOR UPDATE", userId).stream().findFirst();
    }

    private List<UserRow> queryUser(String where, String userId) {
        return jdbc.query("""
                SELECT id,username,display_name,user_type,status,failed_login_count,locked_until,
                       password_change_required,last_login_at,version
                  FROM iam_user
                """ + where, (rs, row) -> new UserRow(
                rs.getString("id"), rs.getString("username"), rs.getString("display_name"),
                UserType.valueOf(rs.getString("user_type")),
                IamRecordStatus.valueOf(rs.getString("status")),
                rs.getInt("failed_login_count"), instant(rs.getTimestamp("locked_until")),
                rs.getBoolean("password_change_required"), instant(rs.getTimestamp("last_login_at")),
                rs.getLong("version")), userId);
    }

    public void insertUser(
            String id, String username, String passwordHash, String displayName,
            UserType userType, String actor, Instant now) {
        int rows = jdbc.update("""
                INSERT INTO iam_user (
                    id,username,password_hash,display_name,user_type,status,failed_login_count,
                    locked_until,password_change_required,last_login_at,
                    created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES (?,?,?,?,?,'ENABLED',0,NULL,true,NULL,?,?,?,?,0,false)
                """, id, username, passwordHash, displayName, userType.name(),
                timestamp(now), actor, timestamp(now), actor);
        requireOne(rows, "用户创建失败");
    }

    public void updateDisplayName(String userId, String displayName, long version, String actor, Instant now) {
        requireOne(jdbc.update("""
                UPDATE iam_user
                   SET display_name=?,updated_at=?,updated_by=?,version=version+1
                 WHERE id=? AND deleted=false AND version=?
                """, displayName, timestamp(now), actor, userId, version), "用户已被并发修改");
    }

    public void updateUserStatus(String userId, IamRecordStatus status, long version, String actor, Instant now) {
        requireOne(jdbc.update("""
                UPDATE iam_user
                   SET status=?,updated_at=?,updated_by=?,version=version+1
                 WHERE id=? AND deleted=false AND version=?
                """, status.name(), timestamp(now), actor, userId, version), "用户状态已被并发修改");
    }

    public void unlockUser(String userId, long version, String actor, Instant now) {
        requireOne(jdbc.update("""
                UPDATE iam_user
                   SET failed_login_count=0,locked_until=NULL,updated_at=?,updated_by=?,version=version+1
                 WHERE id=? AND deleted=false AND version=?
                """, timestamp(now), actor, userId, version), "用户锁定状态已被并发修改");
    }

    public void resetPassword(
            String userId, String passwordHash, long version, String actor, Instant now) {
        requireOne(jdbc.update("""
                UPDATE iam_user
                   SET password_hash=?,password_change_required=true,failed_login_count=0,
                       locked_until=NULL,updated_at=?,updated_by=?,version=version+1
                 WHERE id=? AND deleted=false AND version=?
                """, passwordHash, timestamp(now), actor, userId, version), "用户密码状态已被并发修改");
    }

    public void logicalDeleteUser(String userId, long version, String actor, Instant now) {
        requireOne(jdbc.update("""
                UPDATE iam_user
                   SET status='DISABLED',deleted=true,updated_at=?,updated_by=?,version=version+1
                 WHERE id=? AND deleted=false AND version=?
                """, timestamp(now), actor, userId, version), "用户已被并发修改");
    }

    public List<RoleRow> listRoles(String userType, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,code,name,applicable_user_type,status,built_in,description,version
                  FROM iam_role WHERE deleted=false
                """);
        List<Object> args = new ArrayList<>();
        if (userType != null && !userType.isBlank()) {
            sql.append(" AND applicable_user_type=?");
            args.add(userType.trim());
        }
        sql.append(" ORDER BY code LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), (rs, row) -> roleRow(rs), args.toArray());
    }

    public Optional<RoleRow> lockRole(String roleId) {
        return jdbc.query("""
                SELECT id,code,name,applicable_user_type,status,built_in,description,version
                  FROM iam_role WHERE id=? AND deleted=false FOR UPDATE
                """, (rs, row) -> roleRow(rs), roleId).stream().findFirst();
    }

    public List<RoleRow> findRoles(Collection<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return List.of();
        String placeholders = placeholders(roleIds.size());
        return jdbc.query("""
                SELECT id,code,name,applicable_user_type,status,built_in,description,version
                  FROM iam_role WHERE deleted=false AND id IN (
                """ + placeholders + ") ORDER BY code", (rs, row) -> roleRow(rs), roleIds.toArray());
    }

    public void insertRole(
            String id, String code, String name, UserType type, String description,
            String actor, Instant now) {
        requireOne(jdbc.update("""
                INSERT INTO iam_role (
                    id,code,name,applicable_user_type,status,built_in,description,
                    created_at,created_by,updated_at,updated_by,version,deleted)
                VALUES (?,?,?,?,'ENABLED',false,?,?,?,?,?,0,false)
                """, id, code, name, type.name(), description,
                timestamp(now), actor, timestamp(now), actor), "角色创建失败");
    }

    public void updateRole(
            String roleId, String name, String description, IamRecordStatus status,
            long version, String actor, Instant now) {
        requireOne(jdbc.update("""
                UPDATE iam_role
                   SET name=?,description=?,status=?,updated_at=?,updated_by=?,version=version+1
                 WHERE id=? AND deleted=false AND version=?
                """, name, description, status.name(), timestamp(now), actor, roleId, version),
                "角色已被并发修改");
    }

    public List<PermissionRow> listPermissions(String domainCode, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,code,name,domain_code,resource_code,action_code,risk_level,status,description
                  FROM iam_permission WHERE deleted=false
                """);
        List<Object> args = new ArrayList<>();
        if (domainCode != null && !domainCode.isBlank()) {
            sql.append(" AND domain_code=?");
            args.add(domainCode.trim());
        }
        sql.append(" ORDER BY code LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), (rs, row) -> new PermissionRow(
                rs.getString("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("domain_code"), rs.getString("resource_code"),
                rs.getString("action_code"), rs.getString("risk_level"),
                IamRecordStatus.valueOf(rs.getString("status")), rs.getString("description")),
                args.toArray());
    }

    public List<String> findPermissionIds(Collection<String> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) return List.of();
        return jdbc.queryForList("SELECT id FROM iam_permission WHERE deleted=false AND status='ENABLED' AND id IN ("
                + placeholders(permissionIds.size()) + ")", String.class, permissionIds.toArray());
    }

    public Set<String> userRoleIds(String userId) {
        return Set.copyOf(jdbc.queryForList(
                "SELECT role_id FROM iam_user_role WHERE user_id=? AND status='ENABLED' ORDER BY role_id",
                String.class, userId));
    }

    public void replaceUserRoles(
            String userId, Collection<String> roleIds, String actor, Instant now, IdSupplier ids) {
        jdbc.update("DELETE FROM iam_user_role WHERE user_id=?", userId);
        for (String roleId : roleIds) {
            jdbc.update("""
                    INSERT INTO iam_user_role (
                        id,user_id,role_id,status,valid_from,valid_until,
                        created_at,created_by,updated_at,updated_by,version)
                    VALUES (?,?,?,'ENABLED',NULL,NULL,?,?,?,?,0)
                    """, ids.nextId(), userId, roleId, timestamp(now), actor, timestamp(now), actor);
        }
    }

    public Set<String> rolePermissionIds(String roleId) {
        return Set.copyOf(jdbc.queryForList(
                "SELECT permission_id FROM iam_role_permission WHERE role_id=? ORDER BY permission_id",
                String.class, roleId));
    }

    public void replaceRolePermissions(
            String roleId, Collection<String> permissionIds, String actor, Instant now, IdSupplier ids) {
        jdbc.update("DELETE FROM iam_role_permission WHERE role_id=?", roleId);
        for (String permissionId : permissionIds) {
            jdbc.update("""
                    INSERT INTO iam_role_permission (id,role_id,permission_id,created_at,created_by)
                    VALUES (?,?,?,?,?)
                    """, ids.nextId(), roleId, permissionId, timestamp(now), actor);
        }
    }

    public boolean userHasEffectivePlatformAdmin(String userId, Instant now) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                  FROM iam_user_role ur
                  JOIN iam_role r ON r.id=ur.role_id
                  JOIN iam_user u ON u.id=ur.user_id
                 WHERE ur.user_id=? AND r.code='PLATFORM_ADMIN'
                   AND u.deleted=false AND u.status='ENABLED'
                   AND r.deleted=false AND r.status='ENABLED'
                   AND ur.status='ENABLED'
                   AND (ur.valid_from IS NULL OR ur.valid_from<=?)
                   AND (ur.valid_until IS NULL OR ur.valid_until>?)
                """, Integer.class, userId, timestamp(now), timestamp(now));
        return count != null && count > 0;
    }

    public int effectivePlatformAdminCount(Instant now) {
        Integer count = jdbc.queryForObject("""
                SELECT count(DISTINCT ur.user_id)
                  FROM iam_user_role ur
                  JOIN iam_role r ON r.id=ur.role_id
                  JOIN iam_user u ON u.id=ur.user_id
                 WHERE r.code='PLATFORM_ADMIN'
                   AND u.deleted=false AND u.status='ENABLED'
                   AND r.deleted=false AND r.status='ENABLED'
                   AND ur.status='ENABLED'
                   AND (ur.valid_from IS NULL OR ur.valid_from<=?)
                   AND (ur.valid_until IS NULL OR ur.valid_until>?)
                """, Integer.class, timestamp(now), timestamp(now));
        return count == null ? 0 : count;
    }

    public Set<String> factoryIds(String userId) {
        return Set.copyOf(jdbc.queryForList(
                "SELECT factory_id FROM iam_user_factory_scope WHERE user_id=? AND status='ENABLED' ORDER BY factory_id",
                String.class, userId));
    }

    public void replaceFactoryScopes(
            String userId, Collection<String> factoryIds, String actor, Instant now, IdSupplier ids) {
        jdbc.update("DELETE FROM iam_user_factory_scope WHERE user_id=?", userId);
        for (String factoryId : factoryIds) {
            jdbc.update("""
                    INSERT INTO iam_user_factory_scope (
                        id,user_id,factory_id,status,valid_from,valid_until,
                        created_at,created_by,updated_at,updated_by,version)
                    VALUES (?,?,?,'ENABLED',NULL,NULL,?,?,?,?,0)
                    """, ids.nextId(), userId, factoryId, timestamp(now), actor, timestamp(now), actor);
        }
    }

    public boolean mobileAccessEnabled(String userId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM iam_user_application
                 WHERE user_id=? AND application_code='MOM_MOBILE_PDA' AND status='ENABLED'
                   AND (valid_from IS NULL OR valid_from<=now())
                   AND (valid_until IS NULL OR valid_until>now())
                """, Integer.class, userId);
        return count != null && count > 0;
    }

    public void setMobileAccess(
            String userId, boolean enabled, String actor, Instant now, IdSupplier ids) {
        int updated = jdbc.update("""
                UPDATE iam_user_application
                   SET status=?,valid_from=NULL,valid_until=NULL,updated_at=?,updated_by=?,version=version+1
                 WHERE user_id=? AND application_code='MOM_MOBILE_PDA'
                """, enabled ? "ENABLED" : "DISABLED", timestamp(now), actor, userId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO iam_user_application (
                        id,user_id,application_code,status,valid_from,valid_until,
                        created_at,created_by,updated_at,updated_by,version)
                    VALUES (?,?,'MOM_MOBILE_PDA',?,NULL,NULL,?,?,?,?,0)
                    """, ids.nextId(), userId, enabled ? "ENABLED" : "DISABLED",
                    timestamp(now), actor, timestamp(now), actor);
        }
    }

    public Optional<PartyBindingRow> partyBinding(String userId) {
        return jdbc.query("""
                SELECT id,party_type,party_id,status,version
                  FROM iam_external_user_binding WHERE user_id=?
                """, (rs, row) -> new PartyBindingRow(
                rs.getString("id"), PartyType.valueOf(rs.getString("party_type")),
                rs.getString("party_id"), IamRecordStatus.valueOf(rs.getString("status")),
                rs.getLong("version")), userId).stream().findFirst();
    }

    public void rebindParty(
            String userId, PartyType partyType, String partyId, String actor,
            Instant now, IdSupplier ids) {
        int updated = jdbc.update("""
                UPDATE iam_external_user_binding
                   SET party_type=?,party_id=?,status='ENABLED',valid_from=?,valid_until=NULL,
                       updated_at=?,updated_by=?,version=version+1
                 WHERE user_id=?
                """, partyType.name(), partyId, timestamp(now), timestamp(now), actor, userId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO iam_external_user_binding (
                        id,user_id,party_type,party_id,status,valid_from,valid_until,
                        created_at,created_by,updated_at,updated_by,version)
                    VALUES (?,?,?,?,'ENABLED',?,NULL,?,?,?,?,0)
                    """, ids.nextId(), userId, partyType.name(), partyId, timestamp(now),
                    timestamp(now), actor, timestamp(now), actor);
        }
    }

    public List<SessionRow> listSessions(String userId, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,user_id,client_id,channel,status,login_at,last_refresh_at,
                       absolute_expires_at,latest_access_token_expires_at,device_name,revoked_at,revoke_reason
                  FROM iam_user_session WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (userId != null && !userId.isBlank()) {
            sql.append(" AND user_id=?");
            args.add(userId.trim());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=?");
            args.add(status.trim());
        }
        sql.append(" ORDER BY login_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), (rs, row) -> new SessionRow(
                rs.getString("id"), rs.getString("user_id"), rs.getString("client_id"),
                rs.getString("channel"), rs.getString("status"),
                rs.getTimestamp("login_at").toInstant(), instant(rs.getTimestamp("last_refresh_at")),
                rs.getTimestamp("absolute_expires_at").toInstant(),
                instant(rs.getTimestamp("latest_access_token_expires_at")),
                rs.getString("device_name"), instant(rs.getTimestamp("revoked_at")),
                rs.getString("revoke_reason")), args.toArray());
    }

    public List<String> activeSessionIdsForUser(String userId) {
        return jdbc.queryForList(
                "SELECT id FROM iam_user_session WHERE user_id=? AND status='ACTIVE' ORDER BY id",
                String.class, userId);
    }

    public List<String> activeSessionIdsForClient(String clientId) {
        return jdbc.queryForList(
                "SELECT id FROM iam_user_session WHERE client_id=? AND status='ACTIVE' ORDER BY id",
                String.class, clientId);
    }

    public List<ClientRow> listClients() {
        return jdbc.query("""
                SELECT p.client_id,p.application_code,p.channel,p.allowed_user_type,
                       p.mobile_access_required,p.status,p.description,p.version,
                       r.client_name,r.redirect_uris,r.post_logout_redirect_uris,r.scopes
                  FROM iam_oauth_client_policy p
                  LEFT JOIN oauth2_registered_client r ON r.client_id=p.client_id
                 ORDER BY p.client_id
                """, (rs, row) -> new ClientRow(
                rs.getString("client_id"), rs.getString("application_code"), rs.getString("channel"),
                rs.getString("allowed_user_type"), rs.getBoolean("mobile_access_required"),
                IamRecordStatus.valueOf(rs.getString("status")), rs.getString("description"),
                rs.getLong("version"), rs.getString("client_name"), rs.getString("redirect_uris"),
                rs.getString("post_logout_redirect_uris"), rs.getString("scopes")));
    }

    public Optional<ClientRow> lockClient(String clientId) {
        return jdbc.query("""
                SELECT p.client_id,p.application_code,p.channel,p.allowed_user_type,
                       p.mobile_access_required,p.status,p.description,p.version,
                       r.client_name,r.redirect_uris,r.post_logout_redirect_uris,r.scopes
                  FROM iam_oauth_client_policy p
                  LEFT JOIN oauth2_registered_client r ON r.client_id=p.client_id
                 WHERE p.client_id=? FOR UPDATE OF p
                """, (rs, row) -> new ClientRow(
                rs.getString("client_id"), rs.getString("application_code"), rs.getString("channel"),
                rs.getString("allowed_user_type"), rs.getBoolean("mobile_access_required"),
                IamRecordStatus.valueOf(rs.getString("status")), rs.getString("description"),
                rs.getLong("version"), rs.getString("client_name"), rs.getString("redirect_uris"),
                rs.getString("post_logout_redirect_uris"), rs.getString("scopes")), clientId)
                .stream().findFirst();
    }

    public void updateClientStatus(
            String clientId, IamRecordStatus status, long version, String actor, Instant now) {
        requireOne(jdbc.update("""
                UPDATE iam_oauth_client_policy
                   SET status=?,updated_at=?,updated_by=?,version=version+1
                 WHERE client_id=? AND version=?
                """, status.name(), timestamp(now), actor, clientId, version),
                "Client Policy 已被并发修改");
    }

    public List<AuditRow> listAudit(String category, String targetId, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT id,event_type,event_category,risk_level,result,actor_type,actor_user_id,
                       actor_client_id,target_type,target_id,session_id,reason_code,reason_detail,
                       change_summary::text,correlation_id,occurred_at
                  FROM iam_security_audit_event WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            sql.append(" AND event_category=?");
            args.add(category.trim());
        }
        if (targetId != null && !targetId.isBlank()) {
            sql.append(" AND target_id=?");
            args.add(targetId.trim());
        }
        sql.append(" ORDER BY occurred_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), (rs, row) -> new AuditRow(
                rs.getString("id"), rs.getString("event_type"), rs.getString("event_category"),
                rs.getString("risk_level"), rs.getString("result"), rs.getString("actor_type"),
                rs.getString("actor_user_id"), rs.getString("actor_client_id"),
                rs.getString("target_type"), rs.getString("target_id"), rs.getString("session_id"),
                rs.getString("reason_code"), rs.getString("reason_detail"),
                rs.getString("change_summary"), rs.getString("correlation_id"),
                rs.getTimestamp("occurred_at").toInstant()), args.toArray());
    }

    private static RoleRow roleRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RoleRow(
                rs.getString("id"), rs.getString("code"), rs.getString("name"),
                UserType.valueOf(rs.getString("applicable_user_type")),
                IamRecordStatus.valueOf(rs.getString("status")), rs.getBoolean("built_in"),
                rs.getString("description"), rs.getLong("version"));
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static void requireOne(int rows, String message) {
        if (rows != 1) throw new IllegalStateException(message);
    }

    @FunctionalInterface
    public interface IdSupplier { String nextId(); }

    public record UserRow(
            String id, String username, String displayName, UserType userType,
            IamRecordStatus status, int failedLoginCount, Instant lockedUntil,
            boolean passwordChangeRequired, Instant lastLoginAt, long version) { }

    public record RoleRow(
            String id, String code, String name, UserType applicableUserType,
            IamRecordStatus status, boolean builtIn, String description, long version) { }

    public record PermissionRow(
            String id, String code, String name, String domainCode, String resourceCode,
            String actionCode, String riskLevel, IamRecordStatus status, String description) { }

    public record PartyBindingRow(
            String id, PartyType partyType, String partyId, IamRecordStatus status, long version) { }

    public record SessionRow(
            String id, String userId, String clientId, String channel, String status,
            Instant loginAt, Instant lastRefreshAt, Instant absoluteExpiresAt,
            Instant latestAccessTokenExpiresAt, String deviceName, Instant revokedAt,
            String revokeReason) { }

    public record ClientRow(
            String clientId, String applicationCode, String channel, String allowedUserType,
            boolean mobileAccessRequired, IamRecordStatus status, String description, long version,
            String clientName, String redirectUris, String postLogoutRedirectUris, String scopes) { }

    public record AuditRow(
            String id, String eventType, String eventCategory, String riskLevel, String result,
            String actorType, String actorUserId, String actorClientId, String targetType,
            String targetId, String sessionId, String reasonCode, String reasonDetail,
            String changeSummary, String correlationId, Instant occurredAt) { }
}
