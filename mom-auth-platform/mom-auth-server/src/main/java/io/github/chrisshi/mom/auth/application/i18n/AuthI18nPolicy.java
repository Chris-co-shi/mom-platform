package io.github.chrisshi.mom.auth.application.i18n;

import io.github.chrisshi.mom.i18n.namespace.I18nNamespacePolicy;
import io.github.chrisshi.mom.i18n.runtime.I18nLocalePolicy;
import io.github.chrisshi.mom.i18n.runtime.LocalBaseLocalePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Auth 自有 namespace 和本地基础语言策略。
 *
 * <p>只接受 auth 与 auth.*，不读取 System 数据库或在请求链同步调用 System。
 * 配置在启动时固定，线程安全；非法 Locale 回退本地基础语言。</p>
 */
@Component
public final class AuthI18nPolicy implements I18nNamespacePolicy, I18nLocalePolicy {
    private final LocalBaseLocalePolicy locales;

    /** @param baseLocale Auth 独立部署的基础语言 */
    public AuthI18nPolicy(@Value("${mom.i18n.base-locale:zh-CN}") String baseLocale) {
        this.locales = new LocalBaseLocalePolicy(baseLocale);
    }

    /** {@inheritDoc} */
    @Override
    public boolean owns(String namespace) {
        return "auth".equals(namespace) || namespace.startsWith("auth.");
    }

    /** {@inheritDoc} */
    @Override
    public String requireLocale(String localeCode) {
        return locales.requireLocale(localeCode);
    }

    /** {@inheritDoc} */
    @Override
    public String effectiveLocale(String requestedLocale) {
        return locales.effectiveLocale(requestedLocale);
    }

    /** {@inheritDoc} */
    @Override
    public String baseLocale() {
        return locales.baseLocale();
    }
}
