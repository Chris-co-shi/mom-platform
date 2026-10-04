package io.github.chrisshi.mom.auth.application.model;

/**
 * Mini Auth 分页用例的强类型过滤参数集合。
 *
 * <p>当前 User、Role、Permission 目录尚未开放可选过滤字段，因此对应记录为空；请求仍必须显式传入
 * {@code params: {}}，避免使用 null 或无类型 Map。以后出现真实过滤需求时只扩展对应记录，不改变
 * Controller 与 Application 的 {@code PageQuery<XxxPageParams>} 唯一入参契约。</p>
 *
 * <p>这些记录位于 Application 入站边界，不依赖 HTTP、Entity 或 Mapper，且不可变、线程安全。</p>
 */
public final class AuthPageParams {
    private AuthPageParams() {
    }

    /** User 目录当前没有可选过滤条件。 */
    public record UserPageParams() { }

    /** Role 目录当前没有可选过滤条件。 */
    public record RolePageParams() { }

    /** Permission 目录当前没有可选过滤条件。 */
    public record PermissionPageParams() { }
}
