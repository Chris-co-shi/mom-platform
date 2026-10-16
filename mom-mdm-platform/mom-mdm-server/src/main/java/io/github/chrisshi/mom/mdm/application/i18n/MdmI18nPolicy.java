package io.github.chrisshi.mom.mdm.application.i18n;

import io.github.chrisshi.mom.i18n.runtime.I18nLocalePolicy;
import io.github.chrisshi.mom.i18n.runtime.LocalBaseLocalePolicy;
import org.springframework.stereotype.Component;

/**
 * MDM 本地基础语言策略。
 *
 * <p>只接受 mdm 与 mdm.*，不跨 Schema 或同步调用 System。配置在启动时固定且线程安全；
 * 请求 Locale 不合法时回退基础语言，数据库故障直接上抛。</p>
 */
@Component
public final class MdmI18nPolicy implements I18nLocalePolicy {
    private final LocalBaseLocalePolicy locales;

    /** 使用 zh-CN 作为服务端缺失译文的固定技术兜底，不代表 System 动态平台默认语言。 */
    public MdmI18nPolicy() {
        this.locales = new LocalBaseLocalePolicy("zh-CN");
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
