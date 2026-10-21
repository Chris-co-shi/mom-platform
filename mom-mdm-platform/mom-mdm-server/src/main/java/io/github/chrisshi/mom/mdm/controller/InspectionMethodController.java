package io.github.chrisshi.mom.mdm.controller;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.mdm.application.QualityDefinitionApplication;
import io.github.chrisshi.mom.mdm.application.model.QualityPageParams.MethodParams;
import io.github.chrisshi.mom.mdm.application.model.QualityModels;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 检验方法静态定义的 HTTP 边界。 */
@RestController @RequestMapping("/inspection-methods")
@RequiredArgsConstructor
public class InspectionMethodController {
    private final QualityDefinitionApplication application;

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('mdm:quality-definition:write')")
    public Result<QualityModels.MethodView> create(@Valid @RequestBody CreateRequest request) {
        return Result.success(application.createMethod(request.code(), request.name(), request.description()));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('mdm:quality-definition:write')")
    public Result<QualityModels.MethodView> update(@PathVariable String id, @Valid @RequestBody UpdateRequest request) {
        return Result.success(application.updateMethod(id, request.name(), request.description(), request.enabled(), request.version()));
    }
    @PostMapping("/search") @PreAuthorize("hasAuthority('mdm:quality-definition:read')")
    public Result<PageResult<QualityModels.MethodView>> search(@RequestBody PageQuery<MethodParams> query) {
        return Result.success(application.methods(query));
    }
    public record CreateRequest(@NotBlank @Size(max=64) String code, @NotBlank @Size(max=200) String name,
                                @Size(max=1000) String description) { }
    public record UpdateRequest(@NotBlank @Size(max=200) String name, @Size(max=1000) String description,
                                @NotNull Boolean enabled, @NotNull @PositiveOrZero Long version) { }
}
