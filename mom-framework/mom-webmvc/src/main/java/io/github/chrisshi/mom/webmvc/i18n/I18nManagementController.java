package io.github.chrisshi.mom.webmvc.i18n;

import io.github.chrisshi.mom.i18n.management.I18nManagementService;
import io.github.chrisshi.mom.i18n.model.I18nMessage;
import io.github.chrisshi.mom.i18n.model.I18nTranslation;
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
 * 各 Servlet Owner 共享的 I18n 管理 HTTP 边界。
 *
 * <p>路径和权限由宿主配置，所有写操作都要求真实 Permission；业务所有权、占位符和事务
 * 由 Framework 用例统一执行。Controller 不依赖 System/Auth/MDM 实体或 Mapper。</p>
 */
@RestController
@RequestMapping("${mom.i18n.management.base-path:/i18n/admin/messages}")
public class I18nManagementController {
    private final I18nManagementService management;

    /** @param management Framework 公共管理用例 */
    public I18nManagementController(I18nManagementService management) {
        this.management = management;
    }

    /** 创建稳定 Message 定义。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@i18nManagementAccess.canWrite(authentication)")
    public Result<I18nMessage> create(@RequestBody CreateRequest request) {
        return Result.success(management.createMessage(request.messageKey(),
                request.description(), request.enabled()));
    }

    /** 版本化更新说明与启停。 */
    @PutMapping("/{id}")
    @PreAuthorize("@i18nManagementAccess.canWrite(authentication)")
    public Result<I18nMessage> update(@PathVariable String id, @RequestBody UpdateRequest request) {
        return Result.success(management.updateMessage(id, request.description(),
                request.enabled(), request.version()));
    }

    /**
     * 查询当前服务 Owner 的全部文案。
     * @return 沿用既有数组响应；读取无写副作用，故障沿用统一异常处理
     */
    @GetMapping
    @PreAuthorize("@i18nManagementAccess.canRead(authentication)")
    public Result<List<I18nMessage>> messages() {
        return Result.success(management.messages());
    }

    /** 新增或版本化保存一个 Locale 译文。 */
    @PutMapping("/{messageId}/translations/{localeCode}")
    @PreAuthorize("@i18nManagementAccess.canWrite(authentication)")
    public Result<I18nTranslation> saveTranslation(@PathVariable String messageId,
                                                    @PathVariable String localeCode,
                                                    @RequestBody TranslationRequest request) {
        return Result.success(management.saveTranslation(messageId, localeCode,
                request.messageText(), request.version()));
    }

    /** 查询单消息的全部译文。 */
    @GetMapping("/{messageId}/translations")
    @PreAuthorize("@i18nManagementAccess.canRead(authentication)")
    public Result<List<I18nTranslation>> translations(@PathVariable String messageId) {
        return Result.success(management.translations(messageId));
    }

    /** 查询精确重复译文供前端提醒；不会限制后续保存。 */
    @PostMapping("/{messageId}/translations/{localeCode}/duplicate-check")
    @PreAuthorize("@i18nManagementAccess.canRead(authentication)")
    public Result<List<String>> duplicateMessageKeys(@PathVariable String messageId,
                                                      @PathVariable String localeCode,
                                                      @RequestBody TranslationRequest request) {
        return Result.success(management.duplicateMessageKeys(messageId, localeCode, request.messageText()));
    }

    /** 创建请求，messageKey 创建后不可改。 */
    public record CreateRequest(String messageKey, String description, Boolean enabled) {
    }

    /** 版本化更新请求，不允许 Rename。 */
    public record UpdateRequest(String description, Boolean enabled, Long version) {
    }

    /** 单 Locale 译文请求。 */
    public record TranslationRequest(String messageText, Long version) {
    }
}
