package io.github.chrisshi.mom.i18n.runtime;

import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class I18nRuntimeServiceTest {

    @Test
    void ownerBundleMustUseBatchQueriesAndFallbackEachKeyIndependently() {
        I18nStore store = mock(I18nStore.class);
        List<I18nMessage> messages = List.of(
                new I18nMessage("m1", "mdm.material.page.title", null, true, 0, Instant.EPOCH),
                new I18nMessage("m2", "mdm.uom.page.title", null, true, 0, Instant.EPOCH)
        );
        when(store.enabledMessages()).thenReturn(messages);
        when(store.translations(List.of("m1", "m2"), List.of("en-US", "zh-CN"))).thenReturn(List.of(
                new I18nTranslation("t1", "m1", "en-US", "Materials", 0, Instant.EPOCH),
                new I18nTranslation("t2", "m1", "zh-CN", "物料", 0, Instant.EPOCH),
                new I18nTranslation("t3", "m2", "zh-CN", "计量单位", 0, Instant.EPOCH)
        ));
        I18nRuntimeService service = new I18nRuntimeService(store, new LocalBaseLocalePolicy("zh-CN"));

        var bundle = service.load("en-US");

        assertThat(bundle.messages()).containsEntry("mdm.material.page.title", "Materials")
                .containsEntry("mdm.uom.page.title", "计量单位");
        verify(store).enabledMessages();
        verify(store).translations(List.of("m1", "m2"), List.of("en-US", "zh-CN"));
    }

    @Test
    void unsupportedRequestedLocaleMustUseLocalBaseLocale() {
        I18nStore store = mock(I18nStore.class);
        I18nMessage message = new I18nMessage(
                "m1", "mdm.material.page.title", null, true, 0, Instant.EPOCH);
        when(store.enabledMessages()).thenReturn(List.of(message));
        when(store.translations(List.of("m1"), List.of("zh-CN"))).thenReturn(List.of(
                new I18nTranslation("t1", "m1", "zh-CN", "物料", 0, Instant.EPOCH)));
        I18nRuntimeService service = new I18nRuntimeService(store, new LocalBaseLocalePolicy("zh-CN"));

        var bundle = service.load("unsupported-locale");

        assertThat(bundle.effectiveLocale()).isEqualTo("zh-CN");
        assertThat(bundle.messages()).containsEntry("mdm.material.page.title", "物料");
    }
}
