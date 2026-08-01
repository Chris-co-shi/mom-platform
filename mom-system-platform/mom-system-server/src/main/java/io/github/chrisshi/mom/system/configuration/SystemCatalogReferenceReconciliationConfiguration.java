package io.github.chrisshi.mom.system.configuration;

import io.github.chrisshi.mom.system.application.catalog.SystemCatalogReferenceReconciliationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

/**
 * Catalog 稳定 Reference 定期对账调度。
 *
 * <p>默认关闭；启用后每十分钟执行一次只读对账。调度只触发 Application Service，不持有数据库事务等待 IAM，
 * 不修改 Catalog，也不使用 Seata。启动门禁由独立配置负责，避免调度开关与启动时执行发生条件耦合。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "mom.system.catalog.permission-reconciliation",
        name = "enabled",
        havingValue = "true")
public class SystemCatalogReferenceReconciliationConfiguration {
    private final SystemCatalogReferenceReconciliationService reconciliation;

    public SystemCatalogReferenceReconciliationConfiguration(
            SystemCatalogReferenceReconciliationService reconciliation) {
        this.reconciliation = Objects.requireNonNull(reconciliation, "reconciliation");
    }

    /** 按固定延迟执行生产只读对账；异常由调度框架记录，后续周期继续重试。 */
    @Scheduled(
            fixedDelayString =
                    "${mom.system.catalog.permission-reconciliation.interval:PT10M}",
            initialDelayString =
                    "${mom.system.catalog.permission-reconciliation.initial-delay:PT1M}")
    void reconcile() {
        reconciliation.reconcile();
    }
}
