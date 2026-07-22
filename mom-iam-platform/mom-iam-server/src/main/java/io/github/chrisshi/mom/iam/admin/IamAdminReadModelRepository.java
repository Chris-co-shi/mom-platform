package io.github.chrisshi.mom.iam.admin;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * S07 管理契约加固使用的授权聚合只读仓储。
 *
 * <p>该仓储只读取 {@code iam_user.version}、{@code iam_role.version} 及其授权关系，供管理端在提交
 * 全量替换命令前取得乐观并发版本。查询结果刻意不选择密码摘要、Token、授权码、私钥或 Session
 * 凭证；读取不加锁，也不承担写事务边界。</p>
 */
public final class IamAdminReadModelRepository {
    private final JdbcTemplate jdbc;

    public IamAdminReadModelRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 读取用户授权聚合的当前版本和全部可管理关系。
     *
     * @param userId 用户聚合 ID
     * @return 不含凭证材料的用户授权快照
     * @throws IamAdminExceptions.NotFound 用户不存在或已逻辑删除
     */
    public UserAuthorizationView userAuthorization(String userId) {
        Long userVersion = jdbc.query(
                "SELECT version FROM iam_user WHERE id=? AND deleted=false",
                (rs, row) -> rs.getLong("version"), userId).stream().findFirst()
                .orElseThrow(() -> new IamAdminExceptions.NotFound("用户不存在"));
        Set<String> roleIds = orderedSet(jdbc.queryForList("""
                SELECT role_id FROM iam_user_role
                 WHERE user_id=? AND status='ENABLED'
                 ORDER BY role_id
                """, String.class, userId));
        Set<String> factoryIds = orderedSet(jdbc.queryForList("""
                SELECT factory_id FROM iam_user_factory_scope
                 WHERE user_id=? AND status='ENABLED'
                 ORDER BY factory_id
                """, String.class, userId));
        Integer mobileCount = jdbc.queryForObject("""
                SELECT count(*) FROM iam_user_application
                 WHERE user_id=? AND application_code='MOM_MOBILE_PDA' AND status='ENABLED'
                   AND (valid_from IS NULL OR valid_from<=now())
                   AND (valid_until IS NULL OR valid_until>now())
                """, Integer.class, userId);
        PartyBindingView partyBinding = jdbc.query("""
                SELECT id,party_type,party_id,status,version
                  FROM iam_external_user_binding
                 WHERE user_id=?
                """, (rs, row) -> new PartyBindingView(
                rs.getString("id"), rs.getString("party_type"), rs.getString("party_id"),
                rs.getString("status"), rs.getLong("version")), userId)
                .stream().findFirst().orElse(null);
        return new UserAuthorizationView(
                userId, userVersion, roleIds, factoryIds,
                mobileCount != null && mobileCount > 0, partyBinding);
    }

    /**
     * 读取角色聚合的当前版本和有效 Permission 关系。
     *
     * @param roleId 角色聚合 ID
     * @return 不含凭证材料的角色 Permission 快照
     * @throws IamAdminExceptions.NotFound 角色不存在或已逻辑删除
     */
    public RolePermissionView rolePermissions(String roleId) {
        Long roleVersion = jdbc.query(
                "SELECT version FROM iam_role WHERE id=? AND deleted=false",
                (rs, row) -> rs.getLong("version"), roleId).stream().findFirst()
                .orElseThrow(() -> new IamAdminExceptions.NotFound("角色不存在"));
        Set<String> permissionIds = orderedSet(jdbc.queryForList("""
                SELECT rp.permission_id
                  FROM iam_role_permission rp
                  JOIN iam_permission p ON p.id=rp.permission_id
                 WHERE rp.role_id=? AND p.deleted=false AND p.status='ENABLED'
                 ORDER BY rp.permission_id
                """, String.class, roleId));
        return new RolePermissionView(roleId, roleVersion, permissionIds);
    }

    private static Set<String> orderedSet(List<String> values) {
        return Set.copyOf(new LinkedHashSet<>(values));
    }

    /**
     * 用户授权聚合的完整管理快照；{@code userVersion} 是后续全量替换命令唯一允许使用的并发版本。
     */
    public record UserAuthorizationView(
            String userId,
            long userVersion,
            Set<String> roleIds,
            Set<String> factoryIds,
            boolean mobileAccessEnabled,
            PartyBindingView partyBinding) { }

    /** 外部用户当前 Party Binding 的非敏感投影。 */
    public record PartyBindingView(
            String id,
            String partyType,
            String partyId,
            String status,
            long version) { }

    /**
     * 角色 Permission 聚合的完整管理快照；{@code roleVersion} 用于保护后续全量替换命令。
     */
    public record RolePermissionView(
            String roleId,
            long roleVersion,
            Set<String> permissionIds) { }
}
