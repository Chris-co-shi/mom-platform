package io.github.chrisshi.mom.mdm.application.i18n;

import io.github.chrisshi.mom.i18n.namespace.I18nNamespacePolicy;
import io.github.chrisshi.mom.i18n.runtime.I18nLocalePolicy;
import io.github.chrisshi.mom.i18n.runtime.LocalBaseLocalePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MDM 自有 namespace 和本地基础语言策略。
 *
 * <p>只接受 mdm 与 mdm.*，不跨 Schema 或同步调用 System。配置在启动时固定且线程安全；
 * 请求 Locale 不合法时回退基础语言，数据库故障直接上抛。</p>
 */
@Component
public final class MdmI18nPolicy implements I18nNamespacePolicy, I18nLocalePolicy {
    private final LocalBaseLocalePolicy locales;

    /** @param baseLocale MDM 独立部署的基础语言 */
    public MdmI18nPolicy(@Value("${mom.i18n.base-locale:zh-CN}") String baseLocale) {
        this.locales = new LocalBaseLocalePolicy(baseLocale);
    }

    /** {@inheritDoc} */
    @Override
    public boolean owns(String namespace) {
        return "mdm".equals(namespace) || namespace.startsWith("mdm.");
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
