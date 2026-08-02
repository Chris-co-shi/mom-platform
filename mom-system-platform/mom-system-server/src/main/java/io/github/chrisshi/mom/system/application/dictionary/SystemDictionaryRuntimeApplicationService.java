package io.github.chrisshi.mom.system.application.dictionary;

import io.github.chrisshi.mom.system.api.ResolvedSystemDictionaryItem;
import io.github.chrisshi.mom.system.api.SystemDictionaryItemOption;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeCachePort;
import io.github.chrisshi.mom.system.application.runtime.SystemRuntimeChangeEventPort;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionary;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryItem;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryItemRepository;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryRepository;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryRules;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.CreateDictionaryCommand;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.CreateItemCommand;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.DictionaryView;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.ItemView;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.StatusCommand;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.UpdateDictionaryCommand;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.UpdateItemCommand;
import static io.github.chrisshi.mom.system.application.runtime.SystemRuntimeChangeEventPort.ChangeKind;

/**
 * Dictionary 的可靠 Runtime 编排入口。
 *
 * <p>该 Primary Bean 复用既有 CRUD Application Service，并在同一 System PostgreSQL 本地事务中追加不含
 * Label/正文的 Outbox 事件。Runtime 方法强制 {@link Propagation#NEVER}：先读取 PostgreSQL 字典头确认存在、
 * enabled Kill Switch 与 Version，再访问 Redis Projection；数据库不可用时不会仅凭旧 Cache 返回数据。</p>
 *
 * <p>父类仍负责 CRUD 规则、CAS、分页和管理读取。本类型不引入 Seata，不在事务中访问 Redis 或 Broker。</p>
 */
@Service
@Primary
public class SystemDictionaryRuntimeApplicationService extends SystemDictionaryApplicationService {
    private final SystemDictionaryRepository dictionaries;
    private final SystemDictionaryItemRepository items;
    private final SystemRuntimeCachePort cache;
    private final SystemRuntimeChangeEventPort events;

    public SystemDictionaryRuntimeApplicationService(
            SystemDictionaryRepository dictionaries,
            SystemDictionaryItemRepository items,
            SystemRuntimeCachePort cache,
            SystemRuntimeChangeEventPort events) {
        super(dictionaries, items);
        this.dictionaries = Objects.requireNonNull(dictionaries, "dictionaries");
        this.items = Objects.requireNonNull(items, "items");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** 字典行与 Outbox 在同一事务提交。 */
    @Override
    @Transactional
    public DictionaryView createDictionary(CreateDictionaryCommand command) {
        DictionaryView view = super.createDictionary(command);
        appendDictionary(view, null, ChangeKind.CREATED);
        return view;
    }

    /** 字典更新与 Outbox 在同一事务提交。 */
    @Override
    @Transactional
    public DictionaryView updateDictionary(String id, UpdateDictionaryCommand command) {
        DictionaryView view = super.updateDictionary(id, command);
        appendDictionary(view, null, ChangeKind.UPDATED);
        return view;
    }

    /** 字典 Kill Switch 与 Outbox 在同一事务提交。 */
    @Override
    @Transactional
    public DictionaryView changeDictionaryStatus(String id, StatusCommand command) {
        DictionaryView view = super.changeDictionaryStatus(id, command);
        appendDictionary(view, null, ChangeKind.STATUS_CHANGED);
        return view;
    }

    /** Item 创建与字典级 Cache 失效事件在同一事务提交。 */
    @Override
    @Transactional
    public ItemView createItem(String dictionaryId, CreateItemCommand command) {
        ItemView view = super.createItem(dictionaryId, command);
        appendItem(requireDictionary(dictionaryId), view, ChangeKind.CREATED);
        return view;
    }

    /** Item 更新与字典级 Cache 失效事件在同一事务提交。 */
    @Override
    @Transactional
    public ItemView updateItem(
            String dictionaryId,
            String itemId,
            UpdateItemCommand command) {
        ItemView view = super.updateItem(dictionaryId, itemId, command);
        appendItem(requireDictionary(dictionaryId), view, ChangeKind.UPDATED);
        return view;
    }

    /** Item 状态变化与字典级 Cache 失效事件在同一事务提交。 */
    @Override
    @Transactional
    public ItemView changeItemStatus(
            String dictionaryId,
            String itemId,
            StatusCommand command) {
        ItemView view = super.changeItemStatus(dictionaryId, itemId, command);
        appendItem(requireDictionary(dictionaryId), view, ChangeKind.STATUS_CHANGED);
        return view;
    }

    /** PostgreSQL 字典头确认后读取版本化 Active Item Projection。 */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public List<SystemDictionaryItemOption> activeItems(String dictionaryCode) {
        SystemDictionary dictionary = requireDictionaryByCode(dictionaryCode);
        if (!dictionary.enabled()) {
            return List.of();
        }
        var cached = cache.findDictionaryItems(
                        dictionary.dictionaryCode(), dictionary.version())
                .filter(values -> values.stream().allMatch(value ->
                        value.dictionaryCode().equals(dictionary.dictionaryCode())));
        if (cached.isPresent()) {
            return cached.orElseThrow();
        }
        List<SystemDictionaryItemOption> resolved = items.findActive(dictionary.id()).stream()
                .map(item -> new SystemDictionaryItemOption(
                        dictionary.dictionaryCode(),
                        item.itemCode(),
                        item.itemLabel(),
                        item.sortOrder(),
                        item.version(),
                        item.updatedAt()))
                .toList();
        cache.putDictionaryItems(
                dictionary.dictionaryCode(), dictionary.version(), resolved);
        return resolved;
    }

    /** PostgreSQL 字典头确认后读取版本化单项兼容 Projection。 */
    @Override
    @Transactional(propagation = Propagation.NEVER)
    public ResolvedSystemDictionaryItem resolveItem(
            String dictionaryCode,
            String itemCode) {
        SystemDictionary dictionary = requireDictionaryByCode(dictionaryCode);
        String normalizedItemCode = SystemDictionaryRules.normalizeItemCode(itemCode);
        var cached = cache.findDictionaryItem(
                        dictionary.dictionaryCode(),
                        dictionary.version(),
                        normalizedItemCode)
                .filter(value -> value.dictionaryCode().equals(dictionary.dictionaryCode())
                        && value.itemCode().equals(normalizedItemCode)
                        && value.dictionaryEnabled() == dictionary.enabled());
        if (cached.isPresent()) {
            return cached.orElseThrow();
        }
        SystemDictionaryItem item = items.findByCode(dictionary.id(), normalizedItemCode)
                .orElseThrow(() -> new SystemDictionaryException.NotFound("字典项不存在"));
        ResolvedSystemDictionaryItem resolved = new ResolvedSystemDictionaryItem(
                dictionary.dictionaryCode(),
                item.itemCode(),
                item.itemLabel(),
                dictionary.enabled(),
                item.enabled(),
                item.effectiveEnabled(dictionary.enabled()),
                item.version(),
                item.updatedAt());
        cache.putDictionaryItem(
                dictionary.dictionaryCode(),
                dictionary.version(),
                normalizedItemCode,
                resolved);
        return resolved;
    }

    private SystemDictionary requireDictionary(String id) {
        if (id == null || id.isBlank() || id.trim().length() > 19) {
            throw new IllegalArgumentException("id 必须是 1～19 位字符串");
        }
        return dictionaries.findById(id.trim())
                .orElseThrow(() -> new SystemDictionaryException.NotFound("字典不存在"));
    }

    private SystemDictionary requireDictionaryByCode(String code) {
        String normalized = SystemDictionaryRules.normalizeDictionaryCode(code);
        return dictionaries.findByCode(normalized)
                .orElseThrow(() -> new SystemDictionaryException.NotFound("字典不存在"));
    }

    private void appendDictionary(
            DictionaryView view,
            String itemCode,
            ChangeKind changeKind) {
        events.dictionaryChanged(new SystemRuntimeChangeEventPort.DictionaryChangedEvent(
                view.id(),
                view.dictionaryCode(),
                itemCode,
                view.version(),
                view.enabled(),
                changeKind));
    }

    private void appendItem(
            SystemDictionary dictionary,
            ItemView view,
            ChangeKind changeKind) {
        events.dictionaryChanged(new SystemRuntimeChangeEventPort.DictionaryChangedEvent(
                dictionary.id(),
                dictionary.dictionaryCode(),
                view.itemCode(),
                view.version(),
                view.enabled(),
                changeKind));
    }
}
