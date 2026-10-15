package io.github.chrisshi.mom.qms.controller;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.qms.application.QmsException;
import io.github.chrisshi.mom.qms.application.QualityDefinitionApplication;
import io.github.chrisshi.mom.qms.application.model.QmsPageParams.SpecificationParams;
import io.github.chrisshi.mom.qms.application.model.QualityModels;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * 质量标准与业务修订的 HTTP 边界；数值严格使用 Decimal String。
 */
@RestController
@RequestMapping("/quality-specifications")
public class QualitySpecificationController {
    private final QualityDefinitionApplication application;

    public QualitySpecificationController(QualityDefinitionApplication application) {
        this.application = application;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('qms:quality-definition:write')")
    public Result<QualityModels.DetailView> create(@Valid @RequestBody CreateRequest request) {
        return Result.success(application.createSpecification(request.code(), request.name(), request.plantId(),
            request.materialId(), inputs(request.items())));
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('qms:quality-definition:read')")
    public Result<PageResult<QualityModels.SpecificationView>> search(@RequestBody PageQuery<SpecificationParams> query) {
        return Result.success(application.specifications(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('qms:quality-definition:read')")
    public Result<QualityModels.DetailView> get(@PathVariable String id) {
        return Result.success(application.detail(id));
    }

    @GetMapping("/revisions/{id}")
    @PreAuthorize("hasAuthority('qms:quality-definition:read')")
    public Result<QualityModels.DetailView> revision(@PathVariable String id) {
        return Result.success(application.revision(id));
    }

    @PutMapping("/revisions/{id}")
    @PreAuthorize("hasAuthority('qms:quality-definition:write')")
    public Result<QualityModels.DetailView> update(@PathVariable String id, @Valid @RequestBody UpdateDraftRequest request) {
        return Result.success(application.updateDraft(id, request.lockVersion(), inputs(request.items())));
    }

    @PostMapping("/revisions/next/{specificationId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('qms:quality-definition:write')")
    public Result<QualityModels.DetailView> next(@PathVariable String specificationId) {
        return Result.success(application.nextRevision(specificationId));
    }

    @PatchMapping("/revisions/release/{id}")
    @PreAuthorize("hasAuthority('qms:quality-definition:write')")
    public Result<QualityModels.DetailView> release(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.release(id, request.lockVersion()));
    }

    @PatchMapping("/revisions/obsolete/{id}")
    @PreAuthorize("hasAuthority('qms:quality-definition:write')")
    public Result<QualityModels.DetailView> obsolete(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.obsolete(id, request.lockVersion()));
    }

    private static List<QualityModels.ItemInput> inputs(List<ItemRequest> items) {
        return items.stream().map(item -> new QualityModels.ItemInput(item.sequenceNo(), item.characteristicId(),
            item.methodId(), item.uomId(), decimal(item.lowerLimit()), decimal(item.targetValue()),
            decimal(item.upperLimit()), item.mandatory())).toList();
    }

    private static String decimal(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (!value.isString()) throw QmsException.invalidRange();
        return value.textValue();
    }

    public record CreateRequest(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 200) String name,
                                @NotBlank String plantId, @NotBlank String materialId,
                                @NotEmpty List<@Valid ItemRequest> items) {
    }

    public record UpdateDraftRequest(@NotNull @PositiveOrZero Long lockVersion,
                                     @NotEmpty List<@Valid ItemRequest> items) {
    }

    public record ItemRequest(@NotNull @Positive Integer sequenceNo, @NotBlank String characteristicId,
                              @NotBlank String methodId, @NotBlank String uomId, JsonNode lowerLimit,
                              JsonNode targetValue,
                              JsonNode upperLimit, @NotNull Boolean mandatory) {
    }

    public record VersionRequest(@NotNull @PositiveOrZero Long lockVersion) {
    }
}
