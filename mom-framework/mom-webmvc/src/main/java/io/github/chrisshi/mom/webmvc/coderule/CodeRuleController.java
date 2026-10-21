package io.github.chrisshi.mom.webmvc.coderule;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.coderule.CodeRuleDefinition;
import io.github.chrisshi.mom.coderule.CodeRuleFailure;
import io.github.chrisshi.mom.coderule.CodeRulePageParams;
import io.github.chrisshi.mom.coderule.CodeRuleView;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentMetadata;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentParameterSchema;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRegistry;
import io.github.chrisshi.mom.coderule.application.CodeRuleApplication;
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
@RequiredArgsConstructor
public class CodeRuleController {
    private final CodeRuleApplication application;
    private final CodeSegmentRegistry handlers;
    

    /** 返回与 CodeRuleDesigner 精确兼容的 V1 目录。 */
    @GetMapping("/segments")
    public Result<List<Map<String, Object>>> segments(@RequestParam(defaultValue = "1") int catalogVersion) {
        return Result.success(handlers.catalog(catalogVersion).stream().map(CodeRuleController::entry).toList());
    }
    /** 创建当前 Owner 的规则身份及首个草稿。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@codeRuleManagementAccess.canWrite(authentication)")
    public Result<CodeRuleView> create(@RequestBody CreateRequest request) {
        return Result.success(application.create(request.ruleCode(), request.usageScenario(), request.description(), request.definition()));
    }
    /** 按规则编码和修订状态查询当前 Owner 的分页数据。 */
    @PostMapping("/search")
    @PreAuthorize("@codeRuleManagementAccess.canRead(authentication)")
    public Result<PageResult<CodeRuleView>> search(@RequestBody PageQuery<CodeRulePageParams> query) {
        return Result.success(application.search(query));
    }
    /** 通过修订 ID 读取完整历史定义和规则级版本。 */
    @GetMapping("/{revisionId}")
    @PreAuthorize("@codeRuleManagementAccess.canRead(authentication)")
    public Result<CodeRuleView> detail(@PathVariable String revisionId) {
        return Result.success(application.detail(revisionId));
    }
    /** 以规则级版本保护规则说明和当前草稿完整定义的原子更新。 */
    @PutMapping("/{revisionId}")
    @PreAuthorize("@codeRuleManagementAccess.canWrite(authentication)")
    public Result<CodeRuleView> update(@PathVariable String revisionId, @RequestBody UpdateRequest request) {
        return Result.success(application.updateDraft(revisionId, request.usageScenario(), request.description(), request.definition(), required(request.ruleVersion())));
    }
    /** 从历史修订复制新草稿。 */
    @PostMapping("/{revisionId}/revisions")
    @PreAuthorize("@codeRuleManagementAccess.canWrite(authentication)")
    public Result<CodeRuleView> revise(@PathVariable String revisionId, @RequestBody VersionRequest request) {
        return Result.success(application.revise(revisionId, required(request.ruleVersion())));
    }
    /** 发布目标修订，由 Application 重新校验当前片段与字段能力。 */
    @PostMapping("/{revisionId}/enable")
    @PreAuthorize("@codeRuleManagementAccess.canWrite(authentication)")
    public Result<CodeRuleView> enable(@PathVariable String revisionId, @RequestBody VersionRequest request) {
        return Result.success(application.enable(revisionId, required(request.ruleVersion())));
    }
    /** 停用已发布修订并保留历史定义。 */
    @PostMapping("/{revisionId}/disable")
    @PreAuthorize("@codeRuleManagementAccess.canWrite(authentication)")
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
    /** 创建请求包含稳定编码、规则级使用场景、说明与定义。 */
    public record CreateRequest(String ruleCode, String usageScenario, String description, CodeRuleDefinition definition) { }
    /** 草稿更新携带规则级说明、完整定义和并发版本。 */
    public record UpdateRequest(String usageScenario, String description, CodeRuleDefinition definition, Long ruleVersion) { }
    /** 修订与启停动作共用的规则级并发版本。 */
    public record VersionRequest(Long ruleVersion) { }
}
