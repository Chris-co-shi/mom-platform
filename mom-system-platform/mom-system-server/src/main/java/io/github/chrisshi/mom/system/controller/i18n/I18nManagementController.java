package io.github.chrisshi.mom.system.controller.i18n;

import io.github.chrisshi.mom.system.application.i18n.I18nApplication;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.CreateMessage;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.MessageView;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.SaveTranslation;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.TranslationView;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.UpdateMessage;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * System 自有动态 I18n Message/Translation 的管理 HTTP 边界。
 *
 * <p>本 Controller 只允许管理 {@code system.*}，不提供 Framework、MDM、MES、WMS 或 QMS 的中央写入。
 * messageKey 不出现在更新请求中，从协议层保持不可修改；Translation 保存后由 Application 安排
 * after-commit SSE 失效通知。Runtime Bundle 与 SSE 端点由 Framework Controller 统一提供。</p>
 */
@RestController
@RequestMapping("/api/system/admin/i18n/messages")
public class I18nManagementController {
    private final I18nApplication application;

    /** @param application System I18n 管理用例入口 */
    public I18nManagementController(I18nApplication application) {
        this.application = application;
    }

    /** 创建稳定 Message 定义并返回 201。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:i18n:write')")
    public Result<MessageView> createMessage(@RequestBody CreateMessageRequest request) {
        return Result.success(application.createMessage(new CreateMessage(
                request.namespace(), request.messageKey(), request.description(), request.enabled())));
    }

    /** 更新说明与启停状态，不允许 Rename Key。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:i18n:write')")
    public Result<MessageView> updateMessage(@PathVariable String id, @RequestBody UpdateMessageRequest request) {
        return Result.success(application.updateMessage(id,
                new UpdateMessage(request.description(), request.enabled(), request.version())));
    }

    /** 按 System namespace 查询消息定义管理列表。 */
    @GetMapping
    @PreAuthorize("hasAuthority('system:i18n:read')")
    public Result<List<MessageView>> listMessages(@RequestParam String namespace) {
        return Result.success(application.listMessages(namespace));
    }

    /** 新增或版本化更新指定 Locale Translation。 */
    @PutMapping("/{messageId}/translations/{localeCode}")
    @PreAuthorize("hasAuthority('system:i18n:write')")
    public Result<TranslationView> saveTranslation(
            @PathVariable String messageId,
            @PathVariable String localeCode,
            @RequestBody SaveTranslationRequest request) {
        return Result.success(application.saveTranslation(messageId, localeCode,
                new SaveTranslation(request.messageText(), request.version())));
    }

    /** 返回指定 Message 的全部 Translation 管理列表。 */
    @GetMapping("/{messageId}/translations")
    @PreAuthorize("hasAuthority('system:i18n:read')")
    public Result<List<TranslationView>> listTranslations(@PathVariable String messageId) {
        return Result.success(application.listTranslations(messageId));
    }

    /** 创建 Message 请求。 */
    public record CreateMessageRequest(String namespace, String messageKey, String description, Boolean enabled) {
    }

    /** 更新 Message 请求；不含 namespace/messageKey。 */
    public record UpdateMessageRequest(String description, Boolean enabled, Long version) {
    }

    /** 新增或更新 Translation 请求。 */
    public record SaveTranslationRequest(String messageText, Long version) {
    }
}
