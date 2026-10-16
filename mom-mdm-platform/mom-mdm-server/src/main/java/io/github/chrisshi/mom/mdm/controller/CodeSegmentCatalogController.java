package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.core.codesegment.CodeSegmentMetadata;
import io.github.chrisshi.mom.core.codesegment.CodeSegmentRegistry;
import io.github.chrisshi.mom.webmvc.response.Result;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** MDM Owner 的已认证编码片段目录只读入口。 */
@RestController
public class CodeSegmentCatalogController {
    private final CodeSegmentRegistry registry;

    /** 注入由 MDM Spring Handler 构建的只读 Registry。 */
    public CodeSegmentCatalogController(CodeSegmentRegistry registry) { this.registry = registry; }

    /** 返回 JSON 可序列化的 V1 目录；权限由 Resource Server 的已认证访问规则保护。 */
    @GetMapping("/code-rules/segments")
    public Result<List<CodeSegmentMetadata>> catalog(@RequestParam(defaultValue = "1") int catalogVersion) {
        return Result.success(registry.catalog(catalogVersion));
    }
}
