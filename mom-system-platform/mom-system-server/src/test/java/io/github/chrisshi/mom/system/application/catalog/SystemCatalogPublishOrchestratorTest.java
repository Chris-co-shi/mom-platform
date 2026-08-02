package io.github.chrisshi.mom.system.application.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ApplicationType;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.NavigationType;
import io.github.chrisshi.mom.system.application.catalog.port.CatalogReferenceValidationPort;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplication;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplicationRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogReleaseRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRules;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshotCodec;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationItem;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Catalog 事务外 Reference 校验与候选指纹编排测试。 */
class SystemCatalogPublishOrchestratorTest {

    @Test
    void mustValidateReferencesOutsideCommitAndPassVersionChecksumAndSet() {
        Fixture fixture = new Fixture();
        when(fixture.referenceValidation.validate(anySet())).thenReturn(
                new CatalogReferenceValidationPort.ValidationResult(Instant.EPOCH,
                        Map.of("iam:user:read", CatalogReferenceValidationPort.Status.ENABLED)));
        when(fixture.commit.publish(any(), any(), any())).thenReturn(fixture.releaseView());

        fixture.orchestrator.publish("app", new SystemCatalogApplicationModels.PublishCommand(3L, "publish"));

        ArgumentCaptor<SystemCatalogPublishPlan> plan =
                ArgumentCaptor.forClass(SystemCatalogPublishPlan.class);
        verify(fixture.referenceValidation).validate(Set.of("iam:user:read"));
        verify(fixture.commit).publish(any(), any(), plan.capture());
        assertThat(plan.getValue().applicationVersion()).isEqualTo(3L);
        assertThat(plan.getValue().checksum()).isEqualTo(SystemCatalogRules.sha256("snapshot-json"));
        assertThat(plan.getValue().permissionCodes()).containsExactly("iam:user:read");
    }

    @Test
    void disabledOrUnknownReferenceMustFailBeforeLocalCommit() {
        Fixture fixture = new Fixture();
        when(fixture.referenceValidation.validate(anySet())).thenReturn(
                new CatalogReferenceValidationPort.ValidationResult(Instant.EPOCH,
                        Map.of("iam:user:read", CatalogReferenceValidationPort.Status.DISABLED)));

        assertThatThrownBy(() -> fixture.orchestrator.publish(
                "app", new SystemCatalogApplicationModels.PublishCommand(3L, "publish")))
                .isInstanceOf(SystemCatalogException.Conflict.class)
                .extracting(exception -> ((SystemCatalogException.Conflict) exception).code())
                .isEqualTo("invalid_permission_reference");
        verify(fixture.commit, never()).publish(any(), any(), any());
    }

    private static final class Fixture {
        private final SystemApplicationRepository applications = mock(SystemApplicationRepository.class);
        private final SystemNavigationRepository navigation = mock(SystemNavigationRepository.class);
        private final SystemCatalogReleaseRepository releases = mock(SystemCatalogReleaseRepository.class);
        private final SystemCatalogSnapshotCodec codec = mock(SystemCatalogSnapshotCodec.class);
        private final CatalogReferenceValidationPort referenceValidation =
                mock(CatalogReferenceValidationPort.class);
        private final SystemCatalogPublishCommitService commit = mock(SystemCatalogPublishCommitService.class);
        private final SystemCatalogPublishOrchestrator orchestrator;

        private Fixture() {
            SystemApplication application = new SystemApplication(
                    "app", "iam", ApplicationType.PLATFORM, "mom-web", "mom.menu.iam",
                    null, null, 1, 10, true, null, 0, 3,
                    "actor", Instant.EPOCH, "actor", Instant.EPOCH);
            SystemNavigationItem item = new SystemNavigationItem(
                    "node", "app", null, ClientChannel.WEB, NavigationType.ROUTE,
                    "iam.users", "mom-web", "mom.menu.users", "iam:user:read", null,
                    true, true, true, false, 10, true, 0,
                    "actor", Instant.EPOCH, "actor", Instant.EPOCH);
            when(applications.findById("app")).thenReturn(Optional.of(application));
            when(navigation.findByApplication("app")).thenReturn(List.of(item));
            when(codec.encode(any())).thenReturn("snapshot-json");
            orchestrator = new SystemCatalogPublishOrchestrator(
                    applications, navigation, releases, codec, referenceValidation, commit);
        }

        private SystemCatalogApplicationModels.CatalogReleaseView releaseView() {
            return new SystemCatalogApplicationModels.CatalogReleaseView(
                    "iam", 1, 1, 1, "a".repeat(64), 1, null, 4, Instant.EPOCH);
        }
    }
}
