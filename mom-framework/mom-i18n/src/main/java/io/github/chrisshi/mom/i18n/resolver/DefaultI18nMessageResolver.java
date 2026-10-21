package io.github.chrisshi.mom.i18n.resolver;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.core.i18n.I18nMessageResolver;
import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
import io.github.chrisshi.mom.i18n.runtime.I18nLocalePolicy;
import io.github.chrisshi.mom.i18n.store.I18nStore;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;

/**
 * 业务错误的单条精确 Key 查询解析器。
 *
 * <p>Owner 已由本地 Store 确定，完整 Key 不再拆分或推断 namespace。Framework 技术错误继续走
 * classpath；单条业务错误只查询目标消息和至多两种 Locale 译文，数据库故障向上暴露。</p>
 */
@RequiredArgsConstructor
public final class DefaultI18nMessageResolver implements I18nMessageResolver {
    private final I18nStore store;
    private final I18nLocalePolicy locales;
    private final I18nMessageResolver frameworkResolver;

    

    /** {@inheritDoc} */
    @Override
    public String resolve(String messageKey, Locale locale, Object... args) {
        if (messageKey == null || messageKey.isBlank()) throw new IllegalArgumentException("messageKey 不能为空");
        if (messageKey.startsWith("framework.")) return frameworkResolver.resolve(messageKey, locale, args);
        I18nMessage message = store.message(messageKey);
        if (message == null || !message.enabled()) return messageKey;
        String effective = locales.effectiveLocale(locale == null ? null : locale.toLanguageTag());
        String base = locales.baseLocale();
        List<I18nTranslation> translations = store.translations(List.of(message.id()),
                effective.equals(base) ? List.of(base) : List.of(effective, base));
        String pattern = translations.stream().filter(item -> item.localeCode().equals(effective))
                .map(I18nTranslation::messageText).findFirst().orElseGet(() -> translations.stream()
                        .filter(item -> item.localeCode().equals(base))
                        .map(I18nTranslation::messageText).findFirst().orElse(messageKey));
        return new MessageFormat(pattern, Locale.forLanguageTag(effective)).format(args);
    }
}
