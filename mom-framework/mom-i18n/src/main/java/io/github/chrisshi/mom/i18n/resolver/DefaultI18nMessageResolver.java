package io.github.chrisshi.mom.i18n.resolver;

import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
import io.github.chrisshi.mom.i18n.namespace.I18nNamespacePolicy;
import io.github.chrisshi.mom.i18n.runtime.I18nLocalePolicy;
import io.github.chrisshi.mom.i18n.store.I18nStore;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;

/**
 * 业务错误的单条精确查询解析器。
 *
 * <p>业务调用优先显式传入 namespace + messageKey，可直接支持 {@code mdm.material} 等 Feature namespace；
 * 旧完整 Key 入口仅保留根 Owner 兼容语义。Framework 技术错误永远走 classpath，不依赖数据库。
 * 单条业务错误只查目标 Message 和至多两种 Locale 的译文，不加载整个 Runtime Bundle；DB 故障向上暴露。</p>
 */
public final class DefaultI18nMessageResolver implements I18nMessageResolver {
    private final I18nStore store;
    private final I18nNamespacePolicy namespaces;
    private final I18nLocalePolicy locales;
    private final I18nMessageResolver frameworkResolver;

    /** @param store 本服务 Store @param namespaces 本服务所有权 @param locales 本地回退
     * @param frameworkResolver 无 DB 依赖的技术错误解析器 */
    public DefaultI18nMessageResolver(I18nStore store, I18nNamespacePolicy namespaces,
                                      I18nLocalePolicy locales, I18nMessageResolver frameworkResolver) {
        this.store = store;
        this.namespaces = namespaces;
        this.locales = locales;
        this.frameworkResolver = frameworkResolver;
    }

    /** {@inheritDoc} */
    @Override
    public String resolve(String messageKey, Locale locale, Object... args) {
        if (messageKey == null || messageKey.isBlank()) {
            throw new IllegalArgumentException("messageKey 不能为空");
        }
        if (messageKey.startsWith("framework.")) {
            return frameworkResolver.resolve(messageKey, locale, args);
        }
        int boundary = messageKey.indexOf('.');
        if (boundary <= 0 || boundary == messageKey.length() - 1) {
            return messageKey;
        }
        return resolve(messageKey.substring(0, boundary), messageKey.substring(boundary + 1), locale, args);
    }

    /**
     * 业务消息使用显式 namespace 精确查询，支持 mdm.material、mes.work-order 等 Feature namespace。
     */
    @Override
    public String resolve(String namespace, String messageKey, Locale locale, Object... args) {
        if (namespace == null || namespace.isBlank() || messageKey == null || messageKey.isBlank()) {
            throw new IllegalArgumentException("namespace 和 messageKey 不能为空");
        }
        String stableKey = namespace + "." + messageKey;
        if ("framework".equals(namespace) || namespace.startsWith("framework.")) {
            return frameworkResolver.resolve(stableKey, locale, args);
        }
        if (!namespaces.owns(namespace)) {
            return stableKey;
        }
        I18nMessage message = store.message(namespace, messageKey);
        if (message == null || !message.enabled()) {
            return stableKey;
        }
        String effective = locales.effectiveLocale(locale == null ? null : locale.toLanguageTag());
        String base = locales.baseLocale();
        List<I18nTranslation> translations = store.translations(List.of(message.id()),
                effective.equals(base) ? List.of(base) : List.of(effective, base));
        String pattern = translations.stream().filter(item -> item.localeCode().equals(effective))
                .map(I18nTranslation::messageText).findFirst().orElseGet(() -> translations.stream()
                        .filter(item -> item.localeCode().equals(base))
                        .map(I18nTranslation::messageText).findFirst().orElse(stableKey));
        return new MessageFormat(pattern, Locale.forLanguageTag(effective)).format(args);
    }
}
