package io.github.chrisshi.mom.system.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.repository.CrudRepository;
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
 * 基于 MyBatis-Plus Repository 抽象的 System Parameter 持久化 Adapter。
 *
 * <p>该类型在 Infrastructure 内部复用 {@link CrudRepository} 的单表 CRUD、计数和列表能力，对上仍只实现
 * 框架无关的 {@link SystemParameterRepository}。Entity、Mapper、Wrapper 和通用 CRUD 不得泄漏到
 * Application、Domain 或 Web。唯一数据库专用语义是 Mapper 中固定的事务级 advisory lock。</p>
 *
 * <p>事务、授权、参数类型一致性和领域校验由 Application/Domain 负责；唯一冲突和乐观锁失败继续转换为
 * MOM 稳定语义。数据库不可用时异常向上传播并触发本地事务回滚，不进行缓存或内存降级。</p>
 */
@Repository
public class MybatisSystemParameterRepository
        extends CrudRepository<SystemParameterMapper, SystemParameterEntity>
        implements SystemParameterRepository {

    /** 在当前事务中获取同参数 Key 的 PostgreSQL advisory lock。 */
    @Override
    public void lockParameterKey(String parameterKey) {
        getBaseMapper().lockParameterKey(parameterKey);
    }

    /** 按技术主键读取并转换为领域模型。 */
    @Override
    public Optional<SystemParameter> findById(String id) {
        return Optional.ofNullable(getById(id)).map(MybatisSystemParameterRepository::toDomain);
    }

    /** 按作用域和稳定 Key 精确读取。 */
    @Override
    public Optional<SystemParameter> findByScopeAndKey(
            ParameterScopeType scopeType, String scopeCode, String parameterKey) {
        var query = Wrappers.<SystemParameterEntity>lambdaQuery()
                .eq(SystemParameterEntity::getScopeType, scopeType)
                .eq(SystemParameterEntity::getScopeCode, scopeCode)
                .eq(SystemParameterEntity::getParameterKey, parameterKey);
        return Optional.ofNullable(getOne(query)).map(MybatisSystemParameterRepository::toDomain);
    }

    /** 读取同 Key 的全部作用域，用于 Application 强制值类型一致性。 */
    @Override
    public List<SystemParameter> findAllByKey(String parameterKey) {
        return list(Wrappers.<SystemParameterEntity>lambdaQuery()
                        .eq(SystemParameterEntity::getParameterKey, parameterKey)
                        .orderByAsc(SystemParameterEntity::getScopeType)
                        .orderByAsc(SystemParameterEntity::getScopeCode)
                        .orderByAsc(SystemParameterEntity::getId))
                .stream()
                .map(MybatisSystemParameterRepository::toDomain)
                .toList();
    }

    /** 插入参数；数据库唯一冲突转换为稳定业务冲突。 */
    @Override
    public SystemParameter insert(SystemParameter parameter) {
        SystemParameterEntity entity = toNewEntity(parameter);
        try {
            if (!save(entity)) {
                throw new IllegalStateException("参数未插入预期的一行");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new SystemParameterException.Conflict("相同 Scope 与 parameterKey 的参数已存在", exception);
        }
        return findById(entity.getId()).orElseThrow(
                () -> new IllegalStateException("参数插入成功后无法读取"));
    }

    /** 使用 Entity Version 执行 CAS 内容更新。 */
    @Override
    public boolean update(SystemParameter parameter) {
        SystemParameterEntity entity = new SystemParameterEntity();
        entity.setId(parameter.id());
        entity.setVersion(parameter.version());
        entity.setValueType(parameter.valueType());
        entity.setParameterValue(parameter.parameterValue());
        entity.setDescription(parameter.description());
        return updateById(entity);
    }

    /** 使用 Entity Version 执行 CAS 状态更新。 */
    @Override
    public boolean updateStatus(SystemParameter parameter) {
        SystemParameterEntity entity = new SystemParameterEntity();
        entity.setId(parameter.id());
        entity.setVersion(parameter.version());
        entity.setEnabled(parameter.enabled());
        return updateById(entity);
    }

    /** 按受控单表条件分页，返回 Infrastructure 无关的 Domain Page。 */
    @Override
    public ParameterPage findPage(ParameterQuery query) {
        long offset = Math.multiplyExact((long) query.page(), query.size());
        long total = count(parameterFilter(query));
        List<SystemParameter> items = total == 0 ? List.of() : list(
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

    /** 构造只包含服务端白名单字段的查询条件。 */
    private static LambdaQueryWrapper<SystemParameterEntity> parameterFilter(ParameterQuery query) {
        return Wrappers.<SystemParameterEntity>lambdaQuery()
                .eq(query.scopeType() != null, SystemParameterEntity::getScopeType, query.scopeType())
                .eq(query.scopeCode() != null, SystemParameterEntity::getScopeCode, query.scopeCode())
                .eq(query.parameterKey() != null, SystemParameterEntity::getParameterKey, query.parameterKey())
                .eq(query.enabled() != null, SystemParameterEntity::getEnabled, query.enabled());
    }

    /** 生成仅包含已校验非负数字的固定 PostgreSQL 分页尾句。 */
    private static String limitOffset(int size, long offset) {
        return "LIMIT " + size + " OFFSET " + offset;
    }

    /** 将新领域对象转换为待插入 Entity，由 MyBatis-Plus 填充技术字段。 */
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

    /** 将数据库行模型转换为不可泄漏 Entity 的领域快照。 */
    private static SystemParameter toDomain(SystemParameterEntity entity) {
        return new SystemParameter(entity.getId(), entity.getScopeType(), entity.getScopeCode(),
                entity.getParameterKey(), entity.getValueType(), entity.getParameterValue(),
                Boolean.TRUE.equals(entity.getEnabled()), entity.getVersion(), entity.getDescription(),
                entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedBy(), entity.getUpdatedAt());
    }
}
