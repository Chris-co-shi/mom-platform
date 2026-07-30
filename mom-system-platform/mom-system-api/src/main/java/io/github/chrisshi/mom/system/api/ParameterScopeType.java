package io.github.chrisshi.mom.system.api;

/**
 * System Parameter 的稳定作用域契约。
 *
 * <p>S13 只允许全局与应用两个作用域。该枚举属于跨服务只读契约，不包含数据库、HTTP 或安全实现；新增
 * 作用域必须经过后续独立 Slice 和兼容性评审，调用方不得把 IAM Client、用户或工厂伪装成作用域。</p>
 */
public enum ParameterScopeType {
    /** 对所有应用生效的全局参数。 */
    GLOBAL,
    /** 由独立稳定 applicationCode 标识的应用覆盖参数。 */
    APPLICATION
}
