package io.github.chrisshi.mom.i18n.runtime;

/**
 * Runtime Locale 回退与管理写入校验的宿主策略。
 *
 * <p>System 可以检查本地 SupportedLocale；其他 Owner 只校验 BCP 47 格式，
 * 并使用本地配置的 base Locale。调用不得每次远程请求 System。</p>
 */
public interface I18nLocalePolicy {
    /** @return 规范化的可保存 BCP 47 Locale；非法输入抛出 IllegalArgumentException */
    String requireLocale(String localeCode);

    /** @return 请求 Locale 实际可使用的 Locale；不支持时回退 base */
    String effectiveLocale(String requestedLocale);

    /** @return 本服务配置的 base/default Locale */
    String baseLocale();
}
