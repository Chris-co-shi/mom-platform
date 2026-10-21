package io.github.chrisshi.mom.system.controller.dictionary;

import lombok.RequiredArgsConstructor;
import io.github.chrisshi.mom.system.api.ResolvedSystemDictionaryItem;
import io.github.chrisshi.mom.system.api.SystemDictionaryItemOption;
import io.github.chrisshi.mom.system.application.dictionary.DictionaryApplication;
import io.github.chrisshi.mom.webmvc.response.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 字典运行时只读解析 HTTP 边界。 */
@RestController
@RequestMapping("/dictionaries")
@RequiredArgsConstructor
public class RuntimeDictionaryController {
    private final DictionaryApplication application;


    @GetMapping("/{dictionaryCode}/items") @PreAuthorize("hasAuthority('system:dictionary:read')")
    public Result<List<SystemDictionaryItemOption>> activeItems(@PathVariable String dictionaryCode) {
        return Result.success(application.activeItems(dictionaryCode));
    }
    @GetMapping("/{dictionaryCode}/items/{key}") @PreAuthorize("hasAuthority('system:dictionary:read')")
    public Result<ResolvedSystemDictionaryItem> resolveItem(@PathVariable String dictionaryCode, @PathVariable String key) {
        return Result.success(application.resolveItem(dictionaryCode, key));
    }
}
