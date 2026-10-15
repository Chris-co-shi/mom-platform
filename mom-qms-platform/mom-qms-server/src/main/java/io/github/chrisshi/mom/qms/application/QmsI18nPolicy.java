package io.github.chrisshi.mom.qms.application;

import io.github.chrisshi.mom.i18n.runtime.I18nLocalePolicy;
import io.github.chrisshi.mom.i18n.runtime.LocalBaseLocalePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * QMS 独立部署时的基础 Locale 策略。
 */
@Component
public final class QmsI18nPolicy implements I18nLocalePolicy {
    private final LocalBaseLocalePolicy locales;

    public QmsI18nPolicy(@Value("${mom.i18n.base-locale:zh-CN}") String baseLocale) {
        this.locales = new LocalBaseLocalePolicy(baseLocale);
    }

    @Override
    public String requireLocale(String localeCode) {
        return locales.requireLocale(localeCode);
    }

    @Override
    public String effectiveLocale(String requestedLocale) {
        return locales.effectiveLocale(requestedLocale);
    }

    @Override
    public String baseLocale() {
        return locales.baseLocale();
    }
}
