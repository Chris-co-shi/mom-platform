package io.github.chrisshi.mom.system.infrastructure.persistence.dictionary;

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
 * <p>Adapter 只访问同 Schema Item 表并将唯一/FK完整性错误脱敏。更新通过 MyBatis-Plus Version CAS，
 * affected rows 由 Application 转换为冲突；不级联状态、不缓存、不发布消息。</p>
 */
@Repository
public class MybatisSystemDictionaryItemRepository implements SystemDictionaryItemRepository {
    private final SystemDictionaryItemMapper mapper;

    public MybatisSystemDictionaryItemRepository(SystemDictionaryItemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<SystemDictionaryItem> findById(String dictionaryId, String itemId) {
        return Optional.ofNullable(mapper.selectByDictionaryAndId(dictionaryId, itemId))
                .map(MybatisSystemDictionaryItemRepository::toDomain);
    }

    @Override
    public Optional<SystemDictionaryItem> findByCode(String dictionaryId, String itemCode) {
        return Optional.ofNullable(mapper.selectByDictionaryAndCode(dictionaryId, itemCode))
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
        return mapper.selectActive(dictionaryId).stream()
                .map(MybatisSystemDictionaryItemRepository::toDomain).toList();
    }

    @Override
    public ItemPage findPage(ItemQuery query) {
        long offset = Math.multiplyExact((long) query.page(), query.size());
        List<SystemDictionaryItem> items = mapper.selectPage(query.dictionaryId(), query.itemCode(),
                        query.enabled(), query.size(), offset).stream()
                .map(MybatisSystemDictionaryItemRepository::toDomain).toList();
        long total = mapper.countPage(query.dictionaryId(), query.itemCode(), query.enabled());
        return new ItemPage(items, total, query.page(), query.size());
    }

    private static SystemDictionaryItem toDomain(SystemDictionaryItemEntity entity) {
        return new SystemDictionaryItem(entity.getId(), entity.getDictionaryId(), entity.getItemCode(),
                entity.getItemLabel(), entity.getSortOrder(), Boolean.TRUE.equals(entity.getEnabled()),
                entity.getVersion(), entity.getDescription(), entity.getCreatedBy(), entity.getCreatedAt(),
                entity.getUpdatedBy(), entity.getUpdatedAt());
    }
}
