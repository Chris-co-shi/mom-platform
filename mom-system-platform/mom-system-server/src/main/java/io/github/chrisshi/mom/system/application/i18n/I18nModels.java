package io.github.chrisshi.mom.system.application.i18n;

import java.time.Instant;

/**
 * SupportedLocale 管理用例的命令和 View。
 *
 * <p>类型位于 Application 边界，不暴露 Mapper Wrapper 或数据库异常。</p>
 */
public final class I18nModels {
    private I18nModels() {
    }

    /** 创建非默认 SupportedLocale 命令。 */
    public record CreateLocale(String localeCode, String displayName, String nativeName,
                               Boolean enabled, Integer sortOrder) {
    }

    /** 更新 Locale 展示信息命令；localeCode 不可修改。 */
    public record UpdateLocale(String displayName, String nativeName, Integer sortOrder, Long version) {
    }

    /** 版本化启停命令。 */
    public record ChangeLocaleStatus(Boolean enabled, Long version) {
    }

    /** SupportedLocale 管理视图。 */
    public record LocaleView(String id, String localeCode, String displayName, String nativeName,
                             boolean enabled, boolean defaultLocale, int sortOrder, long version,
                             Instant updatedAt) {
    }

    /** 请求 Locale 经过存在性/启停检查后的回退结果。 */
    public record LocaleResolution(String requestedLocale, String effectiveLocale, String defaultLocale) {
    }

}
