package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * IAM 对单个用户当前有效授权结果的不可变快照。
 *
 * <p>Role、Permission、Factory Scope 与 Party Scope 都来自 IAM 权威数据库。该对象不包含密码、Token、
 * Authorization Code 或其他凭证，也不把 OAuth Scope 与业务 Permission 混合。</p>
 */
public record IamAuthorizationContext(
        String userId,
        String username,
        String displayName,
        UserType userType,
        List<String> roles,
        List<String> permissions,
        List<String> factoryIds,
        PartyType partyType,
        String partyId) {

    public IamAuthorizationContext {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(userType, "userType");
        roles = List.copyOf(roles == null ? List.of() : roles);
        permissions = List.copyOf(permissions == null ? List.of() : permissions);
        factoryIds = List.copyOf(factoryIds == null ? List.of() : factoryIds);
        if ((partyType == null) != (partyId == null)) {
            throw new IllegalArgumentException("partyType 与 partyId 必须同时存在或同时为空");
        }
    }

    /**
     * 返回防御性 ArrayList，兼容 Spring Authorization Server JDBC Store 的 Jackson 类型白名单。
     */
    @Override
    public List<String> roles() {
        return new ArrayList<>(roles);
    }

    /** 返回防御性 ArrayList，避免把 JDK 内部 ImmutableCollections 类型写入 OAuth JDBC Store。 */
    @Override
    public List<String> permissions() {
        return new ArrayList<>(permissions);
    }

    /** 返回防御性 ArrayList，供 JWT Claims 和 Me API 稳定序列化。 */
    @Override
    public List<String> factoryIds() {
        return new ArrayList<>(factoryIds);
    }

    /** @return 用户是否拥有指定业务 Permission */
    public boolean hasPermission(String permission) {
        return permission != null && permissions.contains(permission);
    }

    /** @return 用户是否拥有指定 Factory Scope */
    public boolean hasFactory(String factoryId) {
        return factoryId != null && factoryIds.contains(factoryId);
    }

    /** @return 是否为绑定到单一供应商或客户主体的外部用户 */
    public boolean externalPartyBound() {
        return partyType != null && partyId != null;
    }
}
