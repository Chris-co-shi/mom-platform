package io.github.chrisshi.mom.i18n.runtime;

import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
import io.github.chrisshi.mom.i18n.namespace.I18nNamespacePolicy;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class I18nRuntimeServiceTest {

    @Test
    void multipleNamespacesMustUseBatchQueriesAndPerMessageFallback() {
        I18nStore store = mock(I18nStore.class);
        List<String> namespaces = List.of("mdm.material", "mdm.uom");
        List<I18nMessage> messages = List.of(
                new I18nMessage("m1", "mdm.material", "page.title", null, true, 0, Instant.EPOCH),
                new I18nMessage("m2", "mdm.uom", "page.title", null, true, 0, Instant.EPOCH)
        );
        when(store.enabledMessages(namespaces)).thenReturn(messages);
        when(store.translations(List.of("m1", "m2"), List.of("en-US", "zh-CN"))).thenReturn(List.of(
                new I18nTranslation("t1", "m1", "en-US", "Materials", 0, Instant.EPOCH),
                new I18nTranslation("t2", "m1", "zh-CN", "物料", 0, Instant.EPOCH),
                new I18nTranslation("t3", "m2", "zh-CN", "计量单位", 0, Instant.EPOCH)
        ));

        I18nNamespacePolicy policy = value -> "mdm".equals(value) || value.startsWith("mdm.");
        I18nRuntimeService service = new I18nRuntimeService(
                store, policy, new LocalBaseLocalePolicy("zh-CN"));

        var bundle = service.load("en-US", namespaces);

        assertThat(bundle.bundles().get("mdm.material")).containsEntry("page.title", "Materials");
        assertThat(bundle.bundles().get("mdm.uom")).containsEntry("page.title", "计量单位");
        verify(store).enabledMessages(namespaces);
        verify(store).translations(List.of("m1", "m2"), List.of("en-US", "zh-CN"));
    }

    @Test
    void foreignNamespaceMustFailBeforeStoreAccess() {
        I18nStore store = mock(I18nStore.class);
        I18nRuntimeService service = new I18nRuntimeService(
                store, value -> "mdm".equals(value) || value.startsWith("mdm."),
                new LocalBaseLocalePolicy("zh-CN"));

        assertThatThrownBy(() -> service.load("zh-CN", List.of("mes.work-order")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }
}
