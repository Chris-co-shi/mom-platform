package io.github.chrisshi.mom.core.security;

import java.util.Optional;

/**
 * 提供当前操作人的跨模块抽象。
 *
 * <p>{@code mom-core} 只定义协议；{@code mom-security} 可从认证上下文解析用户，{@code mom-data} 只消费
 * 该接口。实现不得在缺失 Actor 时自动返回 SYSTEM、用户 ID 0 或匿名用户。</p>
 */
@FunctionalInterface
public interface CurrentActorProvider {

    /**
     * 查找当前执行上下文中的操作人。
     *
     * @return 已确认的操作人；当前上下文没有可靠身份时返回空
     */
    Optional<AuditActor> findCurrentActor();

    /**
     * 获取当前操作人，缺失时立即失败。
     *
     * @return 当前操作人
     * @throws AuditActorMissingException 当前无已认证用户且未显式建立 SYSTEM/ADMIN Actor 时抛出
     */
    default AuditActor requireCurrentActor() {
        return findCurrentActor().orElseThrow(AuditActorMissingException::new);
    }
}
