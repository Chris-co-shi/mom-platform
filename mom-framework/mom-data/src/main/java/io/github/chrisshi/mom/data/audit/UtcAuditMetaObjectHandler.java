package io.github.chrisshi.mom.data.audit;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * MyBatis-Plus 数据审计字段自动填充器。
 *
 * <p>插入时填充 {@code createdAt}、{@code updatedAt} 和初始 {@code version}；更新时只刷新
 * {@code updatedAt}。所有时间均来自 UTC {@link Clock}，因此不受应用服务器本地时区影响。</p>
 *
 * <p>操作主体由可选的 {@link CurrentActorProvider} 提供。IAM 或安全模块尚未接入时保持空值，
 * 不会伪造为“admin”或“system”。如果存在多个实现，只使用 Spring 排序后的第一个实现，应用应通过
 * {@code @Primary} 或 {@code @Order} 明确选择。</p>
 */
public final class UtcAuditMetaObjectHandler implements MetaObjectHandler {

    private final Clock clock;
    private final ObjectProvider<CurrentActorProvider> actorProviders;

    /**
     * 创建 UTC 审计字段填充器。
     *
     * @param clock UTC 时钟；测试可以替换为固定时钟
     * @param actorProviders 当前操作主体提供器集合
     */
    public UtcAuditMetaObjectHandler(
            Clock clock,
            ObjectProvider<CurrentActorProvider> actorProviders) {
        this.clock = clock;
        this.actorProviders = actorProviders;
    }

    /**
     * 为首次持久化实体填充审计字段。
     *
     * @param metaObject MyBatis 当前实体元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        Instant now = clock.instant();
        strictInsertFill(metaObject, "createdAt", Instant.class, now);
        strictInsertFill(metaObject, "updatedAt", Instant.class, now);
        strictInsertFill(metaObject, "version", Long.class, 0L);
        currentActorId().ifPresent(actorId -> {
            strictInsertFill(metaObject, "createdBy", String.class, actorId);
            strictInsertFill(metaObject, "updatedBy", String.class, actorId);
        });
    }

    /**
     * 为更新操作刷新最近修改时间和修改主体。
     *
     * @param metaObject MyBatis 当前实体元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", Instant.class, clock.instant());
        currentActorId().ifPresent(actorId ->
                strictUpdateFill(metaObject, "updatedBy", String.class, actorId));
    }

    /**
     * 获取排序最高且返回非空值的操作主体。
     */
    private Optional<String> currentActorId() {
        return actorProviders.orderedStream()
                .map(CurrentActorProvider::currentActorId)
                .flatMap(Optional::stream)
                .map(String::trim)
                .filter(actorId -> !actorId.isBlank())
                .findFirst();
    }
}
