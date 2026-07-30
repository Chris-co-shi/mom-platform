package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamExternalUserBindingEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部用户唯一 Party Binding 的单表 MyBatis-Plus Mapper。
 *
 * <p>该类型位于 IAM Infrastructure，只依赖统一 {@link MomBaseMapper}，普通查询和更新由上层仓储通过
 * Lambda Wrapper 表达；它不得承载跨 Schema 查询、业务事务或外部 Party 校验。Mapper 无可变状态，线程安全
 * 由 MyBatis 代理保证；数据库不可用时异常向仓储传播并由本地事务回滚。</p>
 */
@Mapper
public interface IamExternalUserBindingMapper extends MomBaseMapper<IamExternalUserBindingEntity> {
}
