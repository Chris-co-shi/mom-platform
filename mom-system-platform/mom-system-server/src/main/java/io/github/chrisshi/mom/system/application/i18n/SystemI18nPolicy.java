package io.github.chrisshi.mom.system.application.i18n;

import io.github.chrisshi.mom.i18n.namespace.I18nNamespacePolicy;
import io.github.chrisshi.mom.i18n.runtime.I18nLocalePolicy;
import io.github.chrisshi.mom.i18n.validation.I18nRules;
import org.springframework.stereotype.Component;

/**
 * System 的 namespace 所有权与本地 SupportedLocale 策略。
 *
 * <p>System 独有平台支持语言和默认项，因而 Runtime 在本地查询 System 表即可确定有效 Locale；
 * 其他 Owner 不依赖此类或远程请求 System。该类无可变共享状态，数据库不可用时读取失败。</p>
 */
@Component
public class SystemI18nPolicy implements I18nNamespacePolicy, I18nLocalePolicy {
    private final SupportedLocaleApplication locales;

    /** @param locales System 自有 SupportedLocale 用例 */
    public SystemI18nPolicy(SupportedLocaleApplication locales) {
        this.locales = locales;
    }

    /** {@inheritDoc} */
    @Override
    public boolean owns(String namespace) {
        return "system".equals(namespace) || namespace.startsWith("system.");
    }

    /** {@inheritDoc} */
    @Override
    public String requireLocale(String localeCode) {
        return locales.requireLocale(I18nRules.localeCode(localeCode)).getLocaleCode();
    }

    /** {@inheritDoc} */
    @Override
    public String effectiveLocale(String requestedLocale) {
        if (requestedLocale == null || requestedLocale.isBlank()) return baseLocale();
        return locales.resolve(requestedLocale).effectiveLocale();
    }

    /** {@inheritDoc} */
    @Override
    public String baseLocale() {
        return locales.supportedLocales().stream().filter(locale -> locale.defaultLocale())
                .map(locale -> locale.localeCode()).findFirst()
                .orElseThrow(() -> new IllegalStateException("System 缺少默认 Locale"));
    }
}
