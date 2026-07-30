package io.github.chrisshi.mom.system.infrastructure.persistence.dictionary;

import io.github.chrisshi.mom.system.application.dictionary.SystemDictionaryException;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionary;
import io.github.chrisshi.mom.system.domain.dictionary.SystemDictionaryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的 System Dictionary Repository Adapter。
 *
 * <p>Adapter 转换领域对象与行模型，隐藏 Mapper、分页 SQL 和 affected rows。唯一约束冲突被脱敏为稳定
 * Conflict；其他数据库故障直接传播。所有操作共享 System 唯一 DataSource，不创建缓存或跨 Schema 查询。</p>
 */
@Repository
public class MybatisSystemDictionaryRepository implements SystemDictionaryRepository {
    private final SystemDictionaryMapper mapper;

    public MybatisSystemDictionaryRepository(SystemDictionaryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<SystemDictionary> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisSystemDictionaryRepository::toDomain);
    }

    @Override
    public Optional<SystemDictionary> findByCode(String dictionaryCode) {
        return Optional.ofNullable(mapper.selectByCode(dictionaryCode))
                .map(MybatisSystemDictionaryRepository::toDomain);
    }

    @Override
    public SystemDictionary insert(SystemDictionary dictionary) {
        SystemDictionaryEntity entity = new SystemDictionaryEntity();
        entity.setDictionaryCode(dictionary.dictionaryCode());
        entity.setDictionaryName(dictionary.dictionaryName());
        entity.setEnabled(dictionary.enabled());
        entity.setDescription(dictionary.description());
        try {
            mapper.insert(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new SystemDictionaryException.Conflict("dictionaryCode 已存在", exception);
        }
        return findById(entity.getId()).orElseThrow(() -> new IllegalStateException("字典插入成功后无法读取"));
    }

    @Override
    public boolean update(SystemDictionary dictionary) {
        SystemDictionaryEntity entity = new SystemDictionaryEntity();
        entity.setId(dictionary.id());
        entity.setVersion(dictionary.version());
        entity.setDictionaryName(dictionary.dictionaryName());
        entity.setDescription(dictionary.description());
        return mapper.updateById(entity) == 1;
    }

    @Override
    public boolean updateStatus(SystemDictionary dictionary) {
        SystemDictionaryEntity entity = new SystemDictionaryEntity();
        entity.setId(dictionary.id());
        entity.setVersion(dictionary.version());
        entity.setEnabled(dictionary.enabled());
        return mapper.updateById(entity) == 1;
    }

    @Override
    public DictionaryPage findPage(DictionaryQuery query) {
        long offset = Math.multiplyExact((long) query.page(), query.size());
        List<SystemDictionary> items = mapper.selectPage(query.dictionaryCode(), query.enabled(),
                        query.size(), offset).stream()
                .map(MybatisSystemDictionaryRepository::toDomain).toList();
        long total = mapper.countPage(query.dictionaryCode(), query.enabled());
        return new DictionaryPage(items, total, query.page(), query.size());
    }

    private static SystemDictionary toDomain(SystemDictionaryEntity entity) {
        return new SystemDictionary(entity.getId(), entity.getDictionaryCode(), entity.getDictionaryName(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(), entity.getDescription(),
                entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedBy(), entity.getUpdatedAt());
    }
}
