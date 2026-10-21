package io.github.chrisshi.mom.mdm.controller;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.mdm.api.WarehouseResourceReference;
import io.github.chrisshi.mom.mdm.application.WarehouseStructureApplication;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 仓库与库区的跨域静态引用入口，仍使用既有 MDM 仓储读权限。 */
@RestController
@RequiredArgsConstructor
public class WarehouseReferenceController {
    private final WarehouseStructureApplication application;

    

    /** 查询仓库当前新业务引用有效性。 */
    @GetMapping("/warehouse-references/{id}")
    @PreAuthorize("hasAuthority('mdm:warehouse:read')")
    public Result<WarehouseResourceReference> getWarehouseReference(@PathVariable String id) {
        return Result.success(application.getWarehouseReference(id));
    }

    /** 查询库区及其完整仓库/工厂父链有效性。 */
    @GetMapping("/warehouse-area-references/{id}")
    @PreAuthorize("hasAuthority('mdm:warehouse:read')")
    public Result<WarehouseResourceReference> getWarehouseAreaReference(@PathVariable String id) {
        return Result.success(application.getWarehouseAreaReference(id));
    }
}
