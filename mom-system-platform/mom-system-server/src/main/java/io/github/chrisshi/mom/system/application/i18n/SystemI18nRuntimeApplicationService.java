package io.github.chrisshi.mom.system.application.i18n;

import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.system.application.i18n.port.SystemI18nRuntimeCachePort;
import io.github.chrisshi.mom.system.application.i18n.port.SystemI18nRuntimeQueryPort;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeChangeEventPort;
import io.github.chrisshi.mom.system.domain.i18n.SystemI18nRepository;
import io.github.chrisshi.mom.system.domain.i18n.SystemI18nRules;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.PublishCommand;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.PublishView;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.ResourceView;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.RollbackCommand;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.RuntimeView;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.StatusCommand;

/**
 * Dynamic I18n 的可靠发布事件与 Runtime Cache 编排入口。
 *
 * <p>Primary Bean 复用既有管理、发布和回滚规则。Resource Kill Switch、双 Locale Release 与 Outbox 事件在
 * 同一 System PostgreSQL 本地事务提交；Runtime 强制 {@link Propagation#NEVER}，先读取不含 messages_json
 * 的 PostgreSQL Header，再访问版本化 Redis，Cache Miss 才查询单 Locale 完整 JSONB。</p>
 */
@Service
@Primary
public class SystemI18nRuntimeApplicationService extends SystemI18nApplicationService {
    private final SystemI18nRepository repository;
    private final SystemI18nRuntimeQueryPort runtimeQuery;
    private final SystemI18nRuntimeCachePort cache;
    private final SystemRuntimeChangeEventPort events;

    public SystemI18nRuntimeApplicationService(
            SystemI18nRepository repository,
            CurrentActorProvider actorProvider,
            Clock clock,
            SystemI18nRuntimeQueryPort runtimeQuery,
            SystemI18nRuntimeCachePort cache,
            SystemRuntimeChangeEventPort events) {
        super(repository, actorProvider, clock);
        this.repository = Objects.requireNonNull(repository, "repository");
        this.runtimeQuery = Objects.requireNonNull(runtimeQuery, "runtimeQuery");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Resource Kill Switch 与 Outbox 在同一事务提交。 */
    @Override
    @Transactional
    public ResourceView changeResourceStatus(String id, StatusCommand command) {
        ResourceView view = super.changeResourceStatus(id, command);
        events.i18nStatusChanged(new SystemRuntimeChangeEventPort.I18nStatusChangedEvent(
                view.id(),
                view.applicationCode(),
                view.resourceCode(),
                view.version(),
                view.enabled()));
        return view;
    }

    /** 双 Locale Release、发布指针与 Outbox 在同一事务提交。 */
    @Override
    @Transactional
    public PublishView publish(String resourceId, PublishCommand command) {
        PublishView view = super.publish(resourceId, command);
        appendPublished(resourceId, view);
        return view;
    }

    /** 回滚新 Release、发布指针与 Outbox 在同一事务提交。 */
    @Override
    @Transactional
    public PublishView rollback(String resourceId, RollbackCommand command) {
        PublishView view = super.rollback(resourceId, command);
        appendPublished(resourceId, view);
        return view;
    }

    /**
     * PostgreSQL Header 权威确认后的单 Locale Runtime Cache-Aside。
     *
     * <p>数据库不可用时异常向上传播；不会仅凭 Redis 返回旧翻译。Resource 禁用、未发布、双 Locale 不完整或
     * 请求 Locale 缺失均保持原有 404 Fail Closed 语义。</p>
     */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public RuntimeView runtime(
            String applicationCode,
            String resourceCode,
            String locale) {
        String application = SystemI18nRules.normalizeApplicationCode(applicationCode);
        String resource = SystemI18nRules.normalizeResourceCode(resourceCode);
        String requestedLocale = SystemI18nRules.requireLocale(locale);
        SystemI18nRuntimeQueryPort.RuntimeHeader header = runtimeQuery.findHeader(
                        application, resource, requestedLocale)
                .orElseThrow(() -> new SystemI18nException.NotFound("I18n Resource 不存在"));
        SystemI18nRuntimeQueryPort.RuntimeSnapshot snapshot = cache.find(header)
                .orElseGet(() -> {
                    SystemI18nRuntimeQueryPort.RuntimeSnapshot loaded = runtimeQuery.findSnapshot(header)
                            .orElseThrow(() -> new SystemI18nException.NotFound(
                                    "当前发布版本不存在或不完整"));
                    cache.put(header, loaded);
                    return loaded;
                });
        if (!matches(header, snapshot)) {
            throw new IllegalStateException("I18n Runtime Snapshot 与权威 Header 不一致");
        }
        return new RuntimeView(
                snapshot.applicationCode(),
                snapshot.resourceCode(),
                snapshot.locale(),
                snapshot.defaultLocale(),
                snapshot.releaseVersion(),
                snapshot.checksum(),
                snapshot.fallbackCount(),
                snapshot.publishedAt(),
                snapshot.messages());
    }

    private void appendPublished(String resourceId, PublishView view) {
        SystemI18nRepository.Resource resource = repository.findResourceById(resourceId)
                .orElseThrow(() -> new IllegalStateException("I18n 发布后资源头不存在"));
        events.i18nPublished(new SystemRuntimeChangeEventPort.I18nPublishedEvent(
                resource.id(),
                resource.applicationCode(),
                resource.resourceCode(),
                view.releaseVersion(),
                view.checksums(),
                view.sourceReleaseVersion()));
    }

    private static boolean matches(
            SystemI18nRuntimeQueryPort.RuntimeHeader header,
            SystemI18nRuntimeQueryPort.RuntimeSnapshot snapshot) {
        return header.applicationCode().equals(snapshot.applicationCode())
                && header.resourceCode().equals(snapshot.resourceCode())
                && header.locale().equals(snapshot.locale())
                && header.defaultLocale().equals(snapshot.defaultLocale())
                && header.releaseVersion() == snapshot.releaseVersion()
                && header.checksum().equals(snapshot.checksum())
                && header.fallbackCount() == snapshot.fallbackCount()
                && header.publishedAt().equals(snapshot.publishedAt());
    }
}
