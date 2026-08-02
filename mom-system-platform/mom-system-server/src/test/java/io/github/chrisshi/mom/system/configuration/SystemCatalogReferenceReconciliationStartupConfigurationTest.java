package io.github.chrisshi.mom.system.configuration;

import io.github.chrisshi.mom.system.application.catalog.SystemCatalogReferenceReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 启动对账门禁的真实 Spring 条件装配测试。 */
class SystemCatalogReferenceReconciliationStartupConfigurationTest {
    private final SystemCatalogReferenceReconciliationService service =
            mock(SystemCatalogReferenceReconciliationService.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SystemCatalogReferenceReconciliationStartupConfiguration.class)
            .withBean(SystemCatalogReferenceReconciliationService.class, () -> service);

    @Test
    void startupRunnerMustBeAbsentByDefault() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean("catalogReferenceReconciliationStartupRunner"));
    }

    @Test
    void explicitStartupPropertyMustCreateAndExecuteRunner() {
        when(service.reconcile()).thenReturn(
                new SystemCatalogReferenceReconciliationService.ReconciliationResult(
                        1, 1, 1, 0, 0));

        contextRunner
                .withPropertyValues(
                        "mom.system.catalog.permission-reconciliation.run-on-startup=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ApplicationRunner.class);
                    context.getBean(ApplicationRunner.class).run(null);
                    verify(service).reconcile();
                });
    }
}
