package io.github.chrisshi.mom.system.web.dictionary;

import io.github.chrisshi.mom.system.api.ResolvedSystemDictionaryItem;
import io.github.chrisshi.mom.system.api.SystemDictionaryItemOption;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.CreateDictionaryCommand;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.CreateItemCommand;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.DictionaryPageQuery;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.DictionaryPageView;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.DictionaryView;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.ItemPageQuery;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.ItemPageView;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.ItemView;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.StatusCommand;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.UpdateDictionaryCommand;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.UpdateItemCommand;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * System 非权威通用字典的 HTTP 入站 Adapter。
 *
 * <p>Controller 只映射 HTTP 与 Dictionary Application Command/Query，不依赖 Domain、Mapper、Entity
 * 或 Repository。全部端点必须认证；读取引用 IAM 的 system:dictionary:read，写入引用
 * system:dictionary:write。Permission 仅来自 JWT Authority，System 不保存其定义或分配。</p>
 */
@RestController
@RequestMapping("/api/system")
public class SystemDictionaryController {
    private final SystemDictionaryApplicationService service;

    public SystemDictionaryController(SystemDictionaryApplicationService service) {
        this.service = service;
    }

    /** 创建稳定 Code 字典；成功返回 201，不接受客户端审计字段。 */
    @PostMapping("/admin/dictionaries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:dictionary:write')")
    public DictionaryView createDictionary(@RequestBody CreateDictionaryRequest request) {
        return service.createDictionary(new CreateDictionaryCommand(request.dictionaryCode(),
                request.dictionaryName(), request.description(), request.enabled()));
    }

    /** 使用 Version 更新字典名称与说明，不提供 Rename。 */
    @PutMapping("/admin/dictionaries/{id}")
    @PreAuthorize("hasAuthority('system:dictionary:write')")
    public DictionaryView updateDictionary(@PathVariable String id, @RequestBody UpdateDictionaryRequest request) {
        return service.updateDictionary(id, new UpdateDictionaryCommand(
                request.dictionaryName(), request.description(), request.version()));
    }

    /** 使用 Version 启停字典，不级联修改 Item 状态。 */
    @PatchMapping("/admin/dictionaries/{id}/status")
    @PreAuthorize("hasAuthority('system:dictionary:write')")
    public DictionaryView changeDictionaryStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        return service.changeDictionaryStatus(id, new StatusCommand(request.enabled(), request.version()));
    }

    /** 按 System 内部 ID 读取字典管理视图。 */
    @GetMapping("/admin/dictionaries/{id}")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    public DictionaryView getDictionary(@PathVariable String id) {
        return service.getDictionary(id);
    }

    /** 按 Code/状态精确过滤并固定排序分页。 */
    @GetMapping("/admin/dictionaries")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    public DictionaryPageView pageDictionaries(
            @RequestParam(required = false) String dictionaryCode,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.pageDictionaries(new DictionaryPageQuery(dictionaryCode, enabled, page, size));
    }

    /** 在指定字典下创建稳定 Item Code；成功返回 201。 */
    @PostMapping("/admin/dictionaries/{dictionaryId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:dictionary:write')")
    public ItemView createItem(
            @PathVariable String dictionaryId, @RequestBody CreateItemRequest request) {
        return service.createItem(dictionaryId, new CreateItemCommand(request.itemCode(), request.itemLabel(),
                request.sortOrder(), request.description(), request.enabled()));
    }

    /** 使用 Version 更新 Item Label、排序与说明，不提供 Rename 或换父字典。 */
    @PutMapping("/admin/dictionaries/{dictionaryId}/items/{itemId}")
    @PreAuthorize("hasAuthority('system:dictionary:write')")
    public ItemView updateItem(
            @PathVariable String dictionaryId,
            @PathVariable String itemId,
            @RequestBody UpdateItemRequest request) {
        return service.updateItem(dictionaryId, itemId, new UpdateItemCommand(
                request.itemLabel(), request.sortOrder(), request.description(), request.version()));
    }

    /** 使用 Version 启停 Item，不修改字典状态。 */
    @PatchMapping("/admin/dictionaries/{dictionaryId}/items/{itemId}/status")
    @PreAuthorize("hasAuthority('system:dictionary:write')")
    public ItemView changeItemStatus(
            @PathVariable String dictionaryId,
            @PathVariable String itemId,
            @RequestBody StatusRequest request) {
        return service.changeItemStatus(dictionaryId, itemId,
                new StatusCommand(request.enabled(), request.version()));
    }

    /** 按父字典与 Item 内部 ID 读取管理视图。 */
    @GetMapping("/admin/dictionaries/{dictionaryId}/items/{itemId}")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    public ItemView getItem(@PathVariable String dictionaryId, @PathVariable String itemId) {
        return service.getItem(dictionaryId, itemId);
    }

    /** 查询单字典 Item 管理分页；固定排序，不支持 Label 模糊搜索。 */
    @GetMapping("/admin/dictionaries/{dictionaryId}/items")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    public ItemPageView pageItems(
            @PathVariable String dictionaryId,
            @RequestParam(required = false) String itemCode,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.pageItems(dictionaryId, new ItemPageQuery(itemCode, enabled, page, size));
    }

    /** 返回字典和 Item 均启用的固定排序选择项。 */
    @GetMapping("/dictionaries/{dictionaryCode}/items")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    public List<SystemDictionaryItemOption> activeItems(@PathVariable String dictionaryCode) {
        return service.activeItems(dictionaryCode);
    }

    /** 兼容读取单项；禁用记录仍返回并显式标记 effectiveEnabled。 */
    @GetMapping("/dictionaries/{dictionaryCode}/items/{itemCode}")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    public ResolvedSystemDictionaryItem resolveItem(
            @PathVariable String dictionaryCode, @PathVariable String itemCode) {
        return service.resolveItem(dictionaryCode, itemCode);
    }

    /** 创建字典请求；不包含数据库 ID 或审计字段。 */
    public record CreateDictionaryRequest(
            String dictionaryCode, String dictionaryName, String description, Boolean enabled) {
    }

    /** 更新字典请求；dictionaryCode 不在协议中。 */
    public record UpdateDictionaryRequest(String dictionaryName, String description, Long version) {
    }

    /** 创建 Item 请求；dictionaryId 来自路径，协议无 Metadata/Tree/Alias。 */
    public record CreateItemRequest(
            String itemCode, String itemLabel, Integer sortOrder, String description, Boolean enabled) {
    }

    /** 更新 Item 请求；itemCode 和 dictionaryId 不在协议中。 */
    public record UpdateItemRequest(String itemLabel, Integer sortOrder, String description, Long version) {
    }

    /** 字典与 Item 共用的版本化启停请求。 */
    public record StatusRequest(Boolean enabled, Long version) {
    }
}
