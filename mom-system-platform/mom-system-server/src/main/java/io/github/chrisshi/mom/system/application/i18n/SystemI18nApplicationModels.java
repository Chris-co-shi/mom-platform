package io.github.chrisshi.mom.system.application.i18n;

import io.github.chrisshi.mom.system.domain.i18n.SystemI18nRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Dynamic I18n Application 的 Command、Query 与 View。
 *
 * <p>管理 View 才包含数据库 ID 与审计；Runtime View 只包含稳定双 Code、Locale、版本、校验和和消息，
 * 不泄露 Draft、数据库 ID 或管理审计。请求模型刻意不接受客户端 Actor、不可变 Code 或发布者。</p>
 */
public final class SystemI18nApplicationModels {
    private SystemI18nApplicationModels() {
    }

    /** 创建稳定资源的命令。 */
    public record CreateResourceCommand(String applicationCode, String resourceCode, String resourceName,
                                        String defaultLocale, String description, Boolean enabled) {
    }

    /** 更新资源可变名称与说明的乐观锁命令。 */
    public record UpdateResourceCommand(String resourceName, String description, Long version) {
    }

    /** Resource/Draft 共用的版本化启停命令。 */
    public record StatusCommand(Boolean enabled, Long version) {
    }

    /** 资源管理分页查询。 */
    public record ResourcePageQuery(String applicationCode, Boolean enabled, int page, int size) {
    }

    /** 创建稳定 Key/Locale Draft 的命令。 */
    public record CreateMessageCommand(String messageKey, String locale, String messageValue,
                                       String description, Boolean enabled) {
    }

    /** 更新 Draft 普通文本与说明的乐观锁命令。 */
    public record UpdateMessageCommand(String messageValue, String description, Long version) {
    }

    /** Draft 管理分页查询。 */
    public record MessagePageQuery(String messageKey, String locale, Boolean enabled, int page, int size) {
    }

    /** 显式发布资源的命令。 */
    public record PublishCommand(Long version, String changeNote) {
    }

    /** 将历史内容复制为新版本的回滚命令。 */
    public record RollbackCommand(Long targetReleaseVersion, Long version, String changeNote) {
    }

    /** 包含内部 ID 与审计的资源管理视图。 */
    public record ResourceView(
            String id, String applicationCode, String resourceCode, String resourceName, String defaultLocale,
            boolean enabled, Long publishedVersion, String publishedBy, Instant publishedAt, long version,
            String description, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
        public static ResourceView from(SystemI18nRepository.Resource value) {
            return new ResourceView(value.id(), value.applicationCode(), value.resourceCode(), value.resourceName(),
                    value.defaultLocale(), value.enabled(), value.publishedVersion(), value.publishedBy(),
                    value.publishedAt(), value.version(), value.description(), value.createdBy(), value.createdAt(),
                    value.updatedBy(), value.updatedAt());
        }
    }

    /** 包含内部 ID 与审计的 Draft 管理视图。 */
    public record MessageView(
            String id, String resourceId, String messageKey, String locale, String messageValue,
            boolean enabled, long version, String description, String createdBy, Instant createdAt,
            String updatedBy, Instant updatedAt) {
        public static MessageView from(SystemI18nRepository.Message value) {
            return new MessageView(value.id(), value.resourceId(), value.messageKey(), value.locale(),
                    value.messageValue(), value.enabled(), value.version(), value.description(), value.createdBy(),
                    value.createdAt(), value.updatedBy(), value.updatedAt());
        }
    }

    /** 通用固定分页视图。 */
    public record PageView<T>(List<T> items, long total, int page, int size) {
    }

    /** 发布或回滚成功后的版本摘要。 */
    public record PublishView(long releaseVersion, Long sourceReleaseVersion, String publishedBy,
                              Instant publishedAt, Map<String, String> checksums) {
    }

    /** 不含消息正文的版本历史视图。 */
    public record ReleaseHistoryView(long releaseVersion, Long sourceReleaseVersion, String changeNote,
                                     String publishedBy, Instant publishedAt, int localeCount) {
    }

    /** 不含数据库 ID 与管理审计的运行时只读视图。 */
    public record RuntimeView(
            String applicationCode, String resourceCode, String requestedLocale, String defaultLocale,
            long releaseVersion, String checksum, int fallbackCount, Instant publishedAt,
            Map<String, String> messages) {
    }
}
