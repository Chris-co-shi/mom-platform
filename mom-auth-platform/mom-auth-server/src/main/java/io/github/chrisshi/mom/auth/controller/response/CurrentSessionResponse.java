package io.github.chrisshi.mom.auth.controller.response;

import java.time.Instant;
import java.util.List;

/**
 * Auth 当前账户 HTTP 响应，实时用户资料与 Token 快照明确分区。
 *
 * <p>不暴露 Entity、凭据或摘要，不依赖数据库或 Redis；由 Controller 组合资料。
 * 记录不可变且线程安全；资料读取失败不得伪造成功，不刷新权限或延长 Token。</p>
 *
 * @param user 数据库实时用户资料
 * @param authorization 登录时的权限快照
 * @param session 当前 Token 元数据
 */
public record CurrentSessionResponse(UserProfile user, Authorization authorization, Session session) {
    /**
     * 不可变 HTTP 展示资料，不承担持久化或授权，version 用于写入并发控制。
     * @param userId String 技术主键，不用作显示名称
     * @param username 只读登录名
     * @param displayName 显示名
     * @param version 实时数据库版本
     */
    public record UserProfile(String userId, String username, String displayName, long version) { }

    /**
     * 不可变权限快照，无数据库读取或权限刷新副作用。
     * @param authorities 稳定排序的权限，可为空集合
     */
    public record Authorization(List<String> authorities) {
        /** 复制集合以隔离外部修改；null 为契约错误并直接拒绝。 */
        public Authorization {
            authorities = List.copyOf(authorities);
        }
    }

    /**
     * 不可变会话元数据，无基础设施依赖，不延长有效期。
     * @param expiresAt Token 绝对过期时间
     */
    public record Session(Instant expiresAt) { }
}
