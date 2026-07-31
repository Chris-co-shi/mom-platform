package io.github.chrisshi.mom.system.application.catalog;

import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ApplicationType;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.ClientChannel;
import io.github.chrisshi.mom.system.api.SystemCatalogContracts.NavigationType;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplication;
import io.github.chrisshi.mom.system.domain.catalog.SystemApplicationRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRelease;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogReleaseRepository;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogRules;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshot;
import io.github.chrisshi.mom.system.domain.catalog.SystemCatalogSnapshotCodec;
import io.github.chrisshi.mom.system.domain.catalog.SystemNavigationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Catalog Runtime 权限过滤、Snapshot 完整性与 I18n 发布门禁测试。 */
class SystemCatalogApplicationServiceTest {
    private SystemApplicationRepository applications;
    private SystemNavigationRepository navigation;
    private SystemCatalogReleaseRepository releases;
    private SystemCatalogSnapshotCodec codec;
    private CatalogI18nReferenceQuery i18n;
    private SystemCatalogApplicationService service;

    @BeforeEach
    void setUp() {
        applications = mock(SystemApplicationRepository.class);
        navigation = mock(SystemNavigationRepository.class);
        releases = mock(SystemCatalogReleaseRepository.class);
        codec = mock(SystemCatalogSnapshotCodec.class);
        i18n = mock(CatalogI18nReferenceQuery.class);
        service = new SystemCatalogApplicationService(applications, navigation, releases, codec, i18n);
    }

    @Test
    void runtimeMustFilterByRealAuthoritiesAndRemoveEmptyGroups() {
        SystemCatalogSnapshot snapshot = snapshot();
        String json = "{\"snapshot\":1}";
        String checksum = SystemCatalogRules.sha256(json);
        SystemApplication application = application("release", 1);
        SystemCatalogRelease release = release("release", json, checksum);
        when(applications.findByCode("iam")).thenReturn(Optional.of(application));
        when(releases.findById("release")).thenReturn(Optional.of(release));
        when(codec.decode(json)).thenReturn(snapshot);

        var allowed = service.runtimeApplication("iam", Set.of("iam:user:read"));
        assertThat(allowed.view().applications()).hasSize(1);
        assertThat(allowed.view().applications().getFirst().channels().getFirst().navigation())
                .singleElement().satisfies(group -> assertThat(group.children())
                        .extracting(item -> item.routeKey())
                        .containsExactly("iam.users"));
        assertThatThrownBy(() -> service.runtimeApplication("iam", Set.of()))
                .isInstanceOf(SystemCatalogException.NotFound.class);
    }

    @Test
    void corruptedChecksumMustFailClosed() {
        SystemApplication application = application("release", 1);
        when(applications.findByCode("iam")).thenReturn(Optional.of(application));
        when(releases.findById("release")).thenReturn(Optional.of(
                release("release", "{}", "0".repeat(64))));
        assertThatThrownBy(() -> service.runtimeApplication("iam", Set.of("iam:user:read")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("checksum");
    }

    @Test
    void publishMustRejectMissingI18nReferenceBeforeAppendingRelease() {
        SystemApplication application = application(null, 0);
        when(applications.findById("app")).thenReturn(Optional.of(application));
        when(navigation.findByApplication("app")).thenReturn(List.of());
        when(i18n.findPublished(org.mockito.ArgumentMatchers.eq("iam"),
                org.mockito.ArgumentMatchers.anySet())).thenReturn(Set.of());
        when(codec.encode(org.mockito.ArgumentMatchers.any())).thenReturn("{}");
        assertThatThrownBy(() -> service.publish("app",
                new SystemCatalogApplicationModels.PublishCommand(0L, "initial")))
                .isInstanceOf(SystemCatalogException.Conflict.class)
                .satisfies(error -> assertThat(((SystemCatalogException) error).code())
                        .isEqualTo("invalid_i18n_reference"));
    }

    private static SystemApplication application(String releaseId, long publishedVersion) {
        return new SystemApplication("app", "iam", ApplicationType.PLATFORM,
                "mom-web", "mom.menu.iam", null, null, 1, 10, true,
                releaseId, publishedVersion, 0, "actor", Instant.EPOCH, "actor", Instant.EPOCH);
    }

    private static SystemCatalogRelease release(String id, String json, String checksum) {
        return new SystemCatalogRelease(id, "app", "iam", 1, 1, 1, 0,
                null, json, 3, checksum, "initial", "actor", Instant.EPOCH,
                "actor", Instant.EPOCH);
    }

    private static SystemCatalogSnapshot snapshot() {
        var users = new SystemCatalogSnapshot.NodeSnapshot("iam.users", NavigationType.ROUTE,
                "mom-web", "mom.menu.users", "iam:user:read", null,
                true, true, true, false, List.of());
        var roles = new SystemCatalogSnapshot.NodeSnapshot("iam.roles", NavigationType.ROUTE,
                "mom-web", "mom.menu.roles", "iam:role:read", null,
                true, true, true, false, List.of());
        var group = new SystemCatalogSnapshot.NodeSnapshot("iam.management", NavigationType.GROUP,
                "mom-web", "mom.menu.iam", null, null,
                true, true, false, false, List.of(users, roles));
        return new SystemCatalogSnapshot(1, "iam", ApplicationType.PLATFORM, 1,
                "mom-web", "mom.menu.iam", null,
                List.of(new SystemCatalogSnapshot.ChannelSnapshot(ClientChannel.WEB, List.of(group))));
    }
}
