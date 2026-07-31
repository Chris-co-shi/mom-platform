package io.github.chrisshi.mom.system.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryException;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionary;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryRepository;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemDictionaryEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.mapper.SystemDictionaryMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus Repository 抽象的 System Dictionary 持久化 Adapter。
 *
 * <p>该类型在 Infrastructure 内部复用 {@link CrudRepository} 的单表 CRUD、计数和列表能力，对上仍只实现
 * 框架无关的 {@link SystemDictionaryRepository}。Entity、Mapper、Wrapper 和通用 CRUD 不得泄漏到
 * Application、Domain 或 Web。</p>
 *
 * <p>字典业务规则、授权和事务边界由 Application/Domain 负责；唯一冲突与 Version CAS 失败继续转换为
 * MOM 稳定语义。数据库不可用时异常向上传播，不进行缓存或内存降级。</p>
 */
@Repository
public class MybatisSystemDictionaryRepository
        extends CrudRepository<SystemDictionaryMapper, SystemDictionaryEntity>
        implements SystemDictionaryRepository {

    /** 按技术主键读取字典。 */
    @Override
    public Optional<SystemDictionary> findById(String id) {
        return Optional.ofNullable(getById(id)).map(MybatisSystemDictionaryRepository::toDomain);
    }

    /** 按稳定 dictionaryCode 精确读取字典。 */
    @Override
    public Optional<SystemDictionary> findByCode(String dictionaryCode) {
        var query = Wrappers.<SystemDictionaryEntity>lambdaQuery()
                .eq(SystemDictionaryEntity::getDictionaryCode, dictionaryCode);
        return Optional.ofNullable(getOne(query)).map(MybatisSystemDictionaryRepository::toDomain);
    }

    /** 插入字典；数据库唯一冲突转换为稳定业务冲突。 */
    @Override
    public SystemDictionary insert(SystemDictionary dictionary) {
        SystemDictionaryEntity entity = new SystemDictionaryEntity();
        entity.setDictionaryCode(dictionary.dictionaryCode());
        entity.setDictionaryName(dictionary.dictionaryName());
        entity.setEnabled(dictionary.enabled());
        entity.setDescription(dictionary.description());
        try {
            if (!save(entity)) {
                throw new IllegalStateException("字典未插入预期的一行");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new SystemDictionaryException.Conflict("dictionaryCode 已存在", exception);
        }
        return findById(entity.getId()).orElseThrow(() -> new IllegalStateException("字典插入成功后无法读取"));
    }

    /** 使用 Entity Version 执行 CAS 内容更新。 */
    @Override
    public boolean update(SystemDictionary dictionary) {
        SystemDictionaryEntity entity = new SystemDictionaryEntity();
        entity.setId(dictionary.id());
        entity.setVersion(dictionary.version());
        entity.setDictionaryName(dictionary.dictionaryName());
        entity.setDescription(dictionary.description());
        return updateById(entity);
    }

    /** 使用 Entity Version 执行 CAS 状态更新。 */
    @Override
    public boolean updateStatus(SystemDictionary dictionary) {
        SystemDictionaryEntity entity = new SystemDictionaryEntity();
        entity.setId(dictionary.id());
        entity.setVersion(dictionary.version());
        entity.setEnabled(dictionary.enabled());
        return updateById(entity);
    }

    /** 按受控单表条件分页，返回 Infrastructure 无关的 Domain Page。 */
    @Override
    public DictionaryPage findPage(DictionaryQuery query) {
        long offset = Math.multiplyExact((long) query.page(), query.size());
        long total = count(dictionaryFilter(query));
        List<SystemDictionary> items = total == 0 ? List.of() : list(
                        dictionaryFilter(query)
                                .orderByAsc(SystemDictionaryEntity::getDictionaryCode)
                                .orderByAsc(SystemDictionaryEntity::getId)
                                .last(limitOffset(query.size(), offset)))
                .stream()
                .map(MybatisSystemDictionaryRepository::toDomain)
                .toList();
        return new DictionaryPage(items, total, query.page(), query.size());
    }

    /** 构造只包含服务端白名单字段的查询条件。 */
    private static LambdaQueryWrapper<SystemDictionaryEntity> dictionaryFilter(DictionaryQuery query) {
        return Wrappers.<SystemDictionaryEntity>lambdaQuery()
                .eq(query.dictionaryCode() != null,
                        SystemDictionaryEntity::getDictionaryCode, query.dictionaryCode())
                .eq(query.enabled() != null, SystemDictionaryEntity::getEnabled, query.enabled());
    }

    /** 生成仅包含已校验非负数字的固定 PostgreSQL 分页尾句。 */
    private static String limitOffset(int size, long offset) {
        return "LIMIT " + size + " OFFSET " + offset;
    }

    /** 将数据库行模型转换为不可泄漏 Entity 的领域快照。 */
    private static SystemDictionary toDomain(SystemDictionaryEntity entity) {
        return new SystemDictionary(entity.getId(), entity.getDictionaryCode(), entity.getDictionaryName(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(), entity.getDescription(),
                entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedBy(), entity.getUpdatedAt());
    }
}
