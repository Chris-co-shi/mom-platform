package io.github.chrisshi.mom.system.web.i18n;

import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.CreateMessageCommand;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.CreateResourceCommand;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.MessagePageQuery;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.MessageView;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.PageView;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.PublishCommand;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.PublishView;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.ReleaseHistoryView;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.ResourcePageQuery;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.ResourceView;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.RollbackCommand;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.RuntimeView;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.StatusCommand;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.UpdateMessageCommand;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationModels.UpdateResourceCommand;
import io.github.chrisshi.mom.system.application.i18n.SystemI18nApplicationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dynamic I18n 管理与运行时 HTTP 入站 Adapter。
 *
 * <p>Controller 只映射 HTTP 与 Application Command/Query，不访问 Domain、Repository 或数据库。
 * 管理读取、写入、发布分别引用 IAM Authority {@code system:i18n:read/write/publish}；Runtime V1 仅要求
 *已认证，不允许匿名。ETag 由不可变 Release checksum 生成，匹配 If-None-Match 时返回无 Body 的 304。
 * System 不解析 JWT，也不保存或分配 Permission。</p>
 */
@RestController
@RequestMapping("/api/system")
public class SystemI18nController {
    private final SystemI18nApplicationService service;

    public SystemI18nController(SystemI18nApplicationService service) {
        this.service = service;
    }

    /** 创建稳定双 Code 与默认 Locale 的资源。 */
    @PostMapping("/admin/i18n/resources")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:i18n:write')")
    public ResourceView createResource(@RequestBody CreateResourceRequest request) {
        return service.createResource(new CreateResourceCommand(request.applicationCode(), request.resourceCode(),
                request.resourceName(), request.defaultLocale(), request.description(), request.enabled()));
    }

    /** 更新资源名称和说明，不允许修改稳定 Code/defaultLocale。 */
    @PutMapping("/admin/i18n/resources/{id}")
    @PreAuthorize("hasAuthority('system:i18n:write')")
    public ResourceView updateResource(@PathVariable String id, @RequestBody UpdateResourceRequest request) {
        return service.updateResource(id,
                new UpdateResourceCommand(request.resourceName(), request.description(), request.version()));
    }

    /** 使用乐观版本启停资源 Kill Switch。 */
    @PatchMapping("/admin/i18n/resources/{id}/status")
    @PreAuthorize("hasAuthority('system:i18n:write')")
    public ResourceView changeResourceStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        return service.changeResourceStatus(id, new StatusCommand(request.enabled(), request.version()));
    }

    /** 读取单个资源管理视图。 */
    @GetMapping("/admin/i18n/resources/{id}")
    @PreAuthorize("hasAuthority('system:i18n:read')")
    public ResourceView getResource(@PathVariable String id) {
        return service.getResource(id);
    }

    /** 按 applicationCode/状态精确过滤资源分页。 */
    @GetMapping("/admin/i18n/resources")
    @PreAuthorize("hasAuthority('system:i18n:read')")
    public PageView<ResourceView> pageResources(
            @RequestParam(required = false) String applicationCode,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.pageResources(new ResourcePageQuery(applicationCode, enabled, page, size));
    }

    /** 在资源下创建不可 Rename/换 Locale 的 Draft。 */
    @PostMapping("/admin/i18n/resources/{resourceId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:i18n:write')")
    public MessageView createMessage(
            @PathVariable String resourceId, @RequestBody CreateMessageRequest request) {
        return service.createMessage(resourceId, new CreateMessageCommand(request.messageKey(), request.locale(),
                request.messageValue(), request.description(), request.enabled()));
    }

    /** 更新 Draft 普通文本和说明，不影响当前发布版本。 */
    @PutMapping("/admin/i18n/resources/{resourceId}/messages/{messageId}")
    @PreAuthorize("hasAuthority('system:i18n:write')")
    public MessageView updateMessage(
            @PathVariable String resourceId, @PathVariable String messageId,
            @RequestBody UpdateMessageRequest request) {
        return service.updateMessage(resourceId, messageId,
                new UpdateMessageCommand(request.messageValue(), request.description(), request.version()));
    }

    /** 启停 Draft；状态仅在下一次 Publish 生效。 */
    @PatchMapping("/admin/i18n/resources/{resourceId}/messages/{messageId}/status")
    @PreAuthorize("hasAuthority('system:i18n:write')")
    public MessageView changeMessageStatus(
            @PathVariable String resourceId, @PathVariable String messageId,
            @RequestBody StatusRequest request) {
        return service.changeMessageStatus(resourceId, messageId,
                new StatusCommand(request.enabled(), request.version()));
    }

    /** 读取单个 Draft 管理视图。 */
    @GetMapping("/admin/i18n/resources/{resourceId}/messages/{messageId}")
    @PreAuthorize("hasAuthority('system:i18n:read')")
    public MessageView getMessage(@PathVariable String resourceId, @PathVariable String messageId) {
        return service.getMessage(resourceId, messageId);
    }

    /** 按 Key、Locale、状态精确过滤 Draft 分页。 */
    @GetMapping("/admin/i18n/resources/{resourceId}/messages")
    @PreAuthorize("hasAuthority('system:i18n:read')")
    public PageView<MessageView> pageMessages(
            @PathVariable String resourceId,
            @RequestParam(required = false) String messageKey,
            @RequestParam(required = false) String locale,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.pageMessages(resourceId,
                new MessagePageQuery(messageKey, locale, enabled, page, size));
    }

    /** 显式发布全部启用 Draft。 */
    @PostMapping("/admin/i18n/resources/{resourceId}/publish")
    @PreAuthorize("hasAuthority('system:i18n:publish')")
    public PublishView publish(@PathVariable String resourceId, @RequestBody PublishRequest request) {
        return service.publish(resourceId, new PublishCommand(request.version(), request.changeNote()));
    }

    /** 复制目标历史版本为新的单调版本，Draft 不改变。 */
    @PostMapping("/admin/i18n/resources/{resourceId}/rollback")
    @PreAuthorize("hasAuthority('system:i18n:publish')")
    public PublishView rollback(@PathVariable String resourceId, @RequestBody RollbackRequest request) {
        return service.rollback(resourceId, new RollbackCommand(
                request.targetReleaseVersion(), request.version(), request.changeNote()));
    }

    /** 查询版本级发布历史，不返回消息正文。 */
    @GetMapping("/admin/i18n/resources/{resourceId}/releases")
    @PreAuthorize("hasAuthority('system:i18n:read')")
    public PageView<ReleaseHistoryView> releaseHistory(
            @PathVariable String resourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.releaseHistory(resourceId, page, size);
    }

    /**
     * 返回当前完整发布快照；ETag 匹配时返回 304 且无 Body。
     *
     * @param applicationCode 稳定应用 Code
     * @param resourceCode 应用内稳定资源 Code
     * @param locale V1 请求 Locale
     * @param ifNoneMatch 可选缓存校验头
     * @return 200 Runtime View 或 304 空响应
     */
    @GetMapping("/i18n/applications/{applicationCode}/resources/{resourceCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RuntimeView> runtime(
            @PathVariable String applicationCode,
            @PathVariable String resourceCode,
            @RequestParam String locale,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        RuntimeView view = service.runtime(applicationCode, resourceCode, locale);
        String etag = "\"" + view.checksum() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).header(HttpHeaders.ETAG, etag).build();
        }
        return ResponseEntity.ok().header(HttpHeaders.ETAG, etag).body(view);
    }

    /** 创建资源请求，不接受 ID、发布或审计字段。 */
    public record CreateResourceRequest(String applicationCode, String resourceCode, String resourceName,
                                        String defaultLocale, String description, Boolean enabled) {
    }

    /** 更新资源请求，不接受不可变 Code/defaultLocale。 */
    public record UpdateResourceRequest(String resourceName, String description, Long version) {
    }

    /** 创建 Draft 请求，resourceId 只来自路径。 */
    public record CreateMessageRequest(String messageKey, String locale, String messageValue,
                                       String description, Boolean enabled) {
    }

    /** 更新 Draft 请求，不接受不可变 Key/Locale/resourceId。 */
    public record UpdateMessageRequest(String messageValue, String description, Long version) {
    }

    /** Resource 与 Draft 共用的版本化启停请求。 */
    public record StatusRequest(Boolean enabled, Long version) {
    }

    /** 显式发布请求。 */
    public record PublishRequest(Long version, String changeNote) {
    }

    /** 创建新版本的回滚请求。 */
    public record RollbackRequest(Long targetReleaseVersion, Long version, String changeNote) {
    }
}
