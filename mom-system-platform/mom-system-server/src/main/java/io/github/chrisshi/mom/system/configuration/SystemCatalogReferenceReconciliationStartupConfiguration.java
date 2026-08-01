package io.github.chrisshi.mom.system.configuration;

import io.github.chrisshi.mom.system.application.catalog.SystemCatalogReferenceReconciliationService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

/**
 * Catalog Reference 的显式启动对账门禁。
 *
 * <p>默认不创建。部署或验收环境显式启用后，在应用进入 Ready 前执行一次 IAM 权威对账；失败将中止启动。
 * 该门禁不依赖定时调度开关，避免“只要求启动对账却未创建 Runner”的条件耦合。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "mom.system.catalog.permission-reconciliation",
        name = "run-on-startup",
        havingValue = "true")
public class SystemCatalogReferenceReconciliationStartupConfiguration {

    /** 创建在 Ready 前执行一次对账的 ApplicationRunner。 */
    @Bean
    ApplicationRunner catalogReferenceReconciliationStartupRunner(
            SystemCatalogReferenceReconciliationService reconciliation) {
        Objects.requireNonNull(reconciliation, "reconciliation");
        return arguments -> reconciliation.reconcile();
    }
}
