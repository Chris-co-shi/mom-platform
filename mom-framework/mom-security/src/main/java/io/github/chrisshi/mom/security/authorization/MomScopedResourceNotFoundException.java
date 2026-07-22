package io.github.chrisshi.mom.security.authorization;

/**
 * 业务对象对当前 Factory/Party 不可见时使用的统一异常。
 *
 * <p>业务 Web 层必须把该异常映射为 HTTP 404，而不是 403，以避免攻击者枚举其他 Factory、Supplier
 * 或 Customer 的对象是否存在。</p>
 */
public final class MomScopedResourceNotFoundException extends RuntimeException {
    public MomScopedResourceNotFoundException() {
        super("resource not found");
    }
}
