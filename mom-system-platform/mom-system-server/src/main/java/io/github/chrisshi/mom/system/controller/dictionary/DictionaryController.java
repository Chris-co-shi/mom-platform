package io.github.chrisshi.mom.system.controller.dictionary;

import io.github.chrisshi.mom.system.api.ResolvedSystemDictionaryItem;
import io.github.chrisshi.mom.system.api.SystemDictionaryItemOption;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryApplication;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.ChangeStatus;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.CreateItem;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.CreateType;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.ItemView;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.TypeView;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.UpdateItem;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.UpdateType;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * System V1 Dictionary 的 HTTP 入站边界。
 *
 * <p>Controller 只绑定路径与请求体、执行方法授权并调用 {@link DictionaryApplication}，不依赖 Mapper、
 * Entity 或事务。管理端使用内部 String ID，Runtime 消费端只使用稳定 Dictionary Code/Key。认证失败由
 * Security Filter Chain 处理，业务与输入异常由统一 System ControllerAdvice 本地化。</p>
 */
@RestController
@RequestMapping("/api/system")
public class DictionaryController {
    private final DictionaryApplication application;

    /** @param application Dictionary 用例入口 */
    public DictionaryController(DictionaryApplication application) {
        this.application = application;
    }

    /** 创建字典类型并返回 201。 */
    @PostMapping("/admin/dictionaries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:dictionary:write')")
    public Result<TypeView> createType(@RequestBody CreateTypeRequest request) {
        return Result.success(application.createType(new CreateType(
                request.code(), request.name(), request.enabled(), request.description())));
    }

    /** 更新字典类型名称和说明，不提供 Code Rename。 */
    @PutMapping("/admin/dictionaries/{id}")
    @PreAuthorize("hasAuthority('system:dictionary:write')")
    public Result<TypeView> updateType(@PathVariable String id, @RequestBody UpdateTypeRequest request) {
        return Result.success(application.updateType(
                id, new UpdateType(request.name(), request.description(), request.version())));
    }

    /** 版本化启停字典类型。 */
    @PatchMapping("/admin/dictionaries/{id}/status")
    @PreAuthorize("hasAuthority('system:dictionary:write')")
    public Result<TypeView> changeTypeStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        return Result.success(application.changeTypeStatus(
                id, new ChangeStatus(request.enabled(), request.version())));
    }

    /** 返回字典类型管理列表。 */
    @GetMapping("/admin/dictionaries")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    public Result<List<TypeView>> listTypes() {
        return Result.success(application.listTypes());
    }

    /** 在指定类型下创建稳定 Key 条目并返回 201。 */
    @PostMapping("/admin/dictionaries/{typeId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:dictionary:write')")
    public Result<ItemView> createItem(@PathVariable String typeId, @RequestBody CreateItemRequest request) {
        return Result.success(application.createItem(typeId, new CreateItem(
                request.key(), request.value(), request.sortOrder(), request.enabled(), request.description())));
    }

    /** 更新条目展示值、排序与说明，不修改稳定 Key。 */
    @PutMapping("/admin/dictionaries/{typeId}/items/{itemId}")
    @PreAuthorize("hasAuthority('system:dictionary:write')")
    public Result<ItemView> updateItem(
            @PathVariable String typeId,
            @PathVariable String itemId,
            @RequestBody UpdateItemRequest request) {
        return Result.success(application.updateItem(typeId, itemId, new UpdateItem(
                request.value(), request.sortOrder(), request.description(), request.version())));
    }

    /** 版本化启停条目。 */
    @PatchMapping("/admin/dictionaries/{typeId}/items/{itemId}/status")
    @PreAuthorize("hasAuthority('system:dictionary:write')")
    public Result<ItemView> changeItemStatus(
            @PathVariable String typeId,
            @PathVariable String itemId,
            @RequestBody StatusRequest request) {
        return Result.success(application.changeItemStatus(typeId, itemId,
                new ChangeStatus(request.enabled(), request.version())));
    }

    /** 返回指定字典的全部条目管理列表。 */
    @GetMapping("/admin/dictionaries/{typeId}/items")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    public Result<List<ItemView>> listItems(@PathVariable String typeId) {
        return Result.success(application.listItems(typeId));
    }

    /** 返回可用于新选择的启用条目。 */
    @GetMapping("/dictionaries/{dictionaryCode}/items")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    public Result<List<SystemDictionaryItemOption>> activeItems(@PathVariable String dictionaryCode) {
        return Result.success(application.activeItems(dictionaryCode));
    }

    /** 解析包括已禁用条目在内的历史稳定 Key。 */
    @GetMapping("/dictionaries/{dictionaryCode}/items/{key}")
    @PreAuthorize("hasAuthority('system:dictionary:read')")
    public Result<ResolvedSystemDictionaryItem> resolveItem(
            @PathVariable String dictionaryCode,
            @PathVariable String key) {
        return Result.success(application.resolveItem(dictionaryCode, key));
    }

    /** 创建字典类型请求。 */
    public record CreateTypeRequest(String code, String name, Boolean enabled, String description) {
    }

    /** 更新字典类型请求。 */
    public record UpdateTypeRequest(String name, String description, Long version) {
    }

    /** 创建字典条目请求。 */
    public record CreateItemRequest(
            String key, String value, Integer sortOrder, Boolean enabled, String description) {
    }

    /** 更新字典条目请求。 */
    public record UpdateItemRequest(String value, Integer sortOrder, String description, Long version) {
    }

    /** 字典类型与条目共用的版本化启停请求。 */
    public record StatusRequest(Boolean enabled, Long version) {
    }
}
