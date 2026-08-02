package io.github.chrisshi.mom.system.configuration;

import io.github.chrisshi.mom.system.application.catalog.SystemCatalogReferenceReconciliationService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 定时对账入口只委派给非事务 Application Service。 */
class SystemCatalogReferenceReconciliationConfigurationTest {

    @Test
    void scheduledEntrypointMustDelegateToReconciliationService() {
        SystemCatalogReferenceReconciliationService service =
                mock(SystemCatalogReferenceReconciliationService.class);
        var configuration = new SystemCatalogReferenceReconciliationConfiguration(service);

        configuration.reconcile();

        verify(service).reconcile();
    }
}
