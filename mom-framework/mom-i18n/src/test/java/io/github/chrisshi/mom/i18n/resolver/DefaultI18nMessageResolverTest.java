package io.github.chrisshi.mom.i18n.resolver;

import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
import io.github.chrisshi.mom.i18n.namespace.I18nNamespacePolicy;
import io.github.chrisshi.mom.i18n.runtime.LocalBaseLocalePolicy;
import io.github.chrisshi.mom.i18n.store.I18nStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class DefaultI18nMessageResolverTest {

    @Test
    void explicitFeatureNamespaceMustQueryExactStoreIdentity() {
        I18nStore store = mock(I18nStore.class);
        I18nMessage message = new I18nMessage(
                "m1", "mdm.material", "error.not_found", null, true, 0, Instant.EPOCH);
        when(store.message("mdm.material", "error.not_found")).thenReturn(message);
        when(store.translations(List.of("m1"), List.of("en-US", "zh-CN"))).thenReturn(List.of(
                new I18nTranslation("t1", "m1", "en-US", "Material {0} does not exist", 0, Instant.EPOCH),
                new I18nTranslation("t2", "m1", "zh-CN", "物料 {0} 不存在", 0, Instant.EPOCH)
        ));

        I18nNamespacePolicy namespaces = value -> "mdm".equals(value) || value.startsWith("mdm.");
        I18nMessageResolver framework = (key, locale, args) -> key;
        DefaultI18nMessageResolver resolver = new DefaultI18nMessageResolver(
                store, namespaces, new LocalBaseLocalePolicy("zh-CN"), framework);

        assertThat(resolver.resolve("mdm.material", "error.not_found", Locale.US, "M-001"))
                .isEqualTo("Material M-001 does not exist");
        verify(store).message("mdm.material", "error.not_found");
    }

    @Test
    void missingRequestedTranslationMustFallbackToLocalBaseLocale() {
        I18nStore store = mock(I18nStore.class);
        I18nMessage message = new I18nMessage(
                "m1", "mdm.material", "error.not_found", null, true, 0, Instant.EPOCH);
        when(store.message("mdm.material", "error.not_found")).thenReturn(message);
        when(store.translations(List.of("m1"), List.of("de-DE", "zh-CN"))).thenReturn(List.of(
                new I18nTranslation("t1", "m1", "zh-CN", "物料不存在", 0, Instant.EPOCH)
        ));

        I18nNamespacePolicy namespaces = value -> "mdm".equals(value) || value.startsWith("mdm.");
        DefaultI18nMessageResolver resolver = new DefaultI18nMessageResolver(
                store, namespaces, new LocalBaseLocalePolicy("zh-CN"), (key, locale, args) -> key);

        assertThat(resolver.resolve("mdm.material", "error.not_found", Locale.GERMANY))
                .isEqualTo("物料不存在");
    }

    @Test
    void frameworkMessageMustStayOnClasspathResolver() {
        I18nStore store = mock(I18nStore.class);
        I18nNamespacePolicy namespaces = value -> value.startsWith("mdm");
        DefaultI18nMessageResolver resolver = new DefaultI18nMessageResolver(
                store, namespaces, new LocalBaseLocalePolicy("zh-CN"),
                (key, locale, args) -> "classpath:" + key);

        assertThat(resolver.resolve("framework.web.invalid_request", Locale.US))
                .isEqualTo("classpath:framework.web.invalid_request");
    }

    @Test
    void updatedMdmTranslationMustBeVisibleWithoutRecreatingResolver() {
        I18nStore store = mock(I18nStore.class);
        I18nMessage message = new I18nMessage(
                "1001", "mdm", "error.not_found", null, true, 0, Instant.EPOCH);
        when(store.message("mdm", "error.not_found")).thenReturn(message);
        when(store.translations(List.of("1001"), List.of("en-US", "zh-CN")))
                .thenReturn(List.of(
                        new I18nTranslation("2001", "1001", "en-US", "Not found", 0, Instant.EPOCH),
                        new I18nTranslation("2002", "1001", "zh-CN", "未找到", 0, Instant.EPOCH)))
                .thenReturn(List.of(
                        new I18nTranslation("2001", "1001", "en-US", "Resource missing", 1, Instant.EPOCH),
                        new I18nTranslation("2002", "1001", "zh-CN", "未找到", 0, Instant.EPOCH)));
        when(store.translations(List.of("1001"), List.of("zh-CN"))).thenReturn(List.of(
                new I18nTranslation("2002", "1001", "zh-CN", "未找到", 0, Instant.EPOCH)));
        DefaultI18nMessageResolver resolver = new DefaultI18nMessageResolver(store,
                namespace -> namespace.equals("mdm"), new LocalBaseLocalePolicy("zh-CN"),
                (key, locale, args) -> key);

        assertThat(resolver.resolve("mdm", "error.not_found", Locale.US)).isEqualTo("Not found");
        assertThat(resolver.resolve("mdm", "error.not_found", Locale.US)).isEqualTo("Resource missing");
        verify(store, times(2)).translations(List.of("1001"), List.of("en-US", "zh-CN"));
        assertThat(resolver.resolve("mdm", "error.not_found", Locale.SIMPLIFIED_CHINESE)).isEqualTo("未找到");
    }
}
