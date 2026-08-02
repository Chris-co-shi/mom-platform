package io.github.chrisshi.mom.system.application.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ApplicationType;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeChangeEventPort;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeCachePort;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplication;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplicationRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogReleaseRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshotCodec;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Catalog Application Kill Switch 与非敏感 Outbox 事件测试。 */
class SystemCatalogRuntimeApplicationServiceTest {

    @Test
    void statusChangeMustAppendEventInApplicationBoundary() {
        SystemApplicationRepository applications = mock(SystemApplicationRepository.class);
        SystemNavigationRepository navigation = mock(SystemNavigationRepository.class);
        SystemCatalogReleaseRepository releases = mock(SystemCatalogReleaseRepository.class);
        SystemCatalogSnapshotCodec codec = mock(SystemCatalogSnapshotCodec.class);
        CatalogI18nReferenceQuery i18n = mock(CatalogI18nReferenceQuery.class);
        SystemRuntimeCachePort runtimeCache = mock(SystemRuntimeCachePort.class);
        SystemRuntimeChangeEventPort events = mock(SystemRuntimeChangeEventPort.class);
        SystemApplication current = application(true, 3);
        SystemApplication persisted = application(false, 4);
        when(applications.findById("app")).thenReturn(Optional.of(current), Optional.of(persisted));
        when(applications.updateStatus(any())).thenReturn(true);
        var service = new SystemCatalogRuntimeApplicationService(
                applications, navigation, releases, codec, i18n, runtimeCache, events);

        var result = service.changeApplicationStatus(
                "app", new SystemCatalogApplicationModels.ApplicationStatusCommand(false, 3L));

        assertThat(result.enabled()).isFalse();
        assertThat(result.version()).isEqualTo(4);
        ArgumentCaptor<SystemRuntimeChangeEventPort.CatalogStatusChangedEvent> event =
                ArgumentCaptor.forClass(SystemRuntimeChangeEventPort.CatalogStatusChangedEvent.class);
        verify(events).catalogStatusChanged(event.capture());
        assertThat(event.getValue().applicationCode()).isEqualTo("iam");
        assertThat(event.getValue().enabled()).isFalse();
        assertThat(SystemRuntimeChangeEventPort.CatalogStatusChangedEvent.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("permissionCode", "navigation", "snapshot", "secret");
    }

    private static SystemApplication application(boolean enabled, long version) {
        return new SystemApplication(
                "app",
                "iam",
                ApplicationType.PLATFORM,
                "mom-web",
                "mom.menu.iam",
                null,
                null,
                1,
                10,
                enabled,
                "release",
                2,
                version,
                "actor",
                Instant.EPOCH,
                "actor",
                Instant.EPOCH);
    }
}
