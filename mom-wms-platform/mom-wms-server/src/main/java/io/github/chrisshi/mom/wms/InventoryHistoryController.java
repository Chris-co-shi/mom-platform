package io.github.chrisshi.mom.wms;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import io.github.chrisshi.mom.webmvc.response.Result;
import io.github.chrisshi.mom.wms.application.InventoryHistoryApplication;
import io.github.chrisshi.mom.wms.application.InventoryHistoryApplication.EntryView;
import io.github.chrisshi.mom.wms.application.InventoryHistoryApplication.Filter;
import io.github.chrisshi.mom.wms.application.InventoryHistoryApplication.TransactionView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 库存历史只读 HTTP 边界；搜索与交易详情均要求库存读取权限。 */
@RestController
@RequestMapping("/inventory")
public class InventoryHistoryController {
    private final InventoryHistoryApplication history;

    /** @param history 不可变历史查询用例 */
    public InventoryHistoryController(InventoryHistoryApplication history) { this.history = history; }

    /**
     * 分页搜索符合条件的分录，单笔交易的其他分录不并入当前页。
     * @param request 分页及历史筛选条件
     * @return 分录历史页
     */
    @PostMapping("/history/search")
    @PreAuthorize("hasAuthority('wms:inventory:read')")
    public Result<PageResult<EntryView>> search(@RequestBody PageQuery<Filter> request) {
        return Result.success(history.search(request));
    }

    /**
     * 返回指定交易的全部分录，不沿用列表筛选。
     * @param transactionId 交易标识
     * @return 交易与完整分录；不存在时返回 404
     */
    @GetMapping("/transactions/{transactionId}")
    @PreAuthorize("hasAuthority('wms:inventory:read')")
    public Result<TransactionView> transaction(@PathVariable String transactionId) {
        TransactionView view = history.transaction(transactionId);
        if (view == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return Result.success(view);
    }
}
