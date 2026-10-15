package io.github.chrisshi.mom.qms.controller;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.qms.application.QualityDefinitionApplication;
import io.github.chrisshi.mom.qms.application.model.QmsPageParams.CharacteristicParams;
import io.github.chrisshi.mom.qms.application.model.QualityModels;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 数值型检验特性的 HTTP 边界。 */
@RestController @RequestMapping("/inspection-characteristics")
public class InspectionCharacteristicController {
    private final QualityDefinitionApplication application;
    public InspectionCharacteristicController(QualityDefinitionApplication application) { this.application = application; }

    /** 创建特性并在写入前校验默认 UOM。 */
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('qms:quality-definition:write')")
    public Result<QualityModels.CharacteristicView> create(@Valid @RequestBody CreateRequest request) {
        return Result.success(application.createCharacteristic(request.code(), request.name(), request.defaultUomId()));
    }
    /** 以技术版本更新特性和启停状态。 */
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('qms:quality-definition:write')")
    public Result<QualityModels.CharacteristicView> update(@PathVariable String id,
            @Valid @RequestBody UpdateRequest request) {
        return Result.success(application.updateCharacteristic(id, request.name(), request.defaultUomId(),
                request.enabled(), request.version()));
    }
    /** 分页查询特性。 */
    @PostMapping("/search") @PreAuthorize("hasAuthority('qms:quality-definition:read')")
    public Result<PageResult<QualityModels.CharacteristicView>> search(@RequestBody PageQuery<CharacteristicParams> query) {
        return Result.success(application.characteristics(query));
    }
    public record CreateRequest(@NotBlank @Size(max=64) String code, @NotBlank @Size(max=200) String name,
                                @NotBlank String defaultUomId) { }
    public record UpdateRequest(@NotBlank @Size(max=200) String name, @NotBlank String defaultUomId,
                                @NotNull Boolean enabled, @NotNull @PositiveOrZero Long version) { }
}
