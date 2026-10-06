package io.github.chrisshi.mom.auth.application.model;

/**
 * Mini Auth 分页用例的强类型过滤参数集合。
 *
 * <p>User、Role 目录暂无过滤字段；Permission 与 Permission Resource 按真实查询需求开放强类型参数。
 * 请求仍必须显式传入 {@code params: {}}，避免使用 null 或无类型 Map；不改变
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

    /** Permission 分页过滤；空字段代表不限制，enabled 是本地启停状态。 */
    public record PermissionPageParams(String domainCode, String resourceId, String keyword, Boolean enabled) { }

    /** Permission Resource 分页过滤；空字段代表不限制。 */
    public record PermissionResourcePageParams(String domainCode, String keyword, Boolean enabled) { }
}
