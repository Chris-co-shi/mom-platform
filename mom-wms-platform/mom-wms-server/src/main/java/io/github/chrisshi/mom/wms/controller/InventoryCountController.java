package io.github.chrisshi.mom.wms.controller;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.webmvc.response.Result;
import io.github.chrisshi.mom.wms.application.InventoryCountApplication;
import io.github.chrisshi.mom.wms.application.InventoryCountApplication.*;
import io.github.chrisshi.mom.wms.infrastructure.query.InventoryCountQueryMapper.Summary;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** WMS 库存盘点 HTTP 边界；最终权限在 Resource Server 校验。 */
@RestController
@RequestMapping("/inventory-counts")
public class InventoryCountController {
    private final InventoryCountApplication application;
    /** @param application 盘点业务用例 */
    public InventoryCountController(InventoryCountApplication application) { this.application=application; }
    /** 服务端分页查询任务。 */
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('wms:inventory-count:read')")
    public Result<PageResult<Summary>> list(@Valid @RequestBody PageQuery<Filter> query) {
        return Result.success(application.list(query));
    }
    /** 查询盘点详情与账面、实盘行。 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('wms:inventory-count:read')")
    public Result<Detail> get(@PathVariable String id) { return Result.success(application.get(id)); }
    /** 按创建请求键查询当前操作人的已提交结果，空结果仍需继续阻断重放。 */
    @GetMapping("/by-request-key/{requestKey}")
    @PreAuthorize("hasAuthority('wms:inventory-count:read')")
    public Result<Detail> byRequestKey(@PathVariable String requestKey) {
        return Result.success(application.findCreatedByRequestKey(requestKey));
    }
    /** 创建任务并分配业务编号。 */
    @PostMapping
    @PreAuthorize("hasAuthority('wms:inventory-count:create')")
    public Result<Detail> create(@Valid @RequestBody CreateRequest request) { return Result.success(application.create(request)); }
    /** 修改草稿范围。 */
    @PutMapping("/{id}/scope")
    @PreAuthorize("hasAuthority('wms:inventory-count:write')")
    public Result<Detail> scope(@PathVariable String id,@Valid @RequestBody ScopeRequest request) {
        return Result.success(application.editScope(id,request));
    }
    /** 开始盘点并锁定整仓、生成快照。 */
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('wms:inventory-count:start')")
    public Result<Detail> start(@PathVariable String id) { return Result.success(application.start(id)); }
    /** 保存某完整维度的实盘数量。 */
    @PutMapping("/{id}/lines/{lineId}/count")
    @PreAuthorize("hasAuthority('wms:inventory-count:count')")
    public Result<Detail> count(@PathVariable String id,@PathVariable String lineId,@Valid @RequestBody CountRequest request) {
        return Result.success(application.count(id,lineId,request));
    }
    /** 录入符合任务范围的账外实物。 */
    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('wms:inventory-count:count')")
    public Result<Detail> addExtra(@PathVariable String id,@Valid @RequestBody ExtraRequest request) {
        return Result.success(application.addExtra(id,request));
    }
    /** 确认全部实盘差异。 */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('wms:inventory-count:confirm')")
    public Result<Detail> confirm(@PathVariable String id) { return Result.success(application.confirm(id)); }
    /** 在一个 WMS 本地事务内执行全部差异调整。 */
    @PostMapping("/{id}/adjust")
    @PreAuthorize("hasAuthority('wms:inventory-count:adjust')")
    public Result<Detail> adjust(@PathVariable String id) { return Result.success(application.adjust(id)); }
    /** 取消草稿或未确认盘点，并释放锁。 */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('wms:inventory-count:write')")
    public Result<Detail> cancel(@PathVariable String id) { return Result.success(application.cancel(id)); }
}
