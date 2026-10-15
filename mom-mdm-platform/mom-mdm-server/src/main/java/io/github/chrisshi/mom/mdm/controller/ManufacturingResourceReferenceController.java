package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.mdm.api.ManufacturingResourceReference;
import io.github.chrisshi.mom.mdm.application.FactoryStructureApplication;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 制造资源跨域引用入口，鉴权独立于前端菜单可见性。 */
@RestController
@RequestMapping("/manufacturing-resource-references")
public class ManufacturingResourceReferenceController {
    private final FactoryStructureApplication application;

    /** 复用工厂结构权威 Application，不建立第二写入模型。 */
    public ManufacturingResourceReferenceController(FactoryStructureApplication application) {
        this.application = application;
    }

    /** 按资源类型与 ID 查询当前新业务引用有效性。 */
    @GetMapping("/{resourceType}/{id}")
    @PreAuthorize("hasAuthority('mdm:factory:read')")
    public Result<ManufacturingResourceReference> getReference(
            @PathVariable String resourceType, @PathVariable String id) {
        return Result.success(application.getManufacturingResourceReference(resourceType, id));
    }
}
