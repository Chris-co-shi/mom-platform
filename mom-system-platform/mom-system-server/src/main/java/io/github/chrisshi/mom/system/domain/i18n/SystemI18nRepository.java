package io.github.chrisshi.mom.system.domain.i18n;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dynamic I18n 聚合的持久化 Port。
 *
 * <p>Application 只依赖此契约，Infrastructure 负责 PostgreSQL SQL、行锁、JSONB 转换与数据库异常脱敏。
 * Release 只暴露 Insert/Read，不提供 Update/Delete。所有写操作必须由 Application 公共方法包裹在 System
 * 单 PostgreSQL 本地事务内；数据库不可用时异常向上失败，不缓存或拼装旧版本。</p>
 */
public interface SystemI18nRepository {
    /** 插入新资源并返回服务端 ID/审计后的值。 */
    Resource insertResource(Resource resource);

    /** 使用资源 Version CAS 更新可变字段与发布指针。 */
    boolean updateResource(Resource resource);

    /** 按 System 内部 ID 查找资源。 */
    Optional<Resource> findResourceById(String id);

    /** 按稳定 applicationCode/resourceCode 查找唯一资源。 */
    Optional<Resource> findResourceByCodes(String applicationCode, String resourceCode);

    /** 在当前事务中以行锁读取资源，串行化发布和回滚。 */
    Optional<Resource> lockResource(String id);

    /** 按受控条件与固定排序分页资源。 */
    Page<Resource> findResources(String applicationCode, Boolean enabled, int page, int size);

    /** 插入新 Draft 并返回服务端 ID/审计后的值。 */
    Message insertMessage(Message message);

    /** 使用 Draft Version CAS 更新文本、状态与说明。 */
    boolean updateMessage(Message message);

    /** 按父资源与内部 ID 查找 Draft。 */
    Optional<Message> findMessage(String resourceId, String messageId);

    /** 返回发布构建所需的全部启用 Draft，排序固定。 */
    List<Message> findEnabledMessages(String resourceId);

    /** 按受控条件与固定排序分页 Draft。 */
    Page<Message> findMessages(String resourceId, String messageKey, String locale, Boolean enabled,
                               int page, int size);

    /** 在调用方已持有资源行锁时分配下一单调发布版本。 */
    long nextReleaseVersion(String resourceId);

    /** 只追加单 Locale Release；不得实现为 Upsert。 */
    void insertRelease(Release release);

    /** 读取同一资源、同一版本的全部 Locale Release。 */
    List<Release> findRelease(String resourceId, long releaseVersion);

    /** 按版本倒序分页发布历史摘要。 */
    Page<ReleaseSummary> findReleaseHistory(String resourceId, int page, int size);

    /** 资源头部，稳定 Code/defaultLocale 创建后不可修改。 */
    record Resource(
            String id, String applicationCode, String resourceCode, String resourceName, String defaultLocale,
            boolean enabled, Long publishedVersion, String publishedBy, Instant publishedAt, long version,
            String description, String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {
    }

    /** 可编辑草稿消息；resourceId/messageKey/locale 创建后不可修改。 */
    record Message(
            String id, String resourceId, String messageKey, String locale, String messageValue,
            boolean enabled, long version, String description, String createdBy, Instant createdAt,
            String updatedBy, Instant updatedAt) {
    }

    /** 追加写的单 Locale 发布快照。 */
    record Release(
            String resourceId, long releaseVersion, String locale, Map<String, String> messages,
            String messagesJson, int messageCount, int fallbackCount, String checksum,
            Long sourceReleaseVersion, String changeNote, String publishedBy, Instant publishedAt) {
    }

    /** 每个版本只返回一次的历史摘要。 */
    record ReleaseSummary(
            long releaseVersion, Long sourceReleaseVersion, String changeNote, String publishedBy,
            Instant publishedAt, int localeCount) {
    }

    /** 固定页号、页大小与总数的只读结果。 */
    record Page<T>(List<T> items, long total, int page, int size) {
    }
}
