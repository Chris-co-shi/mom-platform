package io.github.chrisshi.mom.system.application.dictionary;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.system.api.ResolvedSystemDictionaryItem;
import io.github.chrisshi.mom.system.api.SystemDictionaryItemOption;
import io.github.chrisshi.mom.system.application.SystemV1Exception;
import io.github.chrisshi.mom.system.application.SystemV1Rules;
import io.github.chrisshi.mom.system.infrastructure.entity.DictionaryItemEntity;
import io.github.chrisshi.mom.system.infrastructure.entity.DictionaryTypeEntity;
import io.github.chrisshi.mom.system.infrastructure.mapper.DictionaryItemMapper;
import io.github.chrisshi.mom.system.infrastructure.mapper.DictionaryTypeMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.ChangeStatus;
import static io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.CreateItem;
import static io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.CreateType;
import static io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.ItemView;
import static io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.TypeView;
import static io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.UpdateItem;
import static io.github.chrisshi.mom.system.application.dictionary.DictionaryModels.UpdateType;

/**
 * System V1 Dictionary 的 Level 1 用例与事务边界。
 *
 * <p>调用链固定为 Controller → Application → Mapper。该类直接编排两个本地单表 Mapper，不再保留贫血
 * Domain、Repository Port 或一对一 Adapter。PostgreSQL 是唯一事实源；写失败或审计 Actor 缺失时事务
 * 回滚，不使用 Redis、MQ、Outbox 或跨服务调用。并发由业务唯一约束和 Version CAS 兜底。</p>
 */
@Service
public class DictionaryApplication {
    private final DictionaryTypeMapper typeMapper;
    private final DictionaryItemMapper itemMapper;

    /**
     * @param typeMapper 字典类型单表 Mapper
     * @param itemMapper 字典条目单表 Mapper
     */
    public DictionaryApplication(DictionaryTypeMapper typeMapper, DictionaryItemMapper itemMapper) {
        this.typeMapper = typeMapper;
        this.itemMapper = itemMapper;
    }

    /** 创建全局唯一且后续不可改名的字典类型。 */
    @Transactional
    public TypeView createType(CreateType command) {
        DictionaryTypeEntity entity = new DictionaryTypeEntity();
        entity.setCode(SystemV1Rules.dictionaryCode(command.code()));
        entity.setName(SystemV1Rules.displayText(command.name(), "name", 200));
        entity.setEnabled(command.enabled() == null || command.enabled());
        entity.setDescription(SystemV1Rules.description(command.description()));
        try {
            typeMapper.insert(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new SystemV1Exception.Conflict("dictionary_code_conflict", "system.dictionary.error.code_conflict");
        }
        return toTypeView(entity);
    }

    /** 使用 Version 更新名称与说明，不允许修改稳定 Code。 */
    @Transactional
    public TypeView updateType(String id, UpdateType command) {
        DictionaryTypeEntity entity = requireType(id);
        entity.setVersion(SystemV1Rules.version(command.version()));
        entity.setName(SystemV1Rules.displayText(command.name(), "name", 200));
        entity.setDescription(SystemV1Rules.description(command.description()));
        updateTypeOrConflict(entity);
        return toTypeView(requireType(id));
    }

    /** 使用 Version 启停字典类型；不会级联修改条目状态。 */
    @Transactional
    public TypeView changeTypeStatus(String id, ChangeStatus command) {
        requireEnabled(command.enabled());
        DictionaryTypeEntity entity = requireType(id);
        entity.setVersion(SystemV1Rules.version(command.version()));
        entity.setEnabled(command.enabled());
        updateTypeOrConflict(entity);
        return toTypeView(requireType(id));
    }

    /** 返回全部字典类型的固定 Code/ID 排序管理列表。 */
    @Transactional(readOnly = true)
    public List<TypeView> listTypes() {
        return typeMapper.selectList(Wrappers.<DictionaryTypeEntity>lambdaQuery()
                        .orderByAsc(DictionaryTypeEntity::getCode)
                        .orderByAsc(DictionaryTypeEntity::getId))
                .stream().map(DictionaryApplication::toTypeView).toList();
    }

    /** 在已存在字典类型下创建唯一稳定 Key。 */
    @Transactional
    public ItemView createItem(String typeId, CreateItem command) {
        DictionaryTypeEntity type = requireType(typeId);
        DictionaryItemEntity entity = new DictionaryItemEntity();
        entity.setTypeId(type.getId());
        entity.setKey(SystemV1Rules.itemKey(command.key()));
        entity.setValue(SystemV1Rules.displayText(command.value(), "value", 200));
        entity.setSortOrder(SystemV1Rules.sortOrder(command.sortOrder()));
        entity.setEnabled(command.enabled() == null || command.enabled());
        entity.setDescription(SystemV1Rules.description(command.description()));
        try {
            itemMapper.insert(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new SystemV1Exception.Conflict(
                    "dictionary_item_key_conflict", "system.dictionary.error.item_key_conflict");
        }
        return toItemView(entity);
    }

    /** 使用 Version 更新展示值、排序与说明，不允许修改稳定 Key 或父类型。 */
    @Transactional
    public ItemView updateItem(String typeId, String itemId, UpdateItem command) {
        requireType(typeId);
        DictionaryItemEntity entity = requireItem(typeId, itemId);
        entity.setVersion(SystemV1Rules.version(command.version()));
        entity.setValue(SystemV1Rules.displayText(command.value(), "value", 200));
        entity.setSortOrder(SystemV1Rules.sortOrder(command.sortOrder()));
        entity.setDescription(SystemV1Rules.description(command.description()));
        updateItemOrConflict(entity);
        return toItemView(requireItem(typeId, itemId));
    }

    /** 使用 Version 启停条目；禁用条目仍可用于历史值解析。 */
    @Transactional
    public ItemView changeItemStatus(String typeId, String itemId, ChangeStatus command) {
        requireEnabled(command.enabled());
        requireType(typeId);
        DictionaryItemEntity entity = requireItem(typeId, itemId);
        entity.setVersion(SystemV1Rules.version(command.version()));
        entity.setEnabled(command.enabled());
        updateItemOrConflict(entity);
        return toItemView(requireItem(typeId, itemId));
    }

    /** 返回单个字典下全部条目的固定排序管理列表。 */
    @Transactional(readOnly = true)
    public List<ItemView> listItems(String typeId) {
        DictionaryTypeEntity type = requireType(typeId);
        return itemMapper.selectList(Wrappers.<DictionaryItemEntity>lambdaQuery()
                        .eq(DictionaryItemEntity::getTypeId, type.getId())
                        .orderByAsc(DictionaryItemEntity::getSortOrder)
                        .orderByAsc(DictionaryItemEntity::getKey)
                        .orderByAsc(DictionaryItemEntity::getId))
                .stream().map(DictionaryApplication::toItemView).toList();
    }

    /** 返回只可用于新选择的启用条目。 */
    @Transactional(readOnly = true)
    public List<SystemDictionaryItemOption> activeItems(String dictionaryCode) {
        DictionaryTypeEntity type = requireTypeByCode(dictionaryCode);
        if (!Boolean.TRUE.equals(type.getEnabled())) {
            return List.of();
        }
        return itemMapper.selectList(Wrappers.<DictionaryItemEntity>lambdaQuery()
                        .eq(DictionaryItemEntity::getTypeId, type.getId())
                        .eq(DictionaryItemEntity::getEnabled, true)
                        .orderByAsc(DictionaryItemEntity::getSortOrder)
                        .orderByAsc(DictionaryItemEntity::getKey)
                        .orderByAsc(DictionaryItemEntity::getId))
                .stream().map(item -> new SystemDictionaryItemOption(
                        type.getCode(), item.getKey(), item.getValue(), item.getSortOrder())).toList();
    }

    /** 返回历史值可解析结果，不因当前禁用而隐藏条目。 */
    @Transactional(readOnly = true)
    public ResolvedSystemDictionaryItem resolveItem(String dictionaryCode, String key) {
        DictionaryTypeEntity type = requireTypeByCode(dictionaryCode);
        DictionaryItemEntity item = itemMapper.selectOne(Wrappers.<DictionaryItemEntity>lambdaQuery()
                .eq(DictionaryItemEntity::getTypeId, type.getId())
                .eq(DictionaryItemEntity::getKey, SystemV1Rules.itemKey(key)));
        if (item == null) {
            throw new SystemV1Exception.NotFound("system.dictionary.error.item_not_found");
        }
        return new ResolvedSystemDictionaryItem(type.getCode(), item.getKey(), item.getValue(),
                Boolean.TRUE.equals(type.getEnabled()) && Boolean.TRUE.equals(item.getEnabled()));
    }

    private DictionaryTypeEntity requireType(String id) {
        DictionaryTypeEntity entity = typeMapper.selectById(SystemV1Rules.id(id));
        if (entity == null) {
            throw new SystemV1Exception.NotFound("system.dictionary.error.type_not_found");
        }
        return entity;
    }

    private DictionaryTypeEntity requireTypeByCode(String code) {
        DictionaryTypeEntity entity = typeMapper.selectOne(Wrappers.<DictionaryTypeEntity>lambdaQuery()
                .eq(DictionaryTypeEntity::getCode, SystemV1Rules.dictionaryCode(code)));
        if (entity == null) {
            throw new SystemV1Exception.NotFound("system.dictionary.error.type_not_found");
        }
        return entity;
    }

    private DictionaryItemEntity requireItem(String typeId, String itemId) {
        DictionaryItemEntity entity = itemMapper.selectOne(Wrappers.<DictionaryItemEntity>lambdaQuery()
                .eq(DictionaryItemEntity::getId, SystemV1Rules.id(itemId))
                .eq(DictionaryItemEntity::getTypeId, SystemV1Rules.id(typeId)));
        if (entity == null) {
            throw new SystemV1Exception.NotFound("system.dictionary.error.item_not_found");
        }
        return entity;
    }

    private void updateTypeOrConflict(DictionaryTypeEntity entity) {
        if (typeMapper.updateById(entity) != 1) {
            throw new SystemV1Exception.Conflict("stale_version", "system.dictionary.error.stale_version");
        }
    }

    private void updateItemOrConflict(DictionaryItemEntity entity) {
        if (itemMapper.updateById(entity) != 1) {
            throw new SystemV1Exception.Conflict("stale_version", "system.dictionary.error.stale_version");
        }
    }

    private static void requireEnabled(Boolean enabled) {
        if (enabled == null) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
    }

    private static TypeView toTypeView(DictionaryTypeEntity entity) {
        return new TypeView(entity.getId(), entity.getCode(), entity.getName(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getDescription(), entity.getVersion(),
                entity.getUpdatedAt());
    }

    private static ItemView toItemView(DictionaryItemEntity entity) {
        return new ItemView(entity.getId(), entity.getTypeId(), entity.getKey(), entity.getValue(),
                entity.getSortOrder(), Boolean.TRUE.equals(entity.getEnabled()), entity.getDescription(),
                entity.getVersion(), entity.getUpdatedAt());
    }
}
