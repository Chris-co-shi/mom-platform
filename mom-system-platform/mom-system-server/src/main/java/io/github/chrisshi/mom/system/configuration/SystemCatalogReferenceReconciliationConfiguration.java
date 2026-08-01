package io.github.chrisshi.mom.system.configuration;

import io.github.chrisshi.mom.system.application.catalog.SystemCatalogReferenceReconciliationService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Objects;

/**
 * Catalog 稳定 Reference 定期对账调度。
 *
 * <p>默认关闭；启用后每十分钟执行一次只读对账。调度只触发 Application Service，不持有数据库事务等待 IAM，
 * 不修改 Catalog，也不使用 Seata。可选 {@code run-on-startup} 仅在显式启用时于应用启动阶段执行一次，
 * 用于部署门禁或需要立即完成首轮对账的环境；失败会阻止实例进入 Ready。</p>
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

    /**
     * 显式启动门禁：在实例 Ready 前执行一次 IAM 权威对账。
     *
     * <p>默认不存在该 Bean；只有部署或 Smoke 明确设置 {@code run-on-startup=true} 时启用。</p>
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "mom.system.catalog.permission-reconciliation",
            name = "run-on-startup",
            havingValue = "true")
    ApplicationRunner catalogReferenceReconciliationStartupRunner() {
        return arguments -> reconciliation.reconcile();
    }
}
