package io.github.chrisshi.mom.mdm.controller;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.mdm.application.MasterDataGovernanceApplication;
import io.github.chrisshi.mom.mdm.application.model.GovernanceModels;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 主数据治理的只读按需检查入口，不提供自动修复命令。
 */
@RestController
@RequestMapping("/governance")
@RequiredArgsConstructor
public class MasterDataGovernanceController {
    private final MasterDataGovernanceApplication application;



    /**
     * 执行选定板块的一致性扫描；POST 仅承载查询参数，不产生业务写。
     */
    @PostMapping("/consistency-checks")
    @PreAuthorize("hasAuthority('mdm:governance:read')")
    public Result<GovernanceModels.Report> check(@Valid @RequestBody CheckRequest request) {
        return Result.success(application.check(request.scope()));
    }

    public record CheckRequest(@NotNull GovernanceModels.Scope scope) {
    }
}
