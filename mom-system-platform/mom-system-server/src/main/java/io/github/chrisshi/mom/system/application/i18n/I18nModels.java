package io.github.chrisshi.mom.system.application.i18n;

import java.time.Instant;

/**
 * SupportedLocale 与 System I18n 管理用例的命令和 View。
 *
 * <p>类型位于 Application 边界，不暴露 Mapper Wrapper 或数据库异常。Message View 中的技术 ID 仅供
 * System 管理端定位，Runtime Bundle 始终使用 namespace、messageKey 与 localeCode。</p>
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

    /** 创建不可改 Key 的消息定义命令。 */
    public record CreateMessage(String namespace, String messageKey, String description, Boolean enabled) {
    }

    /** 更新消息说明与启停状态命令；namespace/messageKey 不在更新协议中。 */
    public record UpdateMessage(String description, Boolean enabled, Long version) {
    }

    /** 新增或版本化更新 Translation 的命令。 */
    public record SaveTranslation(String messageText, Long version) {
    }

    /** System I18n 消息定义管理视图。 */
    public record MessageView(String id, String namespace, String messageKey, String description,
                              boolean enabled, long version, Instant updatedAt) {
    }

    /** Translation 管理视图。 */
    public record TranslationView(String id, String messageId, String localeCode, String messageText,
                                  long version, Instant updatedAt) {
    }
}
