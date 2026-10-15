package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.mdm.api.QualitySpecificationReference;
import io.github.chrisshi.mom.mdm.application.QualityDefinitionApplication;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 已发布质量标准的跨域只读 HTTP 契约边界。 */
@RestController
public class QualitySpecificationReferenceController {
    private final QualityDefinitionApplication application;

    /** 注入质量定义读取用例。 */
    public QualitySpecificationReferenceController(QualityDefinitionApplication application) {
        this.application = application;
    }

    /** 返回已发布修订及当前新业务可用性；不否定历史检验事实。 */
    @GetMapping("/quality-specification-references/{id}")
    @PreAuthorize("hasAuthority('mdm:quality-definition:read')")
    public Result<QualitySpecificationReference> get(@PathVariable String id) {
        return Result.success(application.reference(id));
    }
}
