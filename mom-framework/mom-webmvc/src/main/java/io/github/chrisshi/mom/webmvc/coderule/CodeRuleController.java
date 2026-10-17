package io.github.chrisshi.mom.webmvc.coderule;

import io.github.chrisshi.mom.core.coderule.*;
import io.github.chrisshi.mom.core.codesegment.*;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 各显式启用 Owner 共用的编码规则 HTTP 边界。 */
@RestController
@RequestMapping("/code-rules")
public class CodeRuleController {
    private final CodeRuleApplication application;
    private final CodeSegmentRegistry handlers;
    public CodeRuleController(CodeRuleApplication application, CodeSegmentRegistry handlers) {
        this.application = application; this.handlers = handlers;
    }

    /** 返回与 CodeRuleDesigner 精确兼容的 V1 目录。 */
    @GetMapping("/segments")
    public Result<List<Map<String, Object>>> segments(@RequestParam(defaultValue = "1") int catalogVersion) {
        return Result.success(handlers.catalog(catalogVersion).stream().map(CodeRuleController::entry).toList());
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mdm:code-rule:write')")
    public Result<CodeRuleView> create(@RequestBody CreateRequest request) {
        return Result.success(application.create(request.ruleCode(), request.definition()));
    }
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('mdm:code-rule:read')")
    public Result<PageResult<CodeRuleView>> search(@RequestBody PageQuery<CodeRulePageParams> query) {
        return Result.success(application.search(query));
    }
    @GetMapping("/{revisionId}")
    @PreAuthorize("hasAuthority('mdm:code-rule:read')")
    public Result<CodeRuleView> detail(@PathVariable String revisionId) {
        return Result.success(application.detail(revisionId));
    }
    @PutMapping("/{revisionId}")
    @PreAuthorize("hasAuthority('mdm:code-rule:write')")
    public Result<CodeRuleView> update(@PathVariable String revisionId, @RequestBody UpdateRequest request) {
        return Result.success(application.updateDraft(revisionId, request.definition(), required(request.ruleVersion())));
    }
    @PostMapping("/{revisionId}/revisions")
    @PreAuthorize("hasAuthority('mdm:code-rule:write')")
    public Result<CodeRuleView> revise(@PathVariable String revisionId, @RequestBody VersionRequest request) {
        return Result.success(application.revise(revisionId, required(request.ruleVersion())));
    }
    @PostMapping("/{revisionId}/enable")
    @PreAuthorize("hasAuthority('mdm:code-rule:write')")
    public Result<CodeRuleView> enable(@PathVariable String revisionId, @RequestBody VersionRequest request) {
        return Result.success(application.enable(revisionId, required(request.ruleVersion())));
    }
    @PostMapping("/{revisionId}/disable")
    @PreAuthorize("hasAuthority('mdm:code-rule:write')")
    public Result<CodeRuleView> disable(@PathVariable String revisionId, @RequestBody VersionRequest request) {
        return Result.success(application.disable(revisionId, required(request.ruleVersion())));
    }

    private static long required(Long value) {
        if (value == null || value < 0) throw new CodeRuleFailure(CodeRuleFailure.Kind.BAD_REQUEST, "rule_version_invalid");
        return value;
    }
    private static Map<String, Object> entry(CodeSegmentMetadata metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", metadata.type()); result.put("label", metadata.label());
        result.put("description", metadata.description()); result.put("catalogVersion", 1);
        List<Map<String, Object>> fields = new ArrayList<>();
        for (CodeSegmentParameterSchema.Field field : metadata.parameterSchema().fields()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", field.id()); item.put("name", field.name()); item.put("type", field.type().name());
            item.put("label", field.label()); item.put("required", field.required());
            if (field.defaultValue() != null) item.put("defaultValue", field.defaultValue());
            if (!field.options().isEmpty()) item.put("options", field.options());
            if (field.minimum() != null) item.put("min", field.minimum());
            if (field.maximum() != null) item.put("max", field.maximum());
            if (field.readOnly()) item.put("readonly", true);
            fields.add(item);
        }
        result.put("parameterSchema", Map.of("schemaVersion", 1, "fields", fields));
        return result;
    }
    public record CreateRequest(String ruleCode, CodeRuleDefinition definition) { }
    public record UpdateRequest(CodeRuleDefinition definition, Long ruleVersion) { }
    public record VersionRequest(Long ruleVersion) { }
}
