package io.github.chrisshi.mom.system.controller.i18n;

import io.github.chrisshi.mom.system.api.SupportedLocaleInfo;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.ChangeLocaleStatus;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.CreateLocale;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.LocaleView;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.UpdateLocale;
import io.github.chrisshi.mom.system.application.i18n.SupportedLocaleApplication;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * System SupportedLocale 的 HTTP 管理与只读契约边界。
 *
 * <p>Controller 不接收用户偏好或业务数据，只管理平台可选择 Locale 与唯一默认值。跨服务读取只暴露
 * localeCode；内部 ID 仅出现在管理 API。事务、默认切换与启停规则全部位于 Application。</p>
 */
@RestController
@RequestMapping("/api/system/i18n/locales")
public class SupportedLocaleController {
    private final SupportedLocaleApplication application;

    /** @param application SupportedLocale 用例入口 */
    public SupportedLocaleController(SupportedLocaleApplication application) {
        this.application = application;
    }

    /** 返回平台 Locale 稳定只读列表。 */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<List<SupportedLocaleInfo>> supportedLocales() {
        return Result.success(application.supportedLocales());
    }

    /** 返回包含 ID/Version 的管理列表。 */
    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('system:i18n:read')")
    public Result<List<LocaleView>> list() {
        return Result.success(application.list());
    }

    /** 创建非默认 Locale 并返回 201。 */
    @PostMapping("/admin")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:i18n:write')")
    public Result<LocaleView> create(@RequestBody CreateLocaleRequest request) {
        return Result.success(application.create(new CreateLocale(request.localeCode(), request.displayName(),
                request.nativeName(), request.enabled(), request.sortOrder())));
    }

    /** 更新 Locale 展示信息，不允许修改 localeCode。 */
    @PutMapping("/admin/{id}")
    @PreAuthorize("hasAuthority('system:i18n:write')")
    public Result<LocaleView> update(@PathVariable String id, @RequestBody UpdateLocaleRequest request) {
        return Result.success(application.update(id, new UpdateLocale(
                request.displayName(), request.nativeName(), request.sortOrder(), request.version())));
    }

    /** 版本化启停 Locale；默认 Locale 不允许禁用。 */
    @PatchMapping("/admin/{id}/status")
    @PreAuthorize("hasAuthority('system:i18n:write')")
    public Result<LocaleView> changeStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        return Result.success(application.changeStatus(
                id, new ChangeLocaleStatus(request.enabled(), request.version())));
    }

    /** 原子切换平台唯一默认 Locale。 */
    @PostMapping("/admin/{id}/default")
    @PreAuthorize("hasAuthority('system:i18n:write')")
    public Result<LocaleView> makeDefault(@PathVariable String id, @RequestBody VersionRequest request) {
        return Result.success(application.makeDefault(id, request.version()));
    }

    /** 创建 Locale 请求。 */
    public record CreateLocaleRequest(
            String localeCode, String displayName, String nativeName, Boolean enabled, Integer sortOrder) {
    }

    /** 更新 Locale 请求。 */
    public record UpdateLocaleRequest(String displayName, String nativeName, Integer sortOrder, Long version) {
    }

    /** 版本化启停 Locale 请求。 */
    public record StatusRequest(Boolean enabled, Long version) {
    }

    /** 默认切换请求。 */
    public record VersionRequest(Long version) {
    }
}
