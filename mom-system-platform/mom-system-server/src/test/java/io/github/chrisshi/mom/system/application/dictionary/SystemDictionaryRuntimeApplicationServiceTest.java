package io.github.chrisshi.mom.system.application.dictionary;

import io.github.chrisshi.mom.system.api.SystemDictionaryItemOption;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeCachePort;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeChangeEventPort;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionary;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryItemRepository;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Dictionary 可靠写事件、权威 Kill Switch 与版本化 Runtime Cache 测试。 */
class SystemDictionaryRuntimeApplicationServiceTest {

    @Test
    void createMustAppendNonSensitiveEventAfterDomainWrite() {
        Fixture fixture = new Fixture(true);
        when(fixture.dictionaries.insert(any())).thenReturn(fixture.dictionary);

        var result = fixture.service.createDictionary(
                new SystemDictionaryApplicationModels.CreateDictionaryCommand(
                        "system.common.state", "State", null, true));

        assertThat(result.dictionaryCode()).isEqualTo("system.common.state");
        ArgumentCaptor<SystemRuntimeChangeEventPort.DictionaryChangedEvent> event =
                ArgumentCaptor.forClass(SystemRuntimeChangeEventPort.DictionaryChangedEvent.class);
        verify(fixture.events).dictionaryChanged(event.capture());
        assertThat(event.getValue().dictionaryCode()).isEqualTo("system.common.state");
        assertThat(event.getValue().itemCode()).isNull();
        assertThat(event.getValue().changeKind())
                .isEqualTo(SystemRuntimeChangeEventPort.ChangeKind.CREATED);
        assertThat(SystemRuntimeChangeEventPort.DictionaryChangedEvent.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("itemLabel", "dictionaryName", "description", "secret");
    }

    @Test
    void activeItemsMustUseCacheOnlyAfterEnabledDictionaryHeaderRead() {
        Fixture fixture = new Fixture(true);
        SystemDictionaryItemOption cached = new SystemDictionaryItemOption(
                "system.common.state", "ready", "Ready", 10, 3, Instant.EPOCH);
        when(fixture.cache.findDictionaryItems("system.common.state", 4))
                .thenReturn(Optional.of(List.of(cached)));

        assertThat(fixture.service.activeItems("system.common.state"))
                .containsExactly(cached);
        verify(fixture.items, never()).findActive(any());
    }

    @Test
    void disabledDictionaryMustActAsImmediateKillSwitchBeforeCacheRead() {
        Fixture fixture = new Fixture(false);

        assertThat(fixture.service.activeItems("system.common.state")).isEmpty();
        verify(fixture.cache, never()).findDictionaryItems(any(), anyLong());
        verify(fixture.items, never()).findActive(any());
    }

    private static final class Fixture {
        private final SystemDictionaryRepository dictionaries =
                mock(SystemDictionaryRepository.class);
        private final SystemDictionaryItemRepository items =
                mock(SystemDictionaryItemRepository.class);
        private final SystemRuntimeCachePort cache = mock(SystemRuntimeCachePort.class);
        private final SystemRuntimeChangeEventPort events =
                mock(SystemRuntimeChangeEventPort.class);
        private final SystemDictionary dictionary;
        private final SystemDictionaryRuntimeApplicationService service;

        private Fixture(boolean enabled) {
            dictionary = new SystemDictionary(
                    "1",
                    "system.common.state",
                    "State",
                    enabled,
                    4,
                    null,
                    "actor",
                    Instant.EPOCH,
                    "actor",
                    Instant.EPOCH);
            when(dictionaries.findByCode("system.common.state"))
                    .thenReturn(Optional.of(dictionary));
            service = new SystemDictionaryRuntimeApplicationService(
                    dictionaries, items, cache, events);
        }
    }
}
