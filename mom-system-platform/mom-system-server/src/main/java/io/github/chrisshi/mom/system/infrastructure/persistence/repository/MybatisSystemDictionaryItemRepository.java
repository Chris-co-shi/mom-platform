package io.github.chrisshi.mom.system.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.repository.CrudRepository;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryException;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryItem;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryItemRepository;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemDictionaryItemEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.mapper.SystemDictionaryItemMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus Repository 抽象的 System Dictionary Item 持久化 Adapter。
 *
 * <p>该类型在 Infrastructure 内部复用 {@link CrudRepository} 的单表 CRUD、计数和列表能力，对上仍只实现
 * 框架无关的 {@link SystemDictionaryItemRepository}。父字典限定、稳定 Code、Active List、固定排序和
 * Version CAS 都保持原行为，Entity、Mapper 和 Wrapper 不得泄漏到上层。</p>
 *
 * <p>父字典存在性、状态兼容、授权与事务边界由 Application/Domain 负责。数据库不可用时异常向上传播，
 * 不进行缓存或内存降级。</p>
 */
@Repository
public class MybatisSystemDictionaryItemRepository
        extends CrudRepository<SystemDictionaryItemMapper, SystemDictionaryItemEntity>
        implements SystemDictionaryItemRepository {

    /** 在父字典范围内按技术主键读取字典项。 */
    @Override
    public Optional<SystemDictionaryItem> findById(String dictionaryId, String itemId) {
        var query = Wrappers.<SystemDictionaryItemEntity>lambdaQuery()
                .eq(SystemDictionaryItemEntity::getDictionaryId, dictionaryId)
                .eq(SystemDictionaryItemEntity::getId, itemId);
        return Optional.ofNullable(getOne(query))
                .map(MybatisSystemDictionaryItemRepository::toDomain);
    }

    /** 在父字典范围内按稳定 itemCode 精确读取。 */
    @Override
    public Optional<SystemDictionaryItem> findByCode(String dictionaryId, String itemCode) {
        var query = Wrappers.<SystemDictionaryItemEntity>lambdaQuery()
                .eq(SystemDictionaryItemEntity::getDictionaryId, dictionaryId)
                .eq(SystemDictionaryItemEntity::getItemCode, itemCode);
        return Optional.ofNullable(getOne(query))
                .map(MybatisSystemDictionaryItemRepository::toDomain);
    }

    /** 插入字典项；数据库唯一冲突转换为稳定业务冲突。 */
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
            if (!save(entity)) {
                throw new IllegalStateException("字典项未插入预期的一行");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new SystemDictionaryException.Conflict("同一字典内 itemCode 已存在", exception);
        }
        return findById(item.dictionaryId(), entity.getId())
                .orElseThrow(() -> new IllegalStateException("字典项插入成功后无法读取"));
    }

    /** 使用 Entity Version 执行 CAS 内容更新。 */
    @Override
    public boolean update(SystemDictionaryItem item) {
        SystemDictionaryItemEntity entity = new SystemDictionaryItemEntity();
        entity.setId(item.id());
        entity.setVersion(item.version());
        entity.setItemLabel(item.itemLabel());
        entity.setSortOrder(item.sortOrder());
        entity.setDescription(item.description());
        return updateById(entity);
    }

    /** 使用 Entity Version 执行 CAS 状态更新。 */
    @Override
    public boolean updateStatus(SystemDictionaryItem item) {
        SystemDictionaryItemEntity entity = new SystemDictionaryItemEntity();
        entity.setId(item.id());
        entity.setVersion(item.version());
        entity.setEnabled(item.enabled());
        return updateById(entity);
    }

    /** 读取启用字典项并保持 sortOrder、itemCode、id 的稳定排序。 */
    @Override
    public List<SystemDictionaryItem> findActive(String dictionaryId) {
        return list(Wrappers.<SystemDictionaryItemEntity>lambdaQuery()
                        .eq(SystemDictionaryItemEntity::getDictionaryId, dictionaryId)
                        .eq(SystemDictionaryItemEntity::getEnabled, true)
                        .orderByAsc(SystemDictionaryItemEntity::getSortOrder)
                        .orderByAsc(SystemDictionaryItemEntity::getItemCode)
                        .orderByAsc(SystemDictionaryItemEntity::getId))
                .stream()
                .map(MybatisSystemDictionaryItemRepository::toDomain)
                .toList();
    }

    /** 按受控单表条件分页，返回 Infrastructure 无关的 Domain Page。 */
    @Override
    public ItemPage findPage(ItemQuery query) {
        long offset = Math.multiplyExact((long) query.page(), query.size());
        long total = count(itemFilter(query));
        List<SystemDictionaryItem> items = total == 0 ? List.of() : list(
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

    /** 构造父字典限定且只包含服务端白名单字段的查询条件。 */
    private static LambdaQueryWrapper<SystemDictionaryItemEntity> itemFilter(ItemQuery query) {
        return Wrappers.<SystemDictionaryItemEntity>lambdaQuery()
                .eq(SystemDictionaryItemEntity::getDictionaryId, query.dictionaryId())
                .eq(query.itemCode() != null, SystemDictionaryItemEntity::getItemCode, query.itemCode())
                .eq(query.enabled() != null, SystemDictionaryItemEntity::getEnabled, query.enabled());
    }

    /** 生成仅包含已校验非负数字的固定 PostgreSQL 分页尾句。 */
    private static String limitOffset(int size, long offset) {
        return "LIMIT " + size + " OFFSET " + offset;
    }

    /** 将数据库行模型转换为不可泄漏 Entity 的领域快照。 */
    private static SystemDictionaryItem toDomain(SystemDictionaryItemEntity entity) {
        return new SystemDictionaryItem(entity.getId(), entity.getDictionaryId(), entity.getItemCode(),
                entity.getItemLabel(), entity.getSortOrder(), Boolean.TRUE.equals(entity.getEnabled()),
                entity.getVersion(), entity.getDescription(), entity.getCreatedBy(), entity.getCreatedAt(),
                entity.getUpdatedBy(), entity.getUpdatedAt());
    }
}
