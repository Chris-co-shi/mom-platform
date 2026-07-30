package io.github.chrisshi.mom.system.application.i18n;

import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.system.domain.i18n.SystemI18nRepository;
import io.github.chrisshi.mom.system.domain.i18n.SystemI18nRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.CreateMessageCommand;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.CreateResourceCommand;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.MessagePageQuery;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.MessageView;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.PageView;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.PublishCommand;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.PublishView;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.ReleaseHistoryView;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.ResourcePageQuery;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.ResourceView;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.RollbackCommand;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.RuntimeView;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.StatusCommand;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.UpdateMessageCommand;
import static io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.UpdateResourceCommand;

/**
 * Dynamic I18n 的管理、发布、运行时读取与回滚用例服务。
 *
 * <p>所有写用例使用 System 唯一 PostgreSQL 本地事务。发布和回滚先通过 {@code SELECT FOR UPDATE}
 * 串行化单 Resource，再原子写入 zh-CN/en-US 两行不可变 Release 并更新资源头；不使用 JVM 锁、Redis、
 * MQ 或 Seata。运行时只读取资源当前版本的完整快照，版本不完整时 Fail Closed 为 404，绝不跨版本拼接。
 * 数据库不可用时异常向上失败，不返回 Draft 或伪造旧数据。</p>
 */
@Service
public class SystemI18nApplicationService {
    private static final int MAX_PAGE_SIZE = 100;
    private final SystemI18nRepository repository;
    private final CurrentActorProvider actorProvider;
    private final Clock clock;

    public SystemI18nApplicationService(
            SystemI18nRepository repository, CurrentActorProvider actorProvider, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.actorProvider = Objects.requireNonNull(actorProvider, "actorProvider");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 创建稳定 applicationCode/resourceCode/defaultLocale 的资源。 */
    @Transactional
    public ResourceView createResource(CreateResourceCommand command) {
        Objects.requireNonNull(command, "command");
        var value = new SystemI18nRepository.Resource(null,
                SystemI18nRules.normalizeApplicationCode(command.applicationCode()),
                SystemI18nRules.normalizeResourceCode(command.resourceCode()),
                SystemI18nRules.normalizeResourceName(command.resourceName()),
                SystemI18nRules.requireLocale(command.defaultLocale()), command.enabled() == null || command.enabled(),
                null, null, null, 0, SystemI18nRules.normalizeDescription(command.description()),
                null, null, null, null);
        return ResourceView.from(repository.insertResource(value));
    }

    /** 使用乐观版本更新可变名称与说明。 */
    @Transactional
    public ResourceView updateResource(String id, UpdateResourceCommand command) {
        Objects.requireNonNull(command, "command");
        requireVersion(command.version());
        var current = requireResource(id);
        var changed = copyResource(current, SystemI18nRules.normalizeResourceName(command.resourceName()),
                current.enabled(), current.publishedVersion(), current.publishedBy(), current.publishedAt(),
                SystemI18nRules.normalizeDescription(command.description()));
        updateResourceOrStale(changed);
        return ResourceView.from(requireResource(current.id()));
    }

    /** 使用乐观版本启停运行时 Kill Switch；重新启用不改变最近发布版本。 */
    @Transactional
    public ResourceView changeResourceStatus(String id, StatusCommand command) {
        Objects.requireNonNull(command, "command");
        requireVersion(command.version());
        requireEnabled(command.enabled());
        var current = requireResource(id);
        updateResourceOrStale(copyResource(current, current.resourceName(), command.enabled(),
                current.publishedVersion(), current.publishedBy(), current.publishedAt(), current.description()));
        return ResourceView.from(requireResource(current.id()));
    }

    /** 按内部 ID 读取资源管理视图。 */
    @Transactional(readOnly = true)
    public ResourceView getResource(String id) {
        return ResourceView.from(requireResource(id));
    }

    /** 按 applicationCode/状态精确过滤并固定排序分页。 */
    @Transactional(readOnly = true)
    public PageView<ResourceView> pageResources(ResourcePageQuery query) {
        Objects.requireNonNull(query, "query");
        requirePage(query.page(), query.size());
        String applicationCode = query.applicationCode() == null || query.applicationCode().isBlank()
                ? null : SystemI18nRules.normalizeApplicationCode(query.applicationCode());
        var page = repository.findResources(applicationCode, query.enabled(), query.page(), query.size());
        return new PageView<>(page.items().stream().map(ResourceView::from).toList(),
                page.total(), page.page(), page.size());
    }

    /** 在资源下创建稳定 messageKey/locale 的 Draft。 */
    @Transactional
    public MessageView createMessage(String resourceId, CreateMessageCommand command) {
        Objects.requireNonNull(command, "command");
        var resource = requireResource(resourceId);
        String messageValue = command.messageValue();
        SystemI18nRules.validateMessageValue(messageValue);
        var value = new SystemI18nRepository.Message(null, resource.id(),
                SystemI18nRules.requireMessageKey(command.messageKey()),
                SystemI18nRules.requireLocale(command.locale()), messageValue,
                command.enabled() == null || command.enabled(), 0,
                SystemI18nRules.normalizeDescription(command.description()), null, null, null, null);
        return MessageView.from(repository.insertMessage(value));
    }

    /** 使用乐观版本更新 Draft 文本与说明，不影响已发布快照。 */
    @Transactional
    public MessageView updateMessage(String resourceId, String messageId, UpdateMessageCommand command) {
        Objects.requireNonNull(command, "command");
        requireVersion(command.version());
        SystemI18nRules.validateMessageValue(command.messageValue());
        var current = requireMessage(resourceId, messageId);
        var changed = copyMessage(current, command.messageValue(), current.enabled(),
                SystemI18nRules.normalizeDescription(command.description()));
        updateMessageOrStale(changed);
        return MessageView.from(requireMessage(current.resourceId(), current.id()));
    }

    /** 使用乐观版本启停 Draft；仅下一次 Publish 生效。 */
    @Transactional
    public MessageView changeMessageStatus(String resourceId, String messageId, StatusCommand command) {
        Objects.requireNonNull(command, "command");
        requireVersion(command.version());
        requireEnabled(command.enabled());
        var current = requireMessage(resourceId, messageId);
        updateMessageOrStale(copyMessage(current, current.messageValue(), command.enabled(), current.description()));
        return MessageView.from(requireMessage(current.resourceId(), current.id()));
    }

    /** 按父资源与内部 ID 读取 Draft 管理视图。 */
    @Transactional(readOnly = true)
    public MessageView getMessage(String resourceId, String messageId) {
        return MessageView.from(requireMessage(resourceId, messageId));
    }

    /** 按 Key、Locale 和状态精确过滤 Draft 分页。 */
    @Transactional(readOnly = true)
    public PageView<MessageView> pageMessages(String resourceId, MessagePageQuery query) {
        Objects.requireNonNull(query, "query");
        requirePage(query.page(), query.size());
        String id = requireResource(resourceId).id();
        String key = query.messageKey() == null || query.messageKey().isBlank()
                ? null : SystemI18nRules.requireMessageKey(query.messageKey());
        String locale = query.locale() == null || query.locale().isBlank()
                ? null : SystemI18nRules.requireLocale(query.locale());
        var page = repository.findMessages(id, key, locale, query.enabled(), query.page(), query.size());
        return new PageView<>(page.items().stream().map(MessageView::from).toList(),
                page.total(), page.page(), page.size());
    }

    /**
     * 原子发布全部启用 Draft 为两个 Locale 的完整不可变快照。
     *
     * @param resourceId 资源内部 ID
     * @param command 客户端资源 Version 与变更说明
     * @return 新单调发布版本及两个校验和
     * @throws SystemI18nException.Conflict 禁用、No-op 或发布状态冲突
     */
    @Transactional
    public PublishView publish(String resourceId, PublishCommand command) {
        Objects.requireNonNull(command, "command");
        requireVersion(command.version());
        String changeNote = requireChangeNote(command.changeNote());
        var resource = lockResource(resourceId);
        requireExpectedVersion(resource, command.version());
        if (!resource.enabled()) {
            throw new SystemI18nException.Conflict("禁用 Resource 不允许发布");
        }
        List<SystemI18nRules.DraftValue> drafts = repository.findEnabledMessages(resource.id()).stream()
                .map(message -> new SystemI18nRules.DraftValue(
                        message.messageKey(), message.locale(), message.messageValue()))
                .toList();
        Map<String, SystemI18nRules.Snapshot> snapshots =
                SystemI18nRules.buildSnapshots(resource.defaultLocale(), drafts);
        rejectNoOp(resource, snapshots);
        return appendRelease(resource, snapshots, null, changeNote);
    }

    /** 创建新单调版本并复制目标历史内容；Draft 保持不变。 */
    @Transactional
    public PublishView rollback(String resourceId, RollbackCommand command) {
        Objects.requireNonNull(command, "command");
        requireVersion(command.version());
        if (command.targetReleaseVersion() == null || command.targetReleaseVersion() < 1) {
            throw new IllegalArgumentException("targetReleaseVersion 必须大于 0");
        }
        String changeNote = requireChangeNote(command.changeNote());
        var resource = lockResource(resourceId);
        requireExpectedVersion(resource, command.version());
        if (!resource.enabled()) {
            throw new SystemI18nException.Conflict("禁用 Resource 不允许回滚");
        }
        List<SystemI18nRepository.Release> target = completeRelease(
                resource.id(), command.targetReleaseVersion());
        Map<String, SystemI18nRules.Snapshot> snapshots = new LinkedHashMap<>();
        for (var release : target) {
            snapshots.put(release.locale(), SystemI18nRules.snapshot(release.messages(), release.fallbackCount()));
        }
        rejectNoOp(resource, snapshots);
        return appendRelease(resource, snapshots, command.targetReleaseVersion(), changeNote);
    }

    /** 返回按 releaseVersion 倒序的版本级历史，不返回消息正文。 */
    @Transactional(readOnly = true)
    public PageView<ReleaseHistoryView> releaseHistory(String resourceId, int page, int size) {
        requirePage(page, size);
        String id = requireResource(resourceId).id();
        var result = repository.findReleaseHistory(id, page, size);
        return new PageView<>(result.items().stream().map(item -> new ReleaseHistoryView(
                item.releaseVersion(), item.sourceReleaseVersion(), item.changeNote(), item.publishedBy(),
                item.publishedAt(), item.localeCount())).toList(), result.total(), result.page(), result.size());
    }

    /**
     * 读取已启用资源当前版本的完整单 Locale Runtime Snapshot。
     *
     * @throws SystemI18nException.NotFound 资源禁用、未发布、不存在或当前版本不完整
     */
    @Transactional(readOnly = true)
    public RuntimeView runtime(String applicationCode, String resourceCode, String locale) {
        String application = SystemI18nRules.normalizeApplicationCode(applicationCode);
        String resourceValue = SystemI18nRules.normalizeResourceCode(resourceCode);
        String requestedLocale = SystemI18nRules.requireLocale(locale);
        var resource = repository.findResourceByCodes(application, resourceValue)
                .filter(SystemI18nRepository.Resource::enabled)
                .filter(value -> value.publishedVersion() != null)
                .orElseThrow(() -> new SystemI18nException.NotFound("I18n Resource 不存在"));
        List<SystemI18nRepository.Release> releases = completeRelease(resource.id(), resource.publishedVersion());
        var selected = releases.stream().filter(release -> release.locale().equals(requestedLocale)).findFirst()
                .orElseThrow(() -> new SystemI18nException.NotFound("当前发布版本不完整"));
        return new RuntimeView(resource.applicationCode(), resource.resourceCode(), requestedLocale,
                resource.defaultLocale(), selected.releaseVersion(), selected.checksum(), selected.fallbackCount(),
                selected.publishedAt(), selected.messages());
    }

    private PublishView appendRelease(
            SystemI18nRepository.Resource resource, Map<String, SystemI18nRules.Snapshot> snapshots,
            Long sourceReleaseVersion, String changeNote) {
        long releaseVersion = repository.nextReleaseVersion(resource.id());
        String actor = actorProvider.requireCurrentActor().actorId();
        Instant now = clock.instant();
        for (String locale : List.of(SystemI18nRules.ZH_CN, SystemI18nRules.EN_US)) {
            var snapshot = snapshots.get(locale);
            if (snapshot == null) {
                throw new IllegalStateException("发布快照缺少 Locale: " + locale);
            }
            repository.insertRelease(new SystemI18nRepository.Release(resource.id(), releaseVersion, locale,
                    snapshot.messages(), snapshot.json(), snapshot.messageCount(), snapshot.fallbackCount(),
                    snapshot.checksum(), sourceReleaseVersion, changeNote, actor, now));
        }
        updateResourceOrStale(copyResource(resource, resource.resourceName(), resource.enabled(),
                releaseVersion, actor, now, resource.description()));
        Map<String, String> checksums = new LinkedHashMap<>();
        snapshots.forEach((locale, snapshot) -> checksums.put(locale, snapshot.checksum()));
        return new PublishView(releaseVersion, sourceReleaseVersion, actor, now, Map.copyOf(checksums));
    }

    private void rejectNoOp(
            SystemI18nRepository.Resource resource, Map<String, SystemI18nRules.Snapshot> snapshots) {
        if (resource.publishedVersion() == null) {
            return;
        }
        List<SystemI18nRepository.Release> current = completeRelease(resource.id(), resource.publishedVersion());
        boolean same = current.stream().allMatch(release -> {
            var candidate = snapshots.get(release.locale());
            return candidate != null
                    && candidate.checksum().equals(release.checksum())
                    && candidate.fallbackCount() == release.fallbackCount();
        });
        if (same) {
            throw new SystemI18nException.Conflict("发布内容与当前版本相同");
        }
    }

    private List<SystemI18nRepository.Release> completeRelease(String resourceId, long releaseVersion) {
        List<SystemI18nRepository.Release> releases = repository.findRelease(resourceId, releaseVersion);
        boolean complete = releases.size() == 2
                && releases.stream().map(SystemI18nRepository.Release::locale).distinct().count() == 2
                && releases.stream().allMatch(release -> SystemI18nRules.SUPPORTED_LOCALES.contains(release.locale()));
        if (!complete) {
            throw new SystemI18nException.NotFound("发布版本不存在或不完整");
        }
        return releases;
    }

    private SystemI18nRepository.Resource requireResource(String id) {
        return repository.findResourceById(requireId(id))
                .orElseThrow(() -> new SystemI18nException.NotFound("I18n Resource 不存在"));
    }

    private SystemI18nRepository.Resource lockResource(String id) {
        return repository.lockResource(requireId(id))
                .orElseThrow(() -> new SystemI18nException.NotFound("I18n Resource 不存在"));
    }

    private SystemI18nRepository.Message requireMessage(String resourceId, String messageId) {
        return repository.findMessage(requireId(resourceId), requireId(messageId))
                .orElseThrow(() -> new SystemI18nException.NotFound("Draft Message 不存在"));
    }

    private void updateResourceOrStale(SystemI18nRepository.Resource value) {
        if (!repository.updateResource(value)) {
            throw new SystemI18nException.StaleVersion("I18n Resource 已被其他请求修改");
        }
    }

    private void updateMessageOrStale(SystemI18nRepository.Message value) {
        if (!repository.updateMessage(value)) {
            throw new SystemI18nException.StaleVersion("Draft Message 已被其他请求修改");
        }
    }

    private static SystemI18nRepository.Resource copyResource(
            SystemI18nRepository.Resource current, String name, boolean enabled, Long publishedVersion,
            String publishedBy, Instant publishedAt, String description) {
        return new SystemI18nRepository.Resource(current.id(), current.applicationCode(), current.resourceCode(),
                name, current.defaultLocale(), enabled, publishedVersion, publishedBy, publishedAt,
                current.version(), description, current.createdBy(), current.createdAt(), current.updatedBy(),
                current.updatedAt());
    }

    private static SystemI18nRepository.Message copyMessage(
            SystemI18nRepository.Message current, String value, boolean enabled, String description) {
        return new SystemI18nRepository.Message(current.id(), current.resourceId(), current.messageKey(),
                current.locale(), value, enabled, current.version(), description, current.createdBy(),
                current.createdAt(), current.updatedBy(), current.updatedAt());
    }

    private static void requireExpectedVersion(SystemI18nRepository.Resource resource, long expected) {
        if (resource.version() != expected) {
            throw new SystemI18nException.StaleVersion("I18n Resource 已被其他请求修改");
        }
    }

    private static String requireChangeNote(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("changeNote 不能为空");
        }
        return SystemI18nRules.normalizeDescription(value);
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank() || id.trim().length() > 19) {
            throw new IllegalArgumentException("id 必须是 1～19 位字符串");
        }
        return id.trim();
    }

    private static void requireVersion(Long version) {
        if (version == null || version < 0) {
            throw new IllegalArgumentException("version 不能小于 0");
        }
    }

    private static void requireEnabled(Boolean enabled) {
        if (enabled == null) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
    }

    private static void requirePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page/size 超出范围");
        }
    }
}
