package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * S04 有效授权上下文只读仓储。
 *
 * <p>查询显式过滤禁用、删除、未生效和已过期关系；不实现角色继承、Deny、ABAC 或跨 Schema 主数据查询。</p>
 */
public final class IamAuthorizationContextRepository {
    private final JdbcTemplate jdbcTemplate;

    public IamAuthorizationContextRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 加载与用户类型匹配的当前有效角色编码，结果稳定排序并去重。 */
    public List<String> listEffectiveRoleCodes(String userId, UserType userType, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT r.code
                  FROM iam_user_role ur
                  JOIN iam_role r ON r.id = ur.role_id
                 WHERE ur.user_id = ?
                   AND ur.status = 'ENABLED'
                   AND (ur.valid_from IS NULL OR ur.valid_from <= ?)
                   AND (ur.valid_until IS NULL OR ur.valid_until > ?)
                   AND r.status = 'ENABLED'
                   AND r.deleted = false
                   AND r.applicable_user_type = ?
                 ORDER BY r.code
                """, String.class, userId, timestamp, timestamp, userType.name());
    }

    /** 通过有效角色并集加载当前有效 Permission 编码，结果稳定排序并去重。 */
    public List<String> listEffectivePermissionCodes(String userId, UserType userType, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT p.code
                  FROM iam_user_role ur
                  JOIN iam_role r ON r.id = ur.role_id
                  JOIN iam_role_permission rp ON rp.role_id = r.id
                  JOIN iam_permission p ON p.id = rp.permission_id
                 WHERE ur.user_id = ?
                   AND ur.status = 'ENABLED'
                   AND (ur.valid_from IS NULL OR ur.valid_from <= ?)
                   AND (ur.valid_until IS NULL OR ur.valid_until > ?)
                   AND r.status = 'ENABLED'
                   AND r.deleted = false
                   AND r.applicable_user_type = ?
                   AND p.status = 'ENABLED'
                   AND p.deleted = false
                 ORDER BY p.code
                """, String.class, userId, timestamp, timestamp, userType.name());
    }

    /** 加载当前有效 Factory Scope，结果稳定排序并去重。 */
    public List<String> listEffectiveFactoryIds(String userId, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT factory_id
                  FROM iam_user_factory_scope
                 WHERE user_id = ?
                   AND status = 'ENABLED'
                   AND (valid_from IS NULL OR valid_from <= ?)
                   AND (valid_until IS NULL OR valid_until > ?)
                 ORDER BY factory_id
                """, String.class, userId, timestamp, timestamp);
    }

    /** 加载供应商或客户用户当前唯一且有效的 Party Scope。 */
    public Optional<PartyScope> findEffectivePartyScope(String userId, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        List<PartyScope> scopes = jdbcTemplate.query("""
                SELECT party_type, party_id
                  FROM iam_external_user_binding
                 WHERE user_id = ?
                   AND status = 'ENABLED'
                   AND (valid_from IS NULL OR valid_from <= ?)
                   AND (valid_until IS NULL OR valid_until > ?)
                """, (resultSet, rowNumber) -> new PartyScope(
                        PartyType.valueOf(resultSet.getString("party_type")),
                        resultSet.getString("party_id")),
                userId, timestamp, timestamp);
        if (scopes.size() > 1) {
            throw new IllegalStateException("IAM 外部用户存在多个有效 Party Binding");
        }
        return scopes.stream().findFirst();
    }

    /** 外部用户唯一 Party Scope。 */
    public record PartyScope(PartyType partyType, String partyId) {
    }
}
