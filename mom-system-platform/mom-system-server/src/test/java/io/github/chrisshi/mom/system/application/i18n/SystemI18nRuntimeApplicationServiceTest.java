package io.github.chrisshi.mom.system.application.i18n;

import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.system.application.i18n.port.SystemI18nRuntimeCachePort;
import io.github.chrisshi.mom.system.application.i18n.port.SystemI18nRuntimeQueryPort;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeChangeEventPort;
import io.github.chrisshi.mom.system.domain.i18n.SystemI18nRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Dynamic I18n 可靠状态事件与 PostgreSQL-First Runtime Cache 测试。 */
class SystemI18nRuntimeApplicationServiceTest {

    @Test
    void statusChangeMustAppendNonSensitiveKillSwitchEvent() {
        Fixture fixture = new Fixture();
        SystemI18nRepository.Resource current = fixture.resource(true, null, 0);
        SystemI18nRepository.Resource persisted = fixture.resource(false, null, 1);
        when(fixture.repository.findResourceById("1"))
                .thenReturn(Optional.of(current), Optional.of(persisted));
        when(fixture.repository.updateResource(any())).thenReturn(true);

        var result = fixture.service.changeResourceStatus(
                "1", new SystemI18nApplicationModels.StatusCommand(false, 0L));

        assertThat(result.enabled()).isFalse();
        ArgumentCaptor<SystemRuntimeChangeEventPort.I18nStatusChangedEvent> event =
                ArgumentCaptor.forClass(SystemRuntimeChangeEventPort.I18nStatusChangedEvent.class);
        verify(fixture.events).i18nStatusChanged(event.capture());
        assertThat(event.getValue().applicationCode()).isEqualTo("mom-web");
        assertThat(event.getValue().resourceCode()).isEqualTo("navigation");
        assertThat(SystemRuntimeChangeEventPort.I18nStatusChangedEvent.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("messages", "messageValue", "translation", "secret");
    }

    @Test
    void matchingCacheMustAvoidFullSnapshotQueryAfterAuthorityHeader() {
        Fixture fixture = new Fixture();
        SystemI18nRuntimeQueryPort.RuntimeHeader header = fixture.header();
        SystemI18nRuntimeQueryPort.RuntimeSnapshot cached = fixture.snapshot();
        when(fixture.runtimeQuery.findHeader("mom-web", "navigation", "en-US"))
                .thenReturn(Optional.of(header));
        when(fixture.cache.find(header)).thenReturn(Optional.of(cached));

        var result = fixture.service.runtime("mom-web", "navigation", "en-US");

        assertThat(result.messages()).containsEntry("menu.home", "Home");
        verify(fixture.runtimeQuery, never()).findSnapshot(any());
    }

    @Test
    void cacheMissMustReadOneSnapshotAndPopulateProjection() {
        Fixture fixture = new Fixture();
        SystemI18nRuntimeQueryPort.RuntimeHeader header = fixture.header();
        SystemI18nRuntimeQueryPort.RuntimeSnapshot loaded = fixture.snapshot();
        when(fixture.runtimeQuery.findHeader("mom-web", "navigation", "en-US"))
                .thenReturn(Optional.of(header));
        when(fixture.cache.find(header)).thenReturn(Optional.empty());
        when(fixture.runtimeQuery.findSnapshot(header)).thenReturn(Optional.of(loaded));

        assertThat(fixture.service.runtime("mom-web", "navigation", "en-US").releaseVersion())
                .isEqualTo(3);
        verify(fixture.cache).put(header, loaded);
    }

    @Test
    void missingAuthorityHeaderMustFailClosedBeforeCacheRead() {
        Fixture fixture = new Fixture();
        when(fixture.runtimeQuery.findHeader("mom-web", "navigation", "en-US"))
                .thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        fixture.service.runtime("mom-web", "navigation", "en-US"))
                .isInstanceOf(SystemI18nException.NotFound.class);
        verify(fixture.cache, never()).find(any());
    }

    private static final class Fixture {
        private final SystemI18nRepository repository = mock(SystemI18nRepository.class);
        private final CurrentActorProvider actorProvider = mock(CurrentActorProvider.class);
        private final SystemI18nRuntimeQueryPort runtimeQuery = mock(SystemI18nRuntimeQueryPort.class);
        private final SystemI18nRuntimeCachePort cache = mock(SystemI18nRuntimeCachePort.class);
        private final SystemRuntimeChangeEventPort events = mock(SystemRuntimeChangeEventPort.class);
        private final Instant now = Instant.parse("2026-08-01T00:00:00Z");
        private final SystemI18nRuntimeApplicationService service =
                new SystemI18nRuntimeApplicationService(
                        repository,
                        actorProvider,
                        Clock.fixed(now, ZoneOffset.UTC),
                        runtimeQuery,
                        cache,
                        events);

        private SystemI18nRepository.Resource resource(
                boolean enabled, Long publishedVersion, long version) {
            return new SystemI18nRepository.Resource(
                    "1",
                    "mom-web",
                    "navigation",
                    "Navigation",
                    "zh-CN",
                    enabled,
                    publishedVersion,
                    null,
                    null,
                    version,
                    null,
                    "actor",
                    now,
                    "actor",
                    now);
        }

        private SystemI18nRuntimeQueryPort.RuntimeHeader header() {
            return new SystemI18nRuntimeQueryPort.RuntimeHeader(
                    "1",
                    "mom-web",
                    "navigation",
                    "zh-CN",
                    3,
                    "en-US",
                    "a".repeat(64),
                    1,
                    now);
        }

        private SystemI18nRuntimeQueryPort.RuntimeSnapshot snapshot() {
            return new SystemI18nRuntimeQueryPort.RuntimeSnapshot(
                    "mom-web",
                    "navigation",
                    "en-US",
                    "zh-CN",
                    3,
                    "a".repeat(64),
                    1,
                    now,
                    Map.of("menu.home", "Home"));
        }
    }
}
