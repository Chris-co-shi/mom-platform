package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.mdm.application.FactoryStructureApplication;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.PlantView;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.ProductionLineView;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.WorkshopView;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.WorkstationView;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Plant 至 Workstation 工厂结构的 HTTP 边界。
 *
 * <p>Controller 只处理协议校验和 Result 包装，不访问 Mapper/Entity 或开启事务。所有端点要求认证；细粒度
 * MDM 权限尚未在本 Slice 定义，因此不在这里伪造不可分配的 Permission。</p>
 */
@RestController
@RequestMapping("/api/mdm")
@PreAuthorize("isAuthenticated()")
public class FactoryStructureController {
    private final FactoryStructureApplication application;

    /**
     * @param application 工厂结构用例入口
     */
    public FactoryStructureController(FactoryStructureApplication application) {
        this.application = application;
    }

    /**
     * 创建 Plant，成功返回 201。
     */
    @PostMapping("/plants")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<PlantView> createPlant(@Valid @RequestBody CreateRootRequest request) {
        return Result.success(application.createPlant(request.code(), request.nameZh(), request.nameEn(), request.status()));
    }

    /**
     * 更新 Plant 名称，不接收 Code。
     */
    @PutMapping("/plants/{id}")
    public Result<PlantView> updatePlant(@PathVariable String id, @Valid @RequestBody UpdateNameRequest request) {
        return Result.success(application.updatePlant(id, request.nameZh(), request.nameEn(), request.version()));
    }

    /**
     * 查询 Plant 详情。
     */
    @GetMapping("/plants/{id}")
    public Result<PlantView> getPlant(@PathVariable String id) {
        return Result.success(application.getPlant(id));
    }

    /**
     * 分页查询 Plant。
     */
    @GetMapping("/plants")
    public Result<PageResult<PlantView>> pagePlants(@RequestParam(defaultValue = "1") @Positive long pageNo,
                                                    @RequestParam(defaultValue = "20") @Positive long pageSize) {
        return Result.success(application.pagePlants(pageNo, pageSize));
    }

    /**
     * 启用 Plant。
     */
    @PatchMapping("/plants/{id}/enable")
    public Result<PlantView> enablePlant(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.enablePlant(id, request.version()));
    }

    /**
     * 停用 Plant。
     */
    @PatchMapping("/plants/{id}/disable")
    public Result<PlantView> disablePlant(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.disablePlant(id, request.version()));
    }

    /**
     * 创建 Workshop。
     */
    @PostMapping("/workshops")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<WorkshopView> createWorkshop(@Valid @RequestBody CreateWorkshopRequest request) {
        return Result.success(application.createWorkshop(request.plantId(), request.code(), request.nameZh(), request.nameEn(), request.status()));
    }

    /**
     * 更新 Workshop 名称。
     */
    @PutMapping("/workshops/{id}")
    public Result<WorkshopView> updateWorkshop(@PathVariable String id, @Valid @RequestBody UpdateNameRequest request) {
        return Result.success(application.updateWorkshop(id, request.nameZh(), request.nameEn(), request.version()));
    }

    /**
     * 查询 Workshop 详情。
     */
    @GetMapping("/workshops/{id}")
    public Result<WorkshopView> getWorkshop(@PathVariable String id) {
        return Result.success(application.getWorkshop(id));
    }

    /**
     * 可按 plantId 分页查询 Workshop。
     */
    @GetMapping("/workshops")
    public Result<PageResult<WorkshopView>> pageWorkshops(@RequestParam(required = false) String plantId, @RequestParam(defaultValue = "1") @Positive long pageNo, @RequestParam(defaultValue = "20") @Positive long pageSize) {
        return Result.success(application.pageWorkshops(plantId, pageNo, pageSize));
    }

    /**
     * 启用 Workshop。
     */
    @PatchMapping("/workshops/{id}/enable")
    public Result<WorkshopView> enableWorkshop(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.enableWorkshop(id, request.version()));
    }

    /**
     * 停用 Workshop。
     */
    @PatchMapping("/workshops/{id}/disable")
    public Result<WorkshopView> disableWorkshop(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.disableWorkshop(id, request.version()));
    }

    /**
     * 创建 ProductionLine。
     */
    @PostMapping("/production-lines")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ProductionLineView> createProductionLine(@Valid @RequestBody CreateProductionLineRequest request) {
        return Result.success(application.createProductionLine(request.workshopId(), request.code(), request.nameZh(), request.nameEn(), request.status()));
    }

    /**
     * 更新 ProductionLine 名称。
     */
    @PutMapping("/production-lines/{id}")
    public Result<ProductionLineView> updateProductionLine(@PathVariable String id, @Valid @RequestBody UpdateNameRequest request) {
        return Result.success(application.updateProductionLine(id, request.nameZh(), request.nameEn(), request.version()));
    }

    /**
     * 查询 ProductionLine 详情。
     */
    @GetMapping("/production-lines/{id}")
    public Result<ProductionLineView> getProductionLine(@PathVariable String id) {
        return Result.success(application.getProductionLine(id));
    }

    /**
     * 可按 workshopId 分页查询 ProductionLine。
     */
    @GetMapping("/production-lines")
    public Result<PageResult<ProductionLineView>> pageProductionLines(@RequestParam(required = false) String workshopId, @RequestParam(defaultValue = "1") @Positive long pageNo, @RequestParam(defaultValue = "20") @Positive long pageSize) {
        return Result.success(application.pageProductionLines(workshopId, pageNo, pageSize));
    }

    /**
     * 启用 ProductionLine。
     */
    @PatchMapping("/production-lines/{id}/enable")
    public Result<ProductionLineView> enableProductionLine(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.enableProductionLine(id, request.version()));
    }

    /**
     * 停用 ProductionLine。
     */
    @PatchMapping("/production-lines/{id}/disable")
    public Result<ProductionLineView> disableProductionLine(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.disableProductionLine(id, request.version()));
    }

    /**
     * 创建 Workstation。
     */
    @PostMapping("/workstations")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<WorkstationView> createWorkstation(@Valid @RequestBody CreateWorkstationRequest request) {
        return Result.success(application.createWorkstation(request.productionLineId(), request.code(), request.nameZh(), request.nameEn(), request.status()));
    }

    /**
     * 更新 Workstation 名称。
     */
    @PutMapping("/workstations/{id}")
    public Result<WorkstationView> updateWorkstation(@PathVariable String id, @Valid @RequestBody UpdateNameRequest request) {
        return Result.success(application.updateWorkstation(id, request.nameZh(), request.nameEn(), request.version()));
    }

    /**
     * 查询 Workstation 详情。
     */
    @GetMapping("/workstations/{id}")
    public Result<WorkstationView> getWorkstation(@PathVariable String id) {
        return Result.success(application.getWorkstation(id));
    }

    /**
     * 可按 productionLineId 分页查询 Workstation。
     */
    @GetMapping("/workstations")
    public Result<PageResult<WorkstationView>> pageWorkstations(@RequestParam(required = false) String productionLineId, @RequestParam(defaultValue = "1") @Positive long pageNo, @RequestParam(defaultValue = "20") @Positive long pageSize) {
        return Result.success(application.pageWorkstations(productionLineId, pageNo, pageSize));
    }

    /**
     * 启用 Workstation。
     */
    @PatchMapping("/workstations/{id}/enable")
    public Result<WorkstationView> enableWorkstation(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.enableWorkstation(id, request.version()));
    }

    /**
     * 停用 Workstation。
     */
    @PatchMapping("/workstations/{id}/disable")
    public Result<WorkstationView> disableWorkstation(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.disableWorkstation(id, request.version()));
    }

    /**
     * 顶层主数据创建请求。
     */
    public record CreateRootRequest(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 200) String nameZh,
                                    @Size(max = 200) String nameEn, @NotBlank String status) {
    }

    /**
     * Workshop 创建请求，plantId 是必填直接父级。
     */
    public record CreateWorkshopRequest(@NotBlank @Size(max = 19) String plantId,
                                        @NotBlank @Size(max = 64) String code,
                                        @NotBlank @Size(max = 200) String nameZh,
                                        @Size(max = 200) String nameEn, @NotBlank String status) {
    }

    /**
     * ProductionLine 创建请求，workshopId 是必填直接父级。
     */
    public record CreateProductionLineRequest(@NotBlank @Size(max = 19) String workshopId,
                                              @NotBlank @Size(max = 64) String code,
                                              @NotBlank @Size(max = 200) String nameZh,
                                              @Size(max = 200) String nameEn, @NotBlank String status) {
    }

    /**
     * Workstation 创建请求，productionLineId 是必填直接父级。
     */
    public record CreateWorkstationRequest(@NotBlank @Size(max = 19) String productionLineId,
                                           @NotBlank @Size(max = 64) String code,
                                           @NotBlank @Size(max = 200) String nameZh,
                                           @Size(max = 200) String nameEn, @NotBlank String status) {
    }

    /**
     * 仅更新名称与乐观锁版本的请求；有意不包含 Code 和父级。
     */
    public record UpdateNameRequest(@NotBlank @Size(max = 200) String nameZh, @Size(max = 200) String nameEn,
                                    @NotNull Long version) {
    }

    /**
     * 启停请求只携带乐观锁版本，目标状态由端点语义决定。
     */
    public record VersionRequest(@NotNull Long version) {
    }
}
