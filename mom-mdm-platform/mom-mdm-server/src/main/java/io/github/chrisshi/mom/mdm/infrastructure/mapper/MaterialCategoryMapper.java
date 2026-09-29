package io.github.chrisshi.mom.mdm.infrastructure.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.mdm.infrastructure.entity.MaterialCategoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * MaterialCategory 单表数据访问入口。
 *
 * <p>该 Mapper 仅由 MDM Application 使用，依赖 MyBatis-Plus 完成普通 CRUD、条件分页和逐级父节点查询；
 * 不承载树规则、事务或 HTTP 语义。数据库异常保持失败并由 Application 事务回滚。</p>
 */
@Mapper
public interface MaterialCategoryMapper extends MomBaseMapper<MaterialCategoryEntity> {
}
