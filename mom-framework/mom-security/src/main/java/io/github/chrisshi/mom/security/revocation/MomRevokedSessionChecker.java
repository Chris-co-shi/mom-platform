package io.github.chrisshi.mom.security.revocation;

/**
 * 协议中立的已撤销 Session 查询端口。
 *
 * <p>业务 Resource Server 只使用已经完成 JWT 签名、Issuer、Audience 与时间校验后的 {@code sid}
 * 查询 IAM 权威撤销状态，不依赖 Gateway Header、IAM HTTP API 或 Session 数据库。实现必须查询与 IAM
 * 撤销写入相同的数据源；基础设施不可用或返回不确定结果时必须抛出
 * {@link MomRevocationStoreUnavailableException}，调用方不得按未撤销放行。</p>
 *
 * <p>该端口只读、无缓存，不改变 revoked sid 的 Key、TTL、写入或 Session 事务语义。</p>
 */
@FunctionalInterface
public interface MomRevokedSessionChecker {

    /**
     * 查询当前 Session 是否已撤销。
     *
     * @param sessionId 已验证 JWT 中的非空 sid
     * @return {@code true} 表示已撤销，{@code false} 表示当前未发现撤销记录
     * @throws MomRevocationStoreUnavailableException 撤销数据源不可用或结果不确定
     */
    boolean isRevoked(String sessionId);
}
