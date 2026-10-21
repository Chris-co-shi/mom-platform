package io.github.chrisshi.mom.system.controller.dictionary;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryApplication;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.ChangeStatus;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.CreateType;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.TypeView;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.UpdateType;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 字典类型管理 HTTP 边界。 */
@RestController
@RequestMapping("/dictionary-types")
@RequiredArgsConstructor
public class DictionaryTypeController {
    private final DictionaryApplication application;


    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('system:dictionary:write')")
    public Result<TypeView> create(@RequestBody CreateTypeRequest request) {
        return Result.success(application.createType(new CreateType(request.code(), request.name(), request.enabled(), request.description())));
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('system:dictionary:write')")
    public Result<TypeView> update(@PathVariable String id, @RequestBody UpdateTypeRequest request) {
        return Result.success(application.updateType(id, new UpdateType(request.name(), request.description(), request.version())));
    }
    @PatchMapping("/change-status/{id}") @PreAuthorize("hasAuthority('system:dictionary:write')")
    public Result<TypeView> changeStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        return Result.success(application.changeTypeStatus(id, new ChangeStatus(request.enabled(), request.version())));
    }
    @GetMapping @PreAuthorize("hasAuthority('system:dictionary:read')")
    public Result<List<TypeView>> list() { return Result.success(application.listTypes()); }

    public record CreateTypeRequest(String code, String name, Boolean enabled, String description) {}
    public record UpdateTypeRequest(String name, String description, Long version) {}
    public record StatusRequest(Boolean enabled, Long version) {}
}
