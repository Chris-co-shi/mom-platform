package io.github.chrisshi.mom.iam.admin;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** S09 MOM Admin 编辑表单需要的只读授权投影；不包含密码、Token 或 Session 凭证。 */
public final class IamAdminReadModelRepository {
    private final JdbcTemplate jdbc;

    public IamAdminReadModelRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UserAuthorizationView userAuthorization(String userId) {
        Integer users = jdbc.queryForObject(
                "SELECT count(*) FROM iam_user WHERE id=? AND deleted=false",
                Integer.class, userId);
        if (users == null || users == 0) {
            throw new IamAdminExceptions.NotFound("用户不存在");
        }
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
                roleIds, factoryIds, mobileCount != null && mobileCount > 0, partyBinding);
    }

    public RolePermissionView rolePermissions(String roleId) {
        Integer roles = jdbc.queryForObject(
                "SELECT count(*) FROM iam_role WHERE id=? AND deleted=false",
                Integer.class, roleId);
        if (roles == null || roles == 0) {
            throw new IamAdminExceptions.NotFound("角色不存在");
        }
        Set<String> permissionIds = orderedSet(jdbc.queryForList("""
                SELECT rp.permission_id
                  FROM iam_role_permission rp
                  JOIN iam_permission p ON p.id=rp.permission_id
                 WHERE rp.role_id=? AND p.deleted=false AND p.status='ENABLED'
                 ORDER BY rp.permission_id
                """, String.class, roleId));
        return new RolePermissionView(permissionIds);
    }

    private static Set<String> orderedSet(List<String> values) {
        return Set.copyOf(new LinkedHashSet<>(values));
    }

    public record UserAuthorizationView(
            Set<String> roleIds,
            Set<String> factoryIds,
            boolean mobileAccessEnabled,
            PartyBindingView partyBinding) { }

    public record PartyBindingView(
            String id,
            String partyType,
            String partyId,
            String status,
            long version) { }

    public record RolePermissionView(Set<String> permissionIds) { }
}
