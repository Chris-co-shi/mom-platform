package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.mdm.api.ProductionVersionReference;
import io.github.chrisshi.mom.mdm.application.ProductionVersionApplication;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 已发布 ProductionVersion 的跨域只读 HTTP 契约边界。 */
@RestController
public class ProductionVersionReferenceController {
    private final ProductionVersionApplication application;

    /** 注入生产版本读取用例。 */
    public ProductionVersionReferenceController(ProductionVersionApplication application) {
        this.application = application;
    }

    /** 返回当前新业务可用性；历史工单仍应使用其已固定快照。 */
    @GetMapping("/production-version-references/{id}")
    @PreAuthorize("hasAuthority('mdm:manufacturing-definition:read')")
    public Result<ProductionVersionReference> get(@PathVariable String id) {
        return Result.success(application.getReference(id));
    }
}
