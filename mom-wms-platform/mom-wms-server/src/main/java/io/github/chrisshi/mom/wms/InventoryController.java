package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.webmvc.response.Result;
import io.github.chrisshi.mom.wms.application.InventoryQueryService;
import io.github.chrisshi.mom.wms.application.InventoryQueryService.DetailFilter;
import io.github.chrisshi.mom.wms.application.InventoryQueryService.DetailRow;
import io.github.chrisshi.mom.wms.application.InventoryQueryService.OverviewFilter;
import io.github.chrisshi.mom.wms.application.InventoryQueryService.OverviewRow;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** WMS 实时库存只读 HTTP 边界；没有任意库存增减 API。 */
@RestController
@RequestMapping("/inventory")
public class InventoryController {
    private final InventoryQueryService query;

    /** @param query 库存实时查询用例 */
    public InventoryController(InventoryQueryService query) { this.query = query; }

    /** 按 Material 返回实时汇总；必须持有 WMS 库存读权限。 */
    @PostMapping("/overview/search")
    @PreAuthorize("hasAuthority('wms:inventory:read')")
    public Result<PageResult<OverviewRow>> overview(@RequestBody PageQuery<OverviewFilter> request) {
        return Result.success(query.overview(request));
    }

    /** 返回实时库存维度明细；默认排除归零余额。 */
    @PostMapping("/details/search")
    @PreAuthorize("hasAuthority('wms:inventory:read')")
    public Result<PageResult<DetailRow>> details(@RequestBody PageQuery<DetailFilter> request) {
        return Result.success(query.details(request));
    }
}
