package io.github.chrisshi.mom.i18n.runtime;

import io.github.chrisshi.mom.i18n.validation.I18nRules;

/**
 * 非 System Owner 的本地 base Locale 策略。
 *
 * <p>构造后不可变且线程安全，不同步远程 SupportedLocale。请求中非法或未翻译的语言
 * 依赖译文级回退到本地 base Locale，DB 故障不被伪装成缺失译文。</p>
 */
public final class LocalBaseLocalePolicy implements I18nLocalePolicy {
    private final String baseLocale;

    /** @param baseLocale 本地配置的 BCP 47 base Locale */
    public LocalBaseLocalePolicy(String baseLocale) {
        this.baseLocale = I18nRules.localeCode(baseLocale);
    }

    /** {@inheritDoc} */
    @Override
    public String requireLocale(String localeCode) {
        return I18nRules.localeCode(localeCode);
    }

    /** {@inheritDoc} */
    @Override
    public String effectiveLocale(String requestedLocale) {
        try {
            return requireLocale(requestedLocale);
        } catch (IllegalArgumentException exception) {
            return baseLocale;
        }
    }

    /** {@inheritDoc} */
    @Override
    public String baseLocale() {
        return baseLocale;
    }
}
