package io.github.chrisshi.mom.system.controller.i18n;

import io.github.chrisshi.mom.system.application.i18n.I18nModels.ChangeLocaleStatus;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.CreateLocale;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.LocaleView;
import io.github.chrisshi.mom.system.application.i18n.I18nModels.UpdateLocale;
import io.github.chrisshi.mom.system.application.i18n.SupportedLocaleApplication;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 平台 Locale 目录维护 HTTP 边界。 */
@RestController
@RequestMapping("/i18n/locale-catalog")
public class LocaleCatalogController {
    private final SupportedLocaleApplication application;
    public LocaleCatalogController(SupportedLocaleApplication application) { this.application = application; }

    @GetMapping @PreAuthorize("hasAuthority('system:i18n:read')")
    public Result<List<LocaleView>> list() { return Result.success(application.list()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('system:i18n:write')")
    public Result<LocaleView> create(@RequestBody CreateLocaleRequest request) {
        return Result.success(application.create(new CreateLocale(request.localeCode(), request.displayName(), request.nativeName(), request.enabled(), request.sortOrder())));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('system:i18n:write')")
    public Result<LocaleView> update(@PathVariable String id, @RequestBody UpdateLocaleRequest request) {
        return Result.success(application.update(id, new UpdateLocale(request.displayName(), request.nativeName(), request.sortOrder(), request.version())));
    }
    @PatchMapping("/change-status/{id}") @PreAuthorize("hasAuthority('system:i18n:write')")
    public Result<LocaleView> changeStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        return Result.success(application.changeStatus(id, new ChangeLocaleStatus(request.enabled(), request.version())));
    }
    @PostMapping("/make-default/{id}") @PreAuthorize("hasAuthority('system:i18n:write')")
    public Result<LocaleView> makeDefault(@PathVariable String id, @RequestBody VersionRequest request) {
        return Result.success(application.makeDefault(id, request.version()));
    }

    public record CreateLocaleRequest(String localeCode, String displayName, String nativeName, Boolean enabled, Integer sortOrder) {}
    public record UpdateLocaleRequest(String displayName, String nativeName, Integer sortOrder, Long version) {}
    public record StatusRequest(Boolean enabled, Long version) {}
    public record VersionRequest(Long version) {}
}
