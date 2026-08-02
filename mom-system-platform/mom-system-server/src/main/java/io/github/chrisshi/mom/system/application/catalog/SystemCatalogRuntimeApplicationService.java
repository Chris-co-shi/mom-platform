package io.github.chrisshi.mom.system.application.catalog;

import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeChangeEventPort;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeCachePort;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplicationRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogReleaseRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshotCodec;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.ApplicationStatusCommand;
import static io.github.chrisshi.mom.system.application.catalog.SystemCatalogApplicationModels.ApplicationView;

/**
 * Catalog Application Kill Switch 的可靠事件编排入口。
 *
 * <p>Primary Bean 复用既有 Catalog Draft CRUD；仅覆盖 Application 启停，使状态行与不含路由、权限或正文的
 * Outbox 事件在同一 System PostgreSQL 本地事务提交。Redis 与 Broker 不进入本事务。</p>
 */
@Service
@Primary
public class SystemCatalogRuntimeApplicationService extends SystemCatalogApplicationService {
    private final SystemRuntimeChangeEventPort events;

    public SystemCatalogRuntimeApplicationService(
            SystemApplicationRepository applications,
            SystemNavigationRepository navigation,
            SystemCatalogReleaseRepository releases,
            SystemCatalogSnapshotCodec codec,
            CatalogI18nReferenceQuery i18nReferences,
            SystemRuntimeCachePort runtimeCache,
            SystemRuntimeChangeEventPort events) {
        super(applications, navigation, releases, codec, i18nReferences, runtimeCache);
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Application Kill Switch 与 Outbox 在同一事务提交。 */
    @Override
    @Transactional
    public ApplicationView changeApplicationStatus(
            String id,
            ApplicationStatusCommand command) {
        ApplicationView view = super.changeApplicationStatus(id, command);
        events.catalogStatusChanged(new SystemRuntimeChangeEventPort.CatalogStatusChangedEvent(
                view.id(),
                view.applicationCode(),
                view.version(),
                view.enabled()));
        return view;
    }
}
