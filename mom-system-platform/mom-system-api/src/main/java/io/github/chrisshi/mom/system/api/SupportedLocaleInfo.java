package io.github.chrisshi.mom.system.api;

/**
 * 全平台可支持 Locale 的稳定只读契约。
 *
 * <p>跨服务只引用 BCP 47 {@code localeCode}，不得引用 System 数据库 ID 或建立跨库外键。启用 Locale
 * 表示平台允许选择，不承诺每个业务服务已经完成全部翻译。</p>
 *
 * @param localeCode BCP 47 Locale Tag
 * @param displayName 平台管理显示名
 * @param nativeName Locale 原生语言名称
 * @param enabled 是否允许新请求选择
 * @param defaultLocale 是否为平台唯一默认 Locale
 * @param sortOrder 展示排序
 */
public record SupportedLocaleInfo(
        String localeCode,
        String displayName,
        String nativeName,
        boolean enabled,
        boolean defaultLocale,
        int sortOrder) {
}
