package io.github.chrisshi.mom.system.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterException;
import io.github.chrisshi.mom.system.domain.parameter.SystemParameter;
import io.github.chrisshi.mom.system.domain.parameter.SystemParameterRepository;
import io.github.chrisshi.mom.system.infrastructure.persistence.entity.SystemParameterEntity;
import io.github.chrisshi.mom.system.infrastructure.persistence.mapper.SystemParameterMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的 System Parameter Repository Adapter。
 *
 * <p>普通 CRUD、精确读取、列表、计数、固定排序和分页全部使用 MomBaseMapper 与 Lambda Wrapper。
 * 唯一数据库专用语义是 Mapper 中固定的事务级 advisory lock；System 不维护 Mapper XML。</p>
 */
@Repository
public class MybatisSystemParameterRepository implements SystemParameterRepository {
    private final SystemParameterMapper mapper;

    public MybatisSystemParameterRepository(SystemParameterMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void lockParameterKey(String parameterKey) {
        mapper.lockParameterKey(parameterKey);
    }

    @Override
    public Optional<SystemParameter> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisSystemParameterRepository::toDomain);
    }

    @Override
    public Optional<SystemParameter> findByScopeAndKey(
            ParameterScopeType scopeType, String scopeCode, String parameterKey) {
        var query = Wrappers.<SystemParameterEntity>lambdaQuery()
                .eq(SystemParameterEntity::getScopeType, scopeType)
                .eq(SystemParameterEntity::getScopeCode, scopeCode)
                .eq(SystemParameterEntity::getParameterKey, parameterKey);
        return Optional.ofNullable(mapper.selectOne(query)).map(MybatisSystemParameterRepository::toDomain);
    }

    @Override
    public List<SystemParameter> findAllByKey(String parameterKey) {
        return mapper.selectList(
                        Wrappers.<SystemParameterEntity>lambdaQuery()
                                .eq(SystemParameterEntity::getParameterKey, parameterKey)
                                .orderByAsc(SystemParameterEntity::getScopeType)
                                .orderByAsc(SystemParameterEntity::getScopeCode)
                                .orderByAsc(SystemParameterEntity::getId))
                .stream()
                .map(MybatisSystemParameterRepository::toDomain)
                .toList();
    }

    @Override
    public SystemParameter insert(SystemParameter parameter) {
        SystemParameterEntity entity = toNewEntity(parameter);
        try {
            mapper.insert(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new SystemParameterException.Conflict("相同 Scope 与 parameterKey 的参数已存在", exception);
        }
        return findById(entity.getId()).orElseThrow(
                () -> new IllegalStateException("参数插入成功后无法读取"));
    }

    @Override
    public boolean update(SystemParameter parameter) {
        SystemParameterEntity entity = new SystemParameterEntity();
        entity.setId(parameter.id());
        entity.setVersion(parameter.version());
        entity.setValueType(parameter.valueType());
        entity.setParameterValue(parameter.parameterValue());
        entity.setDescription(parameter.description());
        return mapper.updateById(entity) == 1;
    }

    @Override
    public boolean updateStatus(SystemParameter parameter) {
        SystemParameterEntity entity = new SystemParameterEntity();
        entity.setId(parameter.id());
        entity.setVersion(parameter.version());
        entity.setEnabled(parameter.enabled());
        return mapper.updateById(entity) == 1;
    }

    @Override
    public ParameterPage findPage(ParameterQuery query) {
        long offset = Math.multiplyExact((long) query.page(), query.size());
        long total = mapper.selectCount(parameterFilter(query));
        List<SystemParameter> items = total == 0 ? List.of() : mapper.selectList(
                        parameterFilter(query)
                                .orderByAsc(SystemParameterEntity::getParameterKey)
                                .orderByAsc(SystemParameterEntity::getScopeType)
                                .orderByAsc(SystemParameterEntity::getScopeCode)
                                .orderByAsc(SystemParameterEntity::getId)
                                .last(limitOffset(query.size(), offset)))
                .stream()
                .map(MybatisSystemParameterRepository::toDomain)
                .toList();
        return new ParameterPage(items, total, query.page(), query.size());
    }

    private static LambdaQueryWrapper<SystemParameterEntity> parameterFilter(ParameterQuery query) {
        return Wrappers.<SystemParameterEntity>lambdaQuery()
                .eq(query.scopeType() != null, SystemParameterEntity::getScopeType, query.scopeType())
                .eq(query.scopeCode() != null, SystemParameterEntity::getScopeCode, query.scopeCode())
                .eq(query.parameterKey() != null, SystemParameterEntity::getParameterKey, query.parameterKey())
                .eq(query.enabled() != null, SystemParameterEntity::getEnabled, query.enabled());
    }

    private static String limitOffset(int size, long offset) {
        return "LIMIT " + size + " OFFSET " + offset;
    }

    private static SystemParameterEntity toNewEntity(SystemParameter parameter) {
        SystemParameterEntity entity = new SystemParameterEntity();
        entity.setScopeType(parameter.scopeType());
        entity.setScopeCode(parameter.scopeCode());
        entity.setParameterKey(parameter.parameterKey());
        entity.setValueType(parameter.valueType());
        entity.setParameterValue(parameter.parameterValue());
        entity.setEnabled(parameter.enabled());
        entity.setDescription(parameter.description());
        return entity;
    }

    private static SystemParameter toDomain(SystemParameterEntity entity) {
        return new SystemParameter(entity.getId(), entity.getScopeType(), entity.getScopeCode(),
                entity.getParameterKey(), entity.getValueType(), entity.getParameterValue(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(), entity.getDescription(),
                entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedBy(), entity.getUpdatedAt());
    }
}
