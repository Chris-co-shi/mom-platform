package io.github.chrisshi.mom.system.application.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ApplicationType;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.NavigationType;
import io.github.chrisshi.mom.system.application.catalog.port.CatalogReferenceValidationPort;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplication;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplicationRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRelease;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogReleaseRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRules;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshotCodec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Catalog 稳定 Reference 只读对账、Fail Closed 与低基数指标测试。 */
class SystemCatalogReferenceReconciliationServiceTest {

    @Test
    void mustBatchValidatePublishedReferencesWithoutMutatingCatalog() {
        Fixture fixture = new Fixture();
        when(fixture.references.validate(anySet())).thenReturn(
                new CatalogReferenceValidationPort.ValidationResult(
                        Instant.EPOCH,
                        Map.of(
                                "iam:user:read", CatalogReferenceValidationPort.Status.ENABLED,
                                "iam:user:write", CatalogReferenceValidationPort.Status.DISABLED)));

        var result = fixture.service.reconcile();

        assertThat(result.applicationCount()).isEqualTo(1);
        assertThat(result.referenceCount()).isEqualTo(2);
        assertThat(result.enabledCount()).isEqualTo(1);
        assertThat(result.disabledCount()).isEqualTo(1);
        assertThat(result.unknownCount()).isZero();
        assertThat(fixture.registry.counter(
                "mom.system.catalog.permission_reconciliation.results",
                "status",
                "disabled").count()).isEqualTo(1);
        assertThat(fixture.registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags())
                        .extracting(tag -> tag.getKey())
                        .containsOnly("status"));
        verify(fixture.references).validate(Set.of("iam:user:read", "iam:user:write"));
        verify(fixture.applications, never()).updatePublished(org.mockito.ArgumentMatchers.any());
        verify(fixture.releases, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void corruptedReleaseMustFailBeforeIamCall() {
        Fixture fixture = new Fixture();
        SystemCatalogRelease corrupt = new SystemCatalogRelease(
                "release",
                "app",
                "iam",
                3,
                1,
                1,
                4,
                null,
                "corrupt",
                2,
                "0".repeat(64),
                null,
                "actor",
                Instant.EPOCH,
                "actor",
                Instant.EPOCH);
        when(fixture.releases.findByIds(List.of("release"))).thenReturn(List.of(corrupt));

        assertThatThrownBy(fixture.service::reconcile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum");
        verify(fixture.references, never()).validate(anySet());
    }

    private static final class Fixture {
        private final SystemApplicationRepository applications =
                mock(SystemApplicationRepository.class);
        private final SystemCatalogReleaseRepository releases =
                mock(SystemCatalogReleaseRepository.class);
        private final SystemCatalogSnapshotCodec codec =
                mock(SystemCatalogSnapshotCodec.class);
        private final CatalogReferenceValidationPort references =
                mock(CatalogReferenceValidationPort.class);
        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        private final SystemCatalogReferenceReconciliationService service;

        private Fixture() {
            SystemApplication application = new SystemApplication(
                    "app",
                    "iam",
                    ApplicationType.PLATFORM,
                    "mom-web",
                    "mom.menu.iam",
                    null,
                    null,
                    1,
                    10,
                    true,
                    "release",
                    3,
                    4,
                    "actor",
                    Instant.EPOCH,
                    "actor",
                    Instant.EPOCH);
            SystemCatalogSnapshot snapshot = new SystemCatalogSnapshot(
                    1,
                    "iam",
                    ApplicationType.PLATFORM,
                    1,
                    "mom-web",
                    "mom.menu.iam",
                    null,
                    List.of(new SystemCatalogSnapshot.ChannelSnapshot(
                            ClientChannel.WEB,
                            List.of(new SystemCatalogSnapshot.NodeSnapshot(
                                    "iam.users",
                                    NavigationType.ROUTE,
                                    "mom-web",
                                    "mom.menu.users",
                                    "iam:user:read",
                                    null,
                                    true,
                                    true,
                                    true,
                                    false,
                                    List.of(new SystemCatalogSnapshot.NodeSnapshot(
                                            "iam.users.write",
                                            NavigationType.ROUTE,
                                            "mom-web",
                                            "mom.menu.users.write",
                                            "iam:user:write",
                                            null,
                                            false,
                                            false,
                                            false,
                                            false,
                                            List.of())))))));
            String json = "snapshot-json";
            SystemCatalogRelease release = new SystemCatalogRelease(
                    "release",
                    "app",
                    "iam",
                    3,
                    1,
                    1,
                    4,
                    null,
                    json,
                    2,
                    SystemCatalogRules.sha256(json),
                    null,
                    "actor",
                    Instant.EPOCH,
                    "actor",
                    Instant.EPOCH);
            when(applications.findEnabledPublished()).thenReturn(List.of(application));
            when(releases.findByIds(List.of("release"))).thenReturn(List.of(release));
            when(codec.decode(json)).thenReturn(snapshot);
            service = new SystemCatalogReferenceReconciliationService(
                    applications, releases, codec, references, registry);
        }
    }
}
