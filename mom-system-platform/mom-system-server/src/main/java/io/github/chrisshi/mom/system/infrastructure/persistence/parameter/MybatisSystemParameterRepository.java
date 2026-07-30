package io.github.chrisshi.mom.system.infrastructure.persistence.parameter;

import io.github.chrisshi.mom.system.api.ParameterScopeType;
import io.github.chrisshi.mom.system.application.parameter.SystemParameterException;
import io.github.chrisshi.mom.system.domain.parameter.SystemParameter;
import io.github.chrisshi.mom.system.domain.parameter.SystemParameterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的 System Parameter Repository Adapter。
 *
 * <p>Adapter 负责领域聚合与行模型转换、参数化分页和数据库异常脱敏。所有操作共享服务唯一 DataSource；
 * 不创建连接池、缓存或跨 Schema 查询。唯一约束冲突转换为稳定 Conflict，其他基础设施错误保持失败。</p>
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
        return Optional.ofNullable(mapper.selectByScopeAndKey(scopeType, scopeCode, parameterKey))
                .map(MybatisSystemParameterRepository::toDomain);
    }

    @Override
    public List<SystemParameter> findAllByKey(String parameterKey) {
        return mapper.selectAllByKey(parameterKey).stream()
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
        List<SystemParameter> items = mapper.selectPage(query.scopeType(), query.scopeCode(),
                        query.parameterKey(), query.enabled(), query.size(), offset)
                .stream().map(MybatisSystemParameterRepository::toDomain).toList();
        long total = mapper.countPage(query.scopeType(), query.scopeCode(), query.parameterKey(), query.enabled());
        return new ParameterPage(items, total, query.page(), query.size());
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
