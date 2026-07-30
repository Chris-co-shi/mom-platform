package io.github.chrisshi.mom.system.application.dictionary;

import io.github.chrisshi.mom.system.api.ResolvedSystemDictionaryItem;
import io.github.chrisshi.mom.system.api.SystemDictionaryItemOption;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionary;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryItem;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryItemRepository;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryRepository;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.CreateDictionaryCommand;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.CreateItemCommand;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.DictionaryPageQuery;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.DictionaryPageView;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.DictionaryView;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.ItemPageQuery;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.ItemPageView;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.ItemView;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.StatusCommand;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.UpdateDictionaryCommand;
import static io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryApplicationModels.UpdateItemCommand;

/**
 * 非权威通用字典的事务用例服务。
 *
 * <p>字典与条目分别通过领域 Port 按需读取，不构造完整 Item 聚合。所有写入使用 System 单 PostgreSQL
 * 本地事务、数据库唯一约束和 Version CAS；服务不依赖 Mapper、Entity、Redis、MQ 或 Seata。禁用字典
 * 不修改条目状态，数据库不可用时不缓存、不降级、不伪造兼容结果。</p>
 */
@Service
public class SystemDictionaryApplicationService {
    private static final int MAX_PAGE_SIZE = 100;
    private final SystemDictionaryRepository dictionaryRepository;
    private final SystemDictionaryItemRepository itemRepository;

    public SystemDictionaryApplicationService(
            SystemDictionaryRepository dictionaryRepository,
            SystemDictionaryItemRepository itemRepository) {
        this.dictionaryRepository = Objects.requireNonNull(dictionaryRepository, "dictionaryRepository");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository");
    }

    /** 创建全局唯一、不可 Rename 的字典 Code。 */
    @Transactional
    public DictionaryView createDictionary(CreateDictionaryCommand command) {
        Objects.requireNonNull(command, "command");
        SystemDictionary dictionary = SystemDictionary.newDictionary(
                SystemDictionaryRules.normalizeDictionaryCode(command.dictionaryCode()),
                SystemDictionaryRules.normalizeDictionaryName(command.dictionaryName()),
                command.enabled() == null || command.enabled(),
                SystemDictionaryRules.normalizeDescription(command.description()));
        return DictionaryView.from(dictionaryRepository.insert(dictionary));
    }

    /** 使用客户端 Version 更新字典名称与说明，dictionaryCode 保持不可变。 */
    @Transactional
    public DictionaryView updateDictionary(String id, UpdateDictionaryCommand command) {
        Objects.requireNonNull(command, "command");
        requireVersion(command.version());
        SystemDictionary current = requireDictionary(id);
        SystemDictionary changed = current.update(command.version(),
                SystemDictionaryRules.normalizeDictionaryName(command.dictionaryName()),
                SystemDictionaryRules.normalizeDescription(command.description()));
        if (!dictionaryRepository.update(changed)) {
            throw new SystemDictionaryException.StaleVersion("字典已被其他请求修改");
        }
        return DictionaryView.from(requireDictionary(current.id()));
    }

    /** 使用客户端 Version 启停字典；不级联修改 Item enabled。 */
    @Transactional
    public DictionaryView changeDictionaryStatus(String id, StatusCommand command) {
        Objects.requireNonNull(command, "command");
        requireVersion(command.version());
        requireEnabled(command.enabled());
        SystemDictionary current = requireDictionary(id);
        if (!dictionaryRepository.updateStatus(current.changeStatus(command.version(), command.enabled()))) {
            throw new SystemDictionaryException.StaleVersion("字典已被其他请求修改");
        }
        return DictionaryView.from(requireDictionary(current.id()));
    }

    /** 按内部 String ID 查询字典管理视图，无副作用。 */
    @Transactional(readOnly = true)
    public DictionaryView getDictionary(String id) {
        return DictionaryView.from(requireDictionary(id));
    }

    /** 按 Code/状态精确过滤并使用固定排序分页。 */
    @Transactional(readOnly = true)
    public DictionaryPageView pageDictionaries(DictionaryPageQuery query) {
        Objects.requireNonNull(query, "query");
        requirePage(query.page(), query.size());
        String code = normalizeOptionalDictionaryCode(query.dictionaryCode());
        var result = dictionaryRepository.findPage(new SystemDictionaryRepository.DictionaryQuery(
                code, query.enabled(), query.page(), query.size()));
        return new DictionaryPageView(result.items().stream().map(DictionaryView::from).toList(),
                result.total(), result.page(), result.size());
    }

    /** 在已存在字典下创建不可 Rename 的 Item Code。 */
    @Transactional
    public ItemView createItem(String dictionaryId, CreateItemCommand command) {
        Objects.requireNonNull(command, "command");
        SystemDictionary dictionary = requireDictionary(dictionaryId);
        SystemDictionaryItem item = SystemDictionaryItem.newItem(
                dictionary.id(),
                SystemDictionaryRules.normalizeItemCode(command.itemCode()),
                SystemDictionaryRules.normalizeItemLabel(command.itemLabel()),
                SystemDictionaryRules.requireSortOrder(command.sortOrder()),
                command.enabled() == null || command.enabled(),
                SystemDictionaryRules.normalizeDescription(command.description()));
        return ItemView.from(itemRepository.insert(item));
    }

    /** 使用客户端 Version 更新 Label、排序和说明，dictionaryId/itemCode 保持不可变。 */
    @Transactional
    public ItemView updateItem(String dictionaryId, String itemId, UpdateItemCommand command) {
        Objects.requireNonNull(command, "command");
        requireVersion(command.version());
        SystemDictionary dictionary = requireDictionary(dictionaryId);
        SystemDictionaryItem current = requireItem(dictionary.id(), itemId);
        SystemDictionaryItem changed = current.update(command.version(),
                SystemDictionaryRules.normalizeItemLabel(command.itemLabel()),
                SystemDictionaryRules.requireSortOrder(command.sortOrder()),
                SystemDictionaryRules.normalizeDescription(command.description()));
        if (!itemRepository.update(changed)) {
            throw new SystemDictionaryException.StaleVersion("字典项已被其他请求修改");
        }
        return ItemView.from(requireItem(dictionary.id(), current.id()));
    }

    /** 使用客户端 Version 启停 Item，不修改字典状态。 */
    @Transactional
    public ItemView changeItemStatus(String dictionaryId, String itemId, StatusCommand command) {
        Objects.requireNonNull(command, "command");
        requireVersion(command.version());
        requireEnabled(command.enabled());
        SystemDictionary dictionary = requireDictionary(dictionaryId);
        SystemDictionaryItem current = requireItem(dictionary.id(), itemId);
        if (!itemRepository.updateStatus(current.changeStatus(command.version(), command.enabled()))) {
            throw new SystemDictionaryException.StaleVersion("字典项已被其他请求修改");
        }
        return ItemView.from(requireItem(dictionary.id(), current.id()));
    }

    /** 按字典和条目内部 ID 查询管理视图，路径关系不匹配时返回 404。 */
    @Transactional(readOnly = true)
    public ItemView getItem(String dictionaryId, String itemId) {
        SystemDictionary dictionary = requireDictionary(dictionaryId);
        return ItemView.from(requireItem(dictionary.id(), itemId));
    }

    /** 查询指定字典的 Item 管理分页，固定按 sortOrder、itemCode、id 排序。 */
    @Transactional(readOnly = true)
    public ItemPageView pageItems(String dictionaryId, ItemPageQuery query) {
        Objects.requireNonNull(query, "query");
        requirePage(query.page(), query.size());
        SystemDictionary dictionary = requireDictionary(dictionaryId);
        String itemCode = normalizeOptionalItemCode(query.itemCode());
        var result = itemRepository.findPage(new SystemDictionaryItemRepository.ItemQuery(
                dictionary.id(), itemCode, query.enabled(), query.page(), query.size()));
        return new ItemPageView(result.items().stream().map(ItemView::from).toList(),
                result.total(), result.page(), result.size());
    }

    /**
     * 返回当前可供新业务选择的有效条目。
     *
     * @param dictionaryCode 全局稳定字典 Code
     * @return 仅字典与条目均启用的固定排序只读契约；字典禁用时为空列表
     * @throws SystemDictionaryException.NotFound 字典不存在
     */
    @Transactional(readOnly = true)
    public List<SystemDictionaryItemOption> activeItems(String dictionaryCode) {
        SystemDictionary dictionary = requireDictionaryByCode(dictionaryCode);
        if (!dictionary.enabled()) {
            return List.of();
        }
        return itemRepository.findActive(dictionary.id()).stream()
                .map(item -> new SystemDictionaryItemOption(dictionary.dictionaryCode(), item.itemCode(),
                        item.itemLabel(), item.sortOrder(), item.version(), item.updatedAt()))
                .toList();
    }

    /**
     * 按稳定双 Code 兼容读取单项，包括已禁用记录。
     *
     * @param dictionaryCode 全局字典 Code
     * @param itemCode 字典内 Item Code
     * @return 两级 enabled 与 effectiveEnabled 的只读契约
     * @throws SystemDictionaryException.NotFound 任一记录不存在
     */
    @Transactional(readOnly = true)
    public ResolvedSystemDictionaryItem resolveItem(String dictionaryCode, String itemCode) {
        SystemDictionary dictionary = requireDictionaryByCode(dictionaryCode);
        String normalizedItemCode = SystemDictionaryRules.normalizeItemCode(itemCode);
        SystemDictionaryItem item = itemRepository.findByCode(dictionary.id(), normalizedItemCode)
                .orElseThrow(() -> new SystemDictionaryException.NotFound("字典项不存在"));
        return new ResolvedSystemDictionaryItem(dictionary.dictionaryCode(), item.itemCode(), item.itemLabel(),
                dictionary.enabled(), item.enabled(), item.effectiveEnabled(dictionary.enabled()),
                item.version(), item.updatedAt());
    }

    private SystemDictionary requireDictionary(String id) {
        return dictionaryRepository.findById(requireId(id))
                .orElseThrow(() -> new SystemDictionaryException.NotFound("字典不存在"));
    }

    private SystemDictionary requireDictionaryByCode(String code) {
        String normalized = SystemDictionaryRules.normalizeDictionaryCode(code);
        return dictionaryRepository.findByCode(normalized)
                .orElseThrow(() -> new SystemDictionaryException.NotFound("字典不存在"));
    }

    private SystemDictionaryItem requireItem(String dictionaryId, String itemId) {
        return itemRepository.findById(dictionaryId, requireId(itemId))
                .orElseThrow(() -> new SystemDictionaryException.NotFound("字典项不存在"));
    }

    private static String normalizeOptionalDictionaryCode(String code) {
        return code == null || code.isBlank() ? null : SystemDictionaryRules.normalizeDictionaryCode(code);
    }

    private static String normalizeOptionalItemCode(String code) {
        return code == null || code.isBlank() ? null : SystemDictionaryRules.normalizeItemCode(code);
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank() || id.trim().length() > 19) {
            throw new IllegalArgumentException("id 必须是 1～19 位字符串");
        }
        return id.trim();
    }

    private static void requireVersion(Long version) {
        if (version == null || version < 0) {
            throw new IllegalArgumentException("version 不能小于 0");
        }
    }

    private static void requireEnabled(Boolean enabled) {
        if (enabled == null) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
    }

    private static void requirePage(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page 不能小于 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size 必须在 1～" + MAX_PAGE_SIZE + " 之间");
        }
    }
}
