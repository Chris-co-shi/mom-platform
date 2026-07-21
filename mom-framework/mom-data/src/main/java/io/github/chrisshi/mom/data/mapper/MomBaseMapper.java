package io.github.chrisshi.mom.data.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * MOM 受治理的 MyBatis-Plus Mapper 基础接口。
 *
 * <p>仅 Wrapper 且没有 Entity 的 UPDATE 不会触发自动填充，因此直接拒绝。普通业务应使用 updateById
 * 或 update(entity, wrapper)；自定义 SQL/JdbcTemplate 必须显式写 updated_at/updated_by 并测试。</p>
 *
 * @param <T> 持久化实体类型
 */
public interface MomBaseMapper<T> extends BaseMapper<T> {

    /** Wrapper-only Update 始终被拒绝，防止静默绕过审计。 */
    @Override
    default int update(Wrapper<T> updateWrapper) {
        throw new UnsupportedOperationException(
                "Wrapper-only Update 不触发实体审计填充；请使用 update(entity, wrapper) 或显式审计 SQL");
    }
}
