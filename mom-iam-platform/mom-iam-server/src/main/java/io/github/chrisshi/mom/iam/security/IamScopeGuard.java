package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.iam.domain.type.PartyType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Objects;

/**
 * Factory/Party Scope 的服务端校验基础契约。
 *
 * <p>`X-Factory-Id` 只是用户选择的当前工作上下文，必须属于 JWT/IAM 授权范围。业务对象不属于当前
 * Factory 或外部 Party 时统一表现为 404，避免通过 403 差异枚举对象。</p>
 */
public final class IamScopeGuard {

    /**
     * 校验可选 Current Factory Header。
     *
     * @return 规范化后的当前 Factory ID；未传入时返回 null
     */
    public String requireCurrentFactory(IamAuthorizationContext context, String requestedFactoryId) {
        Objects.requireNonNull(context, "context");
        if (requestedFactoryId == null || requestedFactoryId.isBlank()) {
            return null;
        }
        String factoryId = requestedFactoryId.trim();
        if (!context.hasFactory(factoryId)) {
            throw new CurrentFactoryAccessDeniedException();
        }
        return factoryId;
    }

    /**
     * 校验业务对象 Factory/Party 归属；不可见对象统一抛出 404。
     *
     * <p>INTERNAL 用户不受外部 Party 固定绑定限制，但仍必须命中 Factory Scope。SUPPLIER/CUSTOMER
     * 必须同时命中 Factory 与唯一 Party。</p>
     */
    public void requireObjectVisible(
            IamAuthorizationContext context,
            String objectFactoryId,
            PartyType objectPartyType,
            String objectPartyId) {
        Objects.requireNonNull(context, "context");
        if (objectFactoryId == null || !context.hasFactory(objectFactoryId)) {
            throw new ScopedResourceNotFoundException();
        }
        if (context.externalPartyBound()
                && (objectPartyType != context.partyType()
                || !Objects.equals(objectPartyId, context.partyId()))) {
            throw new ScopedResourceNotFoundException();
        }
    }

    /** 当前 Factory 不在用户授权范围时返回 403。 */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static final class CurrentFactoryAccessDeniedException extends RuntimeException {
        public CurrentFactoryAccessDeniedException() {
            super("当前工厂不在授权范围内");
        }
    }

    /** 业务对象对当前主体不可见时统一返回 404，禁止暴露对象是否存在。 */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static final class ScopedResourceNotFoundException extends RuntimeException {
        public ScopedResourceNotFoundException() {
            super("资源不存在");
        }
    }
}
