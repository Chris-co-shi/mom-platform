package io.github.chrisshi.mom.wms.controller;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.webmvc.response.Result;
import io.github.chrisshi.mom.wms.application.OpeningInventoryApplication;
import io.github.chrisshi.mom.wms.application.OpeningInventoryApplication.Detail;
import io.github.chrisshi.mom.wms.application.OpeningInventoryApplication.LineInput;
import io.github.chrisshi.mom.wms.application.OpeningInventoryApplication.ListFilter;
import io.github.chrisshi.mom.wms.application.OpeningInventoryApplication.PostedResult;
import io.github.chrisshi.mom.wms.application.OpeningInventoryApplication.Document;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * WMS 期初库存建账 HTTP 边界，负责协议绑定和权限检查。
 * <p>不承载库存规则或事务；真正的授权由 Resource Server 与 Application 服务共同执行。</p>
 */
@RestController
@RequestMapping("/opening-documents")
@Validated
public class OpeningInventoryController {
    private final OpeningInventoryApplication application;

    /**
     * @param application 期初建账用例编排
     */
    public OpeningInventoryController(OpeningInventoryApplication application) {
        this.application = application;
    }

    /**
     * 查询期初建账单列表；要求期初读取权限。
     *
     * @param request 服务端分页请求 @return 建账单分页视图
     */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('wms:opening:read')")
    public Result<PageResult<Document>> list(@Valid @RequestBody PageQuery<ListFilter> request) {
        return Result.success(application.list(request));
    }

    /**
     * 查询单据及其库存明细。
     *
     * @param id 期初单技术 ID @return 单据及明细
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('wms:opening:read')")
    public Result<Detail> get(@PathVariable String id) {
        return Result.success(application.get(id));
    }

    /**
     * 删除未过账草稿；已过账期初单不可删除。
     *
     * @param id 期初单技术 ID @return 成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('wms:opening:write')")
    public Result<Void> delete(@PathVariable String id) {
        application.deleteDraft(id);
        return Result.success();
    }

    /**
     * 创建指定仓库的唯一期初草稿。
     *
     * @param request 目标仓库 ID @return 新建期初单
     */
    @PostMapping
    @PreAuthorize("hasAuthority('wms:opening:write')")
    public Result<Detail> create(@Valid @RequestBody CreateRequest request) {
        return Result.success(application.create(request.warehouseId()));
    }

    /**
     * 新增期初库存明细。
     *
     * @param id 期初单 ID @param request 库存数量及主数据 ID @return 保存后的单据
     */
    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('wms:opening:write')")
    public Result<Detail> addLine(@PathVariable String id, @Valid @RequestBody LineInput request) {
        return Result.success(application.addLine(id, request));
    }

    /**
     * 编辑期初库存明细。
     *
     * @param id 期初单 ID @param lineId 明细 ID @param request 新明细内容 @return 保存后的单据
     */
    @PutMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAuthority('wms:opening:write')")
    public Result<Detail> editLine(@PathVariable String id, @PathVariable String lineId,
                                   @Valid @RequestBody LineInput request) {
        return Result.success(application.editLine(id, lineId, request));
    }

    /**
     * 删除期初库存明细。
     *
     * @param id 期初单 ID @param lineId 明细 ID @return 删除后的单据
     */
    @DeleteMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAuthority('wms:opening:write')")
    public Result<Detail> deleteLine(@PathVariable String id, @PathVariable String lineId) {
        return Result.success(application.deleteLine(id, lineId));
    }

    /**
     * 原子确认并过账全部期初明细。
     *
     * @param id 期初单 ID @return 已过账单及库存交易 ID
     */
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('wms:opening:post')")
    public Result<PostedResult> post(@PathVariable String id) {
        return Result.success(application.post(id));
    }

    /**
     * 正式启用已核对的仓库库存。
     *
     * @param warehouseId MDM 仓库稳定 ID @return 启用后的 WMS 库存状态
     */
    @PostMapping("/warehouses/{warehouseId}/activate")
    @PreAuthorize("hasAuthority('wms:opening:activate')")
    public Result<String> activate(@PathVariable String warehouseId) {
        return Result.success(application.activate(warehouseId));
    }

    /**
     * 创建期初单所需的目标仓库稳定 ID。
     *
     * @param warehouseId MDM 仓库稳定 ID
     */
    public record CreateRequest(@NotBlank @Size(max = 19) String warehouseId) {
    }
}
