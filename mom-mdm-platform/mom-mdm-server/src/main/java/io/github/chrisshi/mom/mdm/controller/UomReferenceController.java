package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.mdm.api.UomReference;
import io.github.chrisshi.mom.mdm.application.UomMasterDataApplication;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 计量单位跨域只读引用入口，使用现有单位读取权限。 */
@RestController
@RequestMapping("/uom-references")
public class UomReferenceController {
    private final UomMasterDataApplication application;

    /** 注入唯一 MDM 单位权威用例。 */
    public UomReferenceController(UomMasterDataApplication application) {
        this.application = application;
    }

    /** 返回当前单位与量纲父链有效性，不改变历史换算版本。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('mdm:uom:read')")
    public Result<UomReference> getReference(@PathVariable String id) {
        return Result.success(application.getUomReference(id));
    }
}
