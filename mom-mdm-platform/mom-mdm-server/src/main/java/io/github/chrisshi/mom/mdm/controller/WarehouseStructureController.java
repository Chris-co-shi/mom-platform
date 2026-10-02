package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.WarehouseAreaPageParams;
import io.github.chrisshi.mom.mdm.application.model.MdmPageParams.WarehousePageParams;
import io.github.chrisshi.mom.mdm.application.model.MdmMasterDataViews.WarehouseAreaView;
import io.github.chrisshi.mom.mdm.application.model.MdmMasterDataViews.WarehouseView;
import io.github.chrisshi.mom.mdm.application.WarehouseStructureApplication;
import io.github.chrisshi.mom.webmvc.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Warehouse 与 WarehouseArea 的 HTTP 协议边界。
 *
 * <p>该 Controller 只校验请求、包装 Result 并调用 Application，不接触 Entity、Mapper 或库存运行时事实。
 * 读写端点分别要求 {@code mdm:warehouse:read} 与 {@code mdm:warehouse:write} 权限，事务和父级校验由
 * Application 负责。</p>
 */
@RestController
@RequestMapping("/api/mdm")
public class WarehouseStructureController {
    private final WarehouseStructureApplication application;

    /**
     * @param application 仓库结构用例入口
     */
    public WarehouseStructureController(WarehouseStructureApplication application) {
        this.application = application;
    }

    /**
     * 创建 Warehouse。
     */
    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mdm:warehouse:write')")
    public Result<WarehouseView> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        return Result.success(application.createWarehouse(request.plantId(), request.code(), request.name(), request.status()));
    }

    /**
     * 更新 Warehouse 名称，不接收 Code 或 Plant。
     */
    @PutMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('mdm:warehouse:write')")
    public Result<WarehouseView> updateWarehouse(@PathVariable String id, @Valid @RequestBody UpdateNameRequest request) {
        return Result.success(application.updateWarehouse(id, request.name(), request.version()));
    }

    /**
     * 查询 Warehouse 详情。
     */
    @GetMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('mdm:warehouse:read')")
    public Result<WarehouseView> getWarehouse(@PathVariable String id) {
        return Result.success(application.getWarehouse(id));
    }

    /**
     * 可按 plantId 分页查询 Warehouse。
     *
     * @param pageQuery 包含可选 Plant ID 和分页信息的唯一请求体
     * @return 统一 Warehouse 分页结果
     */
    @PostMapping("/warehouses/search")
    @PreAuthorize("hasAuthority('mdm:warehouse:read')")
    public Result<PageResult<WarehouseView>> pageWarehouses(
            @RequestBody PageQuery<WarehousePageParams> pageQuery) {
        return Result.success(application.pageWarehouses(pageQuery));
    }

    /**
     * 启用 Warehouse，不级联修改区域。
     */
    @PatchMapping("/warehouses/{id}/enable")
    @PreAuthorize("hasAuthority('mdm:warehouse:write')")
    public Result<WarehouseView> enableWarehouse(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.enableWarehouse(id, request.version()));
    }

    /**
     * 停用 Warehouse，不级联修改区域。
     */
    @PatchMapping("/warehouses/{id}/disable")
    @PreAuthorize("hasAuthority('mdm:warehouse:write')")
    public Result<WarehouseView> disableWarehouse(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.disableWarehouse(id, request.version()));
    }

    /**
     * 创建 WarehouseArea。
     */
    @PostMapping("/warehouse-areas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('mdm:warehouse:write')")
    public Result<WarehouseAreaView> createWarehouseArea(@Valid @RequestBody CreateWarehouseAreaRequest request) {
        return Result.success(application.createWarehouseArea(request.warehouseId(), request.code(), request.name(), request.status()));
    }

    /**
     * 更新 WarehouseArea 名称。
     */
    @PutMapping("/warehouse-areas/{id}")
    @PreAuthorize("hasAuthority('mdm:warehouse:write')")
    public Result<WarehouseAreaView> updateWarehouseArea(@PathVariable String id, @Valid @RequestBody UpdateNameRequest request) {
        return Result.success(application.updateWarehouseArea(id, request.name(), request.version()));
    }

    /**
     * 查询 WarehouseArea 详情。
     */
    @GetMapping("/warehouse-areas/{id}")
    @PreAuthorize("hasAuthority('mdm:warehouse:read')")
    public Result<WarehouseAreaView> getWarehouseArea(@PathVariable String id) {
        return Result.success(application.getWarehouseArea(id));
    }

    /**
     * 可按 warehouseId 分页查询 WarehouseArea。
     *
     * @param pageQuery 包含可选 Warehouse ID 和分页信息的唯一请求体
     * @return 统一 WarehouseArea 分页结果
     */
    @PostMapping("/warehouse-areas/search")
    @PreAuthorize("hasAuthority('mdm:warehouse:read')")
    public Result<PageResult<WarehouseAreaView>> pageWarehouseAreas(
            @RequestBody PageQuery<WarehouseAreaPageParams> pageQuery) {
        return Result.success(application.pageWarehouseAreas(pageQuery));
    }

    /**
     * 启用 WarehouseArea。
     */
    @PatchMapping("/warehouse-areas/{id}/enable")
    @PreAuthorize("hasAuthority('mdm:warehouse:write')")
    public Result<WarehouseAreaView> enableWarehouseArea(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.enableWarehouseArea(id, request.version()));
    }

    /**
     * 停用 WarehouseArea。
     */
    @PatchMapping("/warehouse-areas/{id}/disable")
    @PreAuthorize("hasAuthority('mdm:warehouse:write')")
    public Result<WarehouseAreaView> disableWarehouseArea(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.disableWarehouseArea(id, request.version()));
    }

    /**
     * Warehouse 创建请求。
     */
    public record CreateWarehouseRequest(@NotBlank @Size(max = 19) String plantId,
                                         @NotBlank @Size(max = 64) String code,
                                         @NotBlank @Size(max = 200) String name, @NotBlank String status) {
    }

    /**
     * WarehouseArea 创建请求。
     */
    public record CreateWarehouseAreaRequest(@NotBlank @Size(max = 19) String warehouseId,
                                             @NotBlank @Size(max = 64) String code,
                                             @NotBlank @Size(max = 200) String name, @NotBlank String status) {
    }

    /**
     * 只更新名称和 Version 的请求，不允许修改 Code 或父级。
     */
    public record UpdateNameRequest(@NotBlank @Size(max = 200) String name, @NotNull Long version) {
    }

    /**
     * 启停请求。
     */
    public record VersionRequest(@NotNull Long version) {
    }
}
