package io.github.chrisshi.mom.cache.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 定义缓存数据的业务隔离范围。
 *
 * <p>该值属于 {@code mom-cache} 公共契约，只表达 Global 或已经由服务端权威上下文验证的 Factory 标识，
 * 不负责从 Header、Token 或用户输入推导归属关系。实例不可变且线程安全；非法范围在构造阶段直接拒绝，
 * 防止不同 Factory 因 Key 拼接歧义共享缓存数据。</p>
 *
 * @param value 写入物理 Key 的稳定范围值
 */
public record CacheScope(String value) {

    /** Global Scope 的保留字，Factory 不得使用。 */
    public static final String GLOBAL_VALUE = "_global";

    private static final Pattern FACTORY_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public CacheScope {
        Objects.requireNonNull(value, "缓存 Scope 不能为空");
        if (!GLOBAL_VALUE.equals(value) && !FACTORY_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Factory Scope 必须是已验证且可安全进入 Key 的标识");
        }
    }

    /**
     * 创建平台级 Global Scope。
     *
     * @return 全局缓存范围；无外部副作用
     */
    public static CacheScope global() {
        return new CacheScope(GLOBAL_VALUE);
    }

    /**
     * 创建 Factory 隔离范围。
     *
     * <p>调用方必须先依据服务端授权和对象归属验证 Factory ID。本方法只做 Key 段格式校验，不能替代授权。</p>
     *
     * @param factoryId 已完成权威归属验证的 Factory ID
     * @return Factory 缓存范围
     * @throws IllegalArgumentException 当标识为空、使用保留字或包含不安全字符时抛出
     */
    public static CacheScope factory(String factoryId) {
        if (GLOBAL_VALUE.equals(factoryId)) {
            throw new IllegalArgumentException("_global 是保留 Scope，不能作为 Factory ID");
        }
        return new CacheScope(factoryId);
    }
}
