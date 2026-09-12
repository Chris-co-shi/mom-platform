package io.github.chrisshi.mom.mdm.controller;

import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.mdm.application.LocationMasterDataApplication;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.LocationTypeView;
import io.github.chrisshi.mom.mdm.application.MdmMasterDataViews.LocationView;
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
 * LocationType 和统一可寻址 Location 的 HTTP 协议边界。
 *
 * <p>Controller 不解释动态 Type Code，也不暴露占用、预留、库存、容器或 AGV 字段。所有端点要求认证，
 * 引用校验、事务和乐观并发由 Application 负责。</p>
 */
@RestController
@RequestMapping("/api/mdm")
@PreAuthorize("isAuthenticated()")
public class LocationMasterDataController {
    private final LocationMasterDataApplication application;

    /**
     * @param application 位置主数据用例入口
     */
    public LocationMasterDataController(LocationMasterDataApplication application) {
        this.application = application;
    }

    /**
     * 创建动态 LocationType。
     */
    @PostMapping("/location-types")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<LocationTypeView> createLocationType(@Valid @RequestBody CreateLocationTypeRequest request) {
        return Result.success(application.createLocationType(request.code(), request.nameZh(), request.nameEn(), request.status()));
    }

    /**
     * 更新 LocationType 名称，不接收 Code。
     */
    @PutMapping("/location-types/{id}")
    public Result<LocationTypeView> updateLocationType(@PathVariable String id, @Valid @RequestBody UpdateNameRequest request) {
        return Result.success(application.updateLocationType(id, request.nameZh(), request.nameEn(), request.version()));
    }

    /**
     * 查询 LocationType 详情。
     */
    @GetMapping("/location-types/{id}")
    public Result<LocationTypeView> getLocationType(@PathVariable String id) {
        return Result.success(application.getLocationType(id));
    }

    /**
     * 分页查询 LocationType。
     */
    @GetMapping("/location-types")
    public Result<PageResult<LocationTypeView>> pageLocationTypes(@RequestParam(defaultValue = "1") @Positive long pageNo, @RequestParam(defaultValue = "20") @Positive long pageSize) {
        return Result.success(application.pageLocationTypes(pageNo, pageSize));
    }

    /**
     * 启用 LocationType，不执行 Type Code 分支。
     */
    @PatchMapping("/location-types/{id}/enable")
    public Result<LocationTypeView> enableLocationType(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.enableLocationType(id, request.version()));
    }

    /**
     * 停用 LocationType，不级联 Location。
     */
    @PatchMapping("/location-types/{id}/disable")
    public Result<LocationTypeView> disableLocationType(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.disableLocationType(id, request.version()));
    }

    /**
     * 创建 Location；warehouseAreaId 可以为空。
     */
    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<LocationView> createLocation(@Valid @RequestBody CreateLocationRequest request) {
        return Result.success(application.createLocation(request.plantId(), request.warehouseAreaId(), request.locationTypeId(), request.code(), request.nameZh(), request.nameEn(), request.status()));
    }

    /**
     * 更新 Location 名称，不接收 Code 或引用字段。
     */
    @PutMapping("/locations/{id}")
    public Result<LocationView> updateLocation(@PathVariable String id, @Valid @RequestBody UpdateNameRequest request) {
        return Result.success(application.updateLocation(id, request.nameZh(), request.nameEn(), request.version()));
    }

    /**
     * 查询 Location 详情。
     */
    @GetMapping("/locations/{id}")
    public Result<LocationView> getLocation(@PathVariable String id) {
        return Result.success(application.getLocation(id));
    }

    /**
     * 可按 plantId、warehouseAreaId 组合分页查询 Location。
     */
    @GetMapping("/locations")
    public Result<PageResult<LocationView>> pageLocations(@RequestParam(required = false) String plantId,
                                                          @RequestParam(required = false) String warehouseAreaId,
                                                          @RequestParam(defaultValue = "1") @Positive long pageNo,
                                                          @RequestParam(defaultValue = "20") @Positive long pageSize) {
        return Result.success(application.pageLocations(plantId, warehouseAreaId, pageNo, pageSize));
    }

    /**
     * 启用 Location。
     */
    @PatchMapping("/locations/{id}/enable")
    public Result<LocationView> enableLocation(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.enableLocation(id, request.version()));
    }

    /**
     * 停用 Location，不改变运行时事实。
     */
    @PatchMapping("/locations/{id}/disable")
    public Result<LocationView> disableLocation(@PathVariable String id, @Valid @RequestBody VersionRequest request) {
        return Result.success(application.disableLocation(id, request.version()));
    }

    /**
     * LocationType 创建请求。
     */
    public record CreateLocationTypeRequest(@NotBlank @Size(max = 64) String code,
                                            @NotBlank @Size(max = 200) String nameZh, @Size(max = 200) String nameEn,
                                            @NotBlank String status) {
    }

    /**
     * Location 创建请求；warehouseAreaId 可空，其余引用必填。
     */
    public record CreateLocationRequest(@NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 200) String nameZh,
                                        @Size(max = 200) String nameEn, @NotBlank @Size(max = 19) String plantId,
                                        @Size(max = 19) String warehouseAreaId,
                                        @NotBlank @Size(max = 19) String locationTypeId, @NotBlank String status) {
    }

    /**
     * 只更新名称与 Version 的请求，不允许修改 Code 或引用。
     */
    public record UpdateNameRequest(@NotBlank @Size(max = 200) String nameZh, @Size(max = 200) String nameEn,
                                    @NotNull Long version) {
    }

    /**
     * 启停请求。
     */
    public record VersionRequest(@NotNull Long version) {
    }
}
