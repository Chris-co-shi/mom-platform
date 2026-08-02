package io.github.chrisshi.mom.system.web.preference;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.github.chrisshi.mom.system.api.ResolvedUserPreference;
import io.github.chrisshi.mom.system.api.UserViewSetting;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.ColumnCommand;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.FilterCommand;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.ResetCommand;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.SaveDisplayPreferenceCommand;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.SaveViewSettingCommand;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationModels.SortCommand;
import io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 当前用户显示偏好与受限视图设置的 HTTP 入站 Adapter。
 *
 * <p>全部端点只要求已认证，不新增管理 Permission；用户身份不出现在路径、Query 或 Body，只由
 * CurrentPreferenceUserProvider 的 JWT sub 决定。所有请求 DTO 以局部 {@link JsonAnySetter} 对未声明字段
 * fail-closed，避免身份、授权、审计或普通扩展字段被静默接受；Controller 只映射 DTO/Command 和 HTTP
 * Payload 上限，不依赖 Domain Repository、Mapper 或 Entity。</p>
 */
@RestController
@RequestMapping("/api/system/preferences/me")
@PreAuthorize("isAuthenticated()")
public class SystemUserPreferenceController {
    private static final int MAX_VIEW_PAYLOAD_BYTES = 16 * 1024;
    private final SystemUserPreferenceApplicationService service;

    public SystemUserPreferenceController(SystemUserPreferenceApplicationService service) {
        this.service = service;
    }

    /** 返回当前用户五个白名单显示偏好的有效值与来源。 */
    @GetMapping
    public ResolvedUserPreference getPreference() {
        return service.getMyPreference();
    }

    /** 全量保存或移除当前用户显示覆盖；null 表示删除该字段覆盖。 */
    @PutMapping
    public ResolvedUserPreference savePreference(@RequestBody SaveDisplayPreferenceRequest request) {
        return service.saveMyPreference(new SaveDisplayPreferenceCommand(
                request.locale(), request.displayTimezone(), request.themeMode(), request.density(),
                request.pageSize(), request.version()));
    }

    /** 清空当前用户全部显示覆盖，保留持久化记录。 */
    @PostMapping("/reset")
    public ResolvedUserPreference resetPreference(@RequestBody ResetRequest request) {
        return service.resetMyPreference(new ResetCommand(request.version()));
    }

    /** 按稳定 applicationCode/viewKey 读取当前用户视图。 */
    @GetMapping("/views/{applicationCode}/{viewKey}")
    public UserViewSetting getView(@PathVariable String applicationCode, @PathVariable String viewKey) {
        return service.getMyView(applicationCode, viewKey);
    }

    /** 保存当前用户类型化视图；请求总 Payload 最大 16 KiB。 */
    @PutMapping("/views/{applicationCode}/{viewKey}")
    public UserViewSetting saveView(
            @PathVariable String applicationCode,
            @PathVariable String viewKey,
            @RequestBody SaveViewSettingRequest request,
            HttpServletRequest servletRequest) {
        requireRequestSize(servletRequest);
        return service.saveMyView(applicationCode, viewKey, request.toCommand());
    }

    /** Reset 当前用户视图并禁用记录，不物理删除。 */
    @PostMapping("/views/{applicationCode}/{viewKey}/reset")
    public UserViewSetting resetView(
            @PathVariable String applicationCode,
            @PathVariable String viewKey,
            @RequestBody ResetRequest request) {
        return service.resetMyView(applicationCode, viewKey, new ResetCommand(request.version()));
    }

    /** 列出当前用户指定应用内的已启用视图；applicationCode 强制提供，最多返回 100 条。 */
    @GetMapping("/views")
    public List<UserViewSetting> listViews(@RequestParam String applicationCode) {
        return service.listMyViews(applicationCode);
    }

    private static void requireRequestSize(HttpServletRequest request) {
        long length = request.getContentLengthLong();
        if (length > MAX_VIEW_PAYLOAD_BYTES) {
            throw new io.github.chrisshi.mom.system.application.preference.SystemUserPreferenceException.Invalid(
                    "payload_too_large", "视图设置 Payload 超过 16 KiB");
        }
    }

    /**
     * 统一拒绝 Preference 请求中的未声明字段。
     *
     * <p>不记录字段名或字段值，避免身份、Token 或其他敏感输入进入错误响应和日志。异常由 Web
     * 异常处理器稳定映射为 400 invalid_request；该策略只作用于本 Controller 的请求 DTO。</p>
     */
    private static void rejectUnknownField() {
        throw new IllegalArgumentException("Preference 请求包含未声明字段");
    }

    /** 五个可空显示覆盖和乐观版本；不允许携带 userId 或审计字段。 */
    public record SaveDisplayPreferenceRequest(
            String locale, String displayTimezone, String themeMode, String density, Integer pageSize, Long version) {
        @JsonAnySetter
        private void rejectUnknown(String fieldName, JsonNode ignoredValue) {
            rejectUnknownField();
        }
    }

    /** 乐观 Reset 请求。 */
    public record ResetRequest(Long version) {
        @JsonAnySetter
        private void rejectUnknown(String fieldName, JsonNode ignoredValue) {
            rejectUnknownField();
        }
    }

    /** 类型化视图保存请求；不接受任意 JSON Object。 */
    public record SaveViewSettingRequest(
            Integer schemaVersion,
            List<ColumnRequest> columns,
            List<SortRequest> sorts,
            List<FilterRequest> filters,
            Integer pageSize,
            Long version) {
        @JsonAnySetter
        private void rejectUnknown(String fieldName, JsonNode ignoredValue) {
            rejectUnknownField();
        }

        SaveViewSettingCommand toCommand() {
            return new SaveViewSettingCommand(schemaVersion,
                    columns == null ? null : columns.stream().map(ColumnRequest::toCommand).toList(),
                    sorts == null ? null : sorts.stream().map(SortRequest::toCommand).toList(),
                    filters == null ? null : filters.stream().map(FilterRequest::toCommand).toList(),
                    pageSize, version);
        }
    }

    /** 类型化列请求。 */
    public record ColumnRequest(String columnKey, Boolean visible, Integer order, Integer width, String pinned) {
        @JsonAnySetter
        private void rejectUnknown(String fieldName, JsonNode ignoredValue) {
            rejectUnknownField();
        }

        ColumnCommand toCommand() {
            return new ColumnCommand(columnKey, visible, order, width, pinned);
        }
    }

    /** 类型化排序请求。 */
    public record SortRequest(String fieldKey, String direction, Integer priority) {
        @JsonAnySetter
        private void rejectUnknown(String fieldName, JsonNode ignoredValue) {
            rejectUnknownField();
        }

        SortCommand toCommand() {
            return new SortCommand(fieldKey, direction, priority);
        }
    }

    /** 类型化 Filter 请求；values 的元素由 Jackson 限定为 String。 */
    public record FilterRequest(String fieldKey, String operator, String valueType, List<String> values) {
        @JsonAnySetter
        private void rejectUnknown(String fieldName, JsonNode ignoredValue) {
            rejectUnknownField();
        }

        FilterCommand toCommand() {
            return new FilterCommand(fieldKey, operator, valueType, values);
        }
    }
}
