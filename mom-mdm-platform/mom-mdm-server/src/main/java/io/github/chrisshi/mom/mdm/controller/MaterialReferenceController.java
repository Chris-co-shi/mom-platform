package io.github.chrisshi.mom.mdm.controller;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.mdm.api.MaterialReference;
import io.github.chrisshi.mom.mdm.application.MaterialApplication;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 物料跨域只读引用 HTTP 契约；沿用 MDM 读权限和 Result 协议。 */
@RestController
@RequestMapping("/material-references")
@RequiredArgsConstructor
public class MaterialReferenceController {
    private final MaterialApplication application;

    

    /** 查询当前新业务引用有效性，历史记录由消费域自行固定其使用时快照。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('mdm:material:read')")
    public Result<MaterialReference> getMaterialReference(@PathVariable String id) {
        return Result.success(application.getMaterialReference(id));
    }
}
