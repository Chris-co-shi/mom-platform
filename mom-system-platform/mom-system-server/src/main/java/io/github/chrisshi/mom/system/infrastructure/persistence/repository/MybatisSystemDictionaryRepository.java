package io.github.chrisshi.mom.system.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
 * 基于 MyBatis-Plus 的 System Dictionary Repository Adapter。
 *
 * <p>普通 CRUD、稳定 Code 查询、计数、固定排序和分页全部由 MomBaseMapper 与 Lambda Wrapper 表达；
 * 不维护 Mapper XML，也不暴露 Mapper、Entity 或 Wrapper 到 Domain/Web。</p>
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
        var query = Wrappers.<SystemDictionaryEntity>lambdaQuery()
                .eq(SystemDictionaryEntity::getDictionaryCode, dictionaryCode);
        return Optional.ofNullable(mapper.selectOne(query)).map(MybatisSystemDictionaryRepository::toDomain);
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
        long total = mapper.selectCount(dictionaryFilter(query));
        List<SystemDictionary> items = total == 0 ? List.of() : mapper.selectList(
                        dictionaryFilter(query)
                                .orderByAsc(SystemDictionaryEntity::getDictionaryCode)
                                .orderByAsc(SystemDictionaryEntity::getId)
                                .last(limitOffset(query.size(), offset)))
                .stream()
                .map(MybatisSystemDictionaryRepository::toDomain)
                .toList();
        return new DictionaryPage(items, total, query.page(), query.size());
    }

    private static LambdaQueryWrapper<SystemDictionaryEntity> dictionaryFilter(DictionaryQuery query) {
        return Wrappers.<SystemDictionaryEntity>lambdaQuery()
                .eq(query.dictionaryCode() != null,
                        SystemDictionaryEntity::getDictionaryCode, query.dictionaryCode())
                .eq(query.enabled() != null, SystemDictionaryEntity::getEnabled, query.enabled());
    }

    private static String limitOffset(int size, long offset) {
        return "LIMIT " + size + " OFFSET " + offset;
    }

    private static SystemDictionary toDomain(SystemDictionaryEntity entity) {
        return new SystemDictionary(entity.getId(), entity.getDictionaryCode(), entity.getDictionaryName(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(), entity.getDescription(),
                entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedBy(), entity.getUpdatedAt());
    }
}
