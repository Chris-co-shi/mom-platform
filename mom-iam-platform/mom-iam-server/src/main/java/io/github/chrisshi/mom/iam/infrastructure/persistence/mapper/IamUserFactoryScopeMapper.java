package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserFactoryScopeEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/** 用户 Factory Scope Mapper；MDM Factory 仅作为外部引用保存。 */
@Mapper
public interface IamUserFactoryScopeMapper extends MomBaseMapper<IamUserFactoryScopeEntity> {

    /** @return 用户当前启用的 Factory ID */
    @Select("""
            SELECT factory_id FROM iam_user_factory_scope
             WHERE user_id=#{userId} AND status='ENABLED'
             ORDER BY factory_id
            """)
    List<String> selectFactoryIds(@Param("userId") String userId);

    /** @return 已考虑有效期的当前 Factory Scope */
    List<String> selectEffectiveFactoryIds(@Param("userId") String userId, @Param("now") Instant now);

    /** 删除用户全部 Factory Scope；必须与父版本推进处于同一事务。 */
    @Delete("DELETE FROM iam_user_factory_scope WHERE user_id=#{userId}")
    int deleteByUserId(@Param("userId") String userId);
}
