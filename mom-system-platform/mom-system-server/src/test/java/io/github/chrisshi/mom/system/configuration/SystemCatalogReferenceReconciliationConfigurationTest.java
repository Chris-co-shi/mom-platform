package io.github.chrisshi.mom.system.configuration;

import io.github.chrisshi.mom.system.application.catalog.SystemCatalogReferenceReconciliationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Catalog Reference 定时与启动门禁使用同一 Application Service 的装配测试。 */
class SystemCatalogReferenceReconciliationConfigurationTest {

    @Test
    void scheduledAndStartupEntrypointsMustDelegateToSameNonTransactionalService() throws Exception {
        SystemCatalogReferenceReconciliationService service =
                mock(SystemCatalogReferenceReconciliationService.class);
        var configuration = new SystemCatalogReferenceReconciliationConfiguration(service);

        configuration.reconcile();
        configuration.catalogReferenceReconciliationStartupRunner().run(null);

        verify(service, times(2)).reconcile();
    }
}
