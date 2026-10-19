package io.github.chrisshi.mom.wms.controller;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.webmvc.response.Result;
import io.github.chrisshi.mom.wms.application.InventoryFreezeApplication;
import io.github.chrisshi.mom.wms.application.InventoryFreezeApplication.*;
import io.github.chrisshi.mom.wms.application.InventoryQueryService;
import io.github.chrisshi.mom.wms.application.InventoryQueryService.DetailFilter;
import io.github.chrisshi.mom.wms.application.InventoryQueryService.DetailRow;
import io.github.chrisshi.mom.wms.application.InventoryQueryService.OverviewFilter;
import io.github.chrisshi.mom.wms.application.InventoryQueryService.OverviewRow;
import io.github.chrisshi.mom.wms.application.InventoryTransferApplication;
import io.github.chrisshi.mom.wms.application.InventoryTransferApplication.TransferCommand;
import io.github.chrisshi.mom.wms.application.InventoryTransferApplication.TransferResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * WMS 实时库存、仓储冻结和同仓库库位移动 HTTP 边界；没有任意增减或质量状态修改 API。
 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {
    private final InventoryQueryService query;
    private final InventoryFreezeApplication freezes;
    private final InventoryTransferApplication transfers;

    /**
     * @param query 库存实时查询用例 @param freezes 仓储冻结与解冻用例 @param transfers 同仓库库位移动用例
     */
    public InventoryController(InventoryQueryService query, InventoryFreezeApplication freezes,
                               InventoryTransferApplication transfers) {
        this.query = query;
        this.freezes = freezes;
        this.transfers = transfers;
    }

    /**
     * 按 Material 返回实时汇总；必须持有 WMS 库存读权限。
     */
    @PostMapping("/overview/search")
    @PreAuthorize("hasAuthority('wms:inventory:read')")
    public Result<PageResult<OverviewRow>> overview(@RequestBody PageQuery<OverviewFilter> request) {
        return Result.success(query.overview(request));
    }

    /**
     * 返回实时库存维度明细；默认排除归零余额。
     */
    @PostMapping("/details/search")
    @PreAuthorize("hasAuthority('wms:inventory:read')")
    public Result<PageResult<DetailRow>> details(@RequestBody PageQuery<DetailFilter> request) {
        return Result.success(query.details(request));
    }

    /**
     * 查询选定库存维度当前所有单独有效的冻结限制。
     */
    @PostMapping("/freezes/query")
    @PreAuthorize("hasAuthority('wms:inventory:read')")
    public Result<List<FreezeView>> activeFreezes(@RequestBody FreezeLookup request) {
        return Result.success(freezes.queryActiveFreezes(request.balanceId()));
    }

    /**
     * 按指定余额执行人工冻结；操作人取自已认证 Resource Server 主体。
     */
    @PostMapping("/freezes")
    @PreAuthorize("hasAuthority('wms:inventory:freeze')")
    public Result<FreezeView> freeze(@RequestBody FreezeRequest request, Authentication authentication) {
        return Result.success(freezes.freezeInventory(new FreezeCommand(request.requestKey(), request.balanceId(),
            request.quantity(), request.reason(), request.note(), "MANUAL", request.requestKey(),
            authentication.getName())));
    }

    /**
     * 仅解除 URL 指定的单条冻结限制；仓储操作说明保留在解冻历史。
     */
    @PostMapping("/freezes/{freezeId}/unfreeze")
    @PreAuthorize("hasAuthority('wms:inventory:unfreeze')")
    public Result<UnfreezeResult> unfreeze(@PathVariable String freezeId, @RequestBody UnfreezeRequest request,
                                           Authentication authentication) {
        return Result.success(freezes.unfreezeInventory(new UnfreezeCommand(request.requestKey(), freezeId,
            request.quantity(), request.note(), "MANUAL", request.requestKey(), authentication.getName())));
    }

    /**
     * 从选中的具体余额移动至同仓库 MDM 库位；操作人取自 Resource Server 认证主体。
     */
    @PostMapping("/transfers")
    @PreAuthorize("hasAuthority('wms:inventory:transfer')")
    public Result<TransferResult> transfer(@RequestBody TransferRequest request, Authentication authentication) {
        return Result.success(transfers.transfer(new TransferCommand(request.requestKey(), request.balanceId(),
            request.targetLocationId(), request.quantity(), authentication.getName())));
    }

    /**
     * 冻结限制查询请求。
     */
    public record FreezeLookup(String balanceId) {
    }

    /**
     * 人工冻结 HTTP 请求，不接受客户端传入仓储状态或操作人。
     */
    public record FreezeRequest(String requestKey, String balanceId, BigDecimal quantity,
                                FreezeReason reason, String note) {
    }

    /**
     * 人工解冻 HTTP 请求，不接受客户端传入冻结目标或操作人。
     */
    public record UnfreezeRequest(String requestKey, BigDecimal quantity, String note) {
    }

    /**
     * 库位移动请求只包含来源余额、MDM 目标库位和数量，状态及其他库存维度由服务器确定。
     */
    public record TransferRequest(String requestKey, String balanceId, String targetLocationId, BigDecimal quantity) {
    }
}
