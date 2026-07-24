package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamExternalUserBindingEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamExternalUserBindingMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserFactoryScopeMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserRoleMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 当前有效授权上下文只读仓储。
 *
 * <p>多表授权 JOIN 位于用户角色 Mapper XML，显式过滤禁用、删除、未生效和已过期关系；Factory 与
 * Party Scope 由对应表 Mapper 查询。不实现角色继承、Deny、ABAC 或跨 Schema 主数据查询。</p>
 */
public final class IamAuthorizationContextRepository {
    private final IamUserRoleMapper userRoleMapper;
    private final IamUserFactoryScopeMapper factoryScopeMapper;
    private final IamExternalUserBindingMapper bindingMapper;

    /** 创建授权上下文只读仓储。 */
    public IamAuthorizationContextRepository(
            IamUserRoleMapper userRoleMapper,
            IamUserFactoryScopeMapper factoryScopeMapper,
            IamExternalUserBindingMapper bindingMapper) {
        this.userRoleMapper = userRoleMapper;
        this.factoryScopeMapper = factoryScopeMapper;
        this.bindingMapper = bindingMapper;
    }

    /** @return 与用户类型匹配的当前有效角色编码，稳定排序并去重 */
    public List<String> listEffectiveRoleCodes(String userId, UserType userType, Instant now) {
        return userRoleMapper.selectEffectiveRoleCodes(userId, userType, now);
    }

    /** @return 通过有效角色并集计算出的 Permission 编码，稳定排序并去重 */
    public List<String> listEffectivePermissionCodes(String userId, UserType userType, Instant now) {
        return userRoleMapper.selectEffectivePermissionCodes(userId, userType, now);
    }

    /** @return 当前有效 Factory Scope，稳定排序并去重 */
    public List<String> listEffectiveFactoryIds(String userId, Instant now) {
        return factoryScopeMapper.selectEffectiveFactoryIds(userId, now);
    }

    /** @return 供应商或客户用户当前唯一且有效的 Party Scope */
    public Optional<PartyScope> findEffectivePartyScope(String userId, Instant now) {
        IamExternalUserBindingEntity binding = bindingMapper.selectEffectiveByUserId(userId, now);
        return Optional.ofNullable(binding)
                .map(value -> new PartyScope(value.getPartyType(), value.getPartyId()));
    }

    /** 外部用户唯一 Party Scope。 */
    public record PartyScope(PartyType partyType, String partyId) {
    }
}
