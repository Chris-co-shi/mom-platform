package io.github.chrisshi.mom.system.controller.dictionary;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryApplication;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.ChangeStatus;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.CreateItem;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.ItemView;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.UpdateItem;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 字典条目管理 HTTP 边界；父类型 ID 作为条目的所属关系参数。 */
@RestController
@RequestMapping("/dictionary-items")
@RequiredArgsConstructor
public class DictionaryItemController {
    private final DictionaryApplication application;


    @PostMapping("/{typeId}") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('system:dictionary:write')")
    public Result<ItemView> create(@PathVariable String typeId, @RequestBody CreateItemRequest request) {
        return Result.success(application.createItem(typeId, new CreateItem(request.key(), request.value(), request.sortOrder(), request.enabled(), request.description())));
    }
    @PutMapping("/{typeId}/{itemId}") @PreAuthorize("hasAuthority('system:dictionary:write')")
    public Result<ItemView> update(@PathVariable String typeId, @PathVariable String itemId, @RequestBody UpdateItemRequest request) {
        return Result.success(application.updateItem(typeId, itemId, new UpdateItem(request.value(), request.sortOrder(), request.description(), request.version())));
    }
    @PatchMapping("/change-status/{typeId}/{itemId}") @PreAuthorize("hasAuthority('system:dictionary:write')")
    public Result<ItemView> changeStatus(@PathVariable String typeId, @PathVariable String itemId, @RequestBody StatusRequest request) {
        return Result.success(application.changeItemStatus(typeId, itemId, new ChangeStatus(request.enabled(), request.version())));
    }
    @GetMapping("/{typeId}") @PreAuthorize("hasAuthority('system:dictionary:read')")
    public Result<List<ItemView>> list(@PathVariable String typeId) { return Result.success(application.listItems(typeId)); }

    public record CreateItemRequest(String key, String value, Integer sortOrder, Boolean enabled, String description) {}
    public record UpdateItemRequest(String value, Integer sortOrder, String description, Long version) {}
    public record StatusRequest(Boolean enabled, Long version) {}
}
