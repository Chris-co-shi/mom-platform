package io.github.chrisshi.mom.system.infrastructure.persistence.dictionary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryException;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryItem;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryItemRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的 System Dictionary Item Repository Adapter。
 *
 * <p>父字典限定、稳定 Code 查询、Active List、计数、固定排序和分页全部使用 Lambda Wrapper；
 * 更新继续通过 BaseEntity Version CAS，不维护 Mapper XML。</p>
 */
@Repository
public class MybatisSystemDictionaryItemRepository implements SystemDictionaryItemRepository {
    private final SystemDictionaryItemMapper mapper;

    public MybatisSystemDictionaryItemRepository(SystemDictionaryItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<SystemDictionaryItem> findById(String dictionaryId, String itemId) {
        var query = Wrappers.<SystemDictionaryItemEntity>lambdaQuery()
                .eq(SystemDictionaryItemEntity::getDictionaryId, dictionaryId)
                .eq(SystemDictionaryItemEntity::getId, itemId);
        return Optional.ofNullable(mapper.selectOne(query))
                .map(MybatisSystemDictionaryItemRepository::toDomain);
    }

    @Override
    public Optional<SystemDictionaryItem> findByCode(String dictionaryId, String itemCode) {
        var query = Wrappers.<SystemDictionaryItemEntity>lambdaQuery()
                .eq(SystemDictionaryItemEntity::getDictionaryId, dictionaryId)
                .eq(SystemDictionaryItemEntity::getItemCode, itemCode);
        return Optional.ofNullable(mapper.selectOne(query))
                .map(MybatisSystemDictionaryItemRepository::toDomain);
    }

    @Override
    public SystemDictionaryItem insert(SystemDictionaryItem item) {
        SystemDictionaryItemEntity entity = new SystemDictionaryItemEntity();
        entity.setDictionaryId(item.dictionaryId());
        entity.setItemCode(item.itemCode());
        entity.setItemLabel(item.itemLabel());
        entity.setSortOrder(item.sortOrder());
        entity.setEnabled(item.enabled());
        entity.setDescription(item.description());
        try {
            mapper.insert(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new SystemDictionaryException.Conflict("同一字典内 itemCode 已存在", exception);
        }
        return findById(item.dictionaryId(), entity.getId())
                .orElseThrow(() -> new IllegalStateException("字典项插入成功后无法读取"));
    }

    @Override
    public boolean update(SystemDictionaryItem item) {
        SystemDictionaryItemEntity entity = new SystemDictionaryItemEntity();
        entity.setId(item.id());
        entity.setVersion(item.version());
        entity.setItemLabel(item.itemLabel());
        entity.setSortOrder(item.sortOrder());
        entity.setDescription(item.description());
        return mapper.updateById(entity) == 1;
    }

    @Override
    public boolean updateStatus(SystemDictionaryItem item) {
        SystemDictionaryItemEntity entity = new SystemDictionaryItemEntity();
        entity.setId(item.id());
        entity.setVersion(item.version());
        entity.setEnabled(item.enabled());
        return mapper.updateById(entity) == 1;
    }

    @Override
    public List<SystemDictionaryItem> findActive(String dictionaryId) {
        return mapper.selectList(
                        Wrappers.<SystemDictionaryItemEntity>lambdaQuery()
                                .eq(SystemDictionaryItemEntity::getDictionaryId, dictionaryId)
                                .eq(SystemDictionaryItemEntity::getEnabled, true)
                                .orderByAsc(SystemDictionaryItemEntity::getSortOrder)
                                .orderByAsc(SystemDictionaryItemEntity::getItemCode)
                                .orderByAsc(SystemDictionaryItemEntity::getId))
                .stream()
                .map(MybatisSystemDictionaryItemRepository::toDomain)
                .toList();
    }

    @Override
    public ItemPage findPage(ItemQuery query) {
        long offset = Math.multiplyExact((long) query.page(), query.size());
        long total = mapper.selectCount(itemFilter(query));
        List<SystemDictionaryItem> items = total == 0 ? List.of() : mapper.selectList(
                        itemFilter(query)
                                .orderByAsc(SystemDictionaryItemEntity::getSortOrder)
                                .orderByAsc(SystemDictionaryItemEntity::getItemCode)
                                .orderByAsc(SystemDictionaryItemEntity::getId)
                                .last(limitOffset(query.size(), offset)))
                .stream()
                .map(MybatisSystemDictionaryItemRepository::toDomain)
                .toList();
        return new ItemPage(items, total, query.page(), query.size());
    }

    private static LambdaQueryWrapper<SystemDictionaryItemEntity> itemFilter(ItemQuery query) {
        return Wrappers.<SystemDictionaryItemEntity>lambdaQuery()
                .eq(SystemDictionaryItemEntity::getDictionaryId, query.dictionaryId())
                .eq(query.itemCode() != null, SystemDictionaryItemEntity::getItemCode, query.itemCode())
                .eq(query.enabled() != null, SystemDictionaryItemEntity::getEnabled, query.enabled());
    }

    private static String limitOffset(int size, long offset) {
        return "LIMIT " + size + " OFFSET " + offset;
    }

    private static SystemDictionaryItem toDomain(SystemDictionaryItemEntity entity) {
        return new SystemDictionaryItem(entity.getId(), entity.getDictionaryId(), entity.getItemCode(),
                entity.getItemLabel(), entity.getSortOrder(), Boolean.TRUE.equals(entity.getEnabled()),
                entity.getVersion(), entity.getDescription(), entity.getCreatedBy(), entity.getCreatedAt(),
                entity.getUpdatedBy(), entity.getUpdatedAt());
    }
}
