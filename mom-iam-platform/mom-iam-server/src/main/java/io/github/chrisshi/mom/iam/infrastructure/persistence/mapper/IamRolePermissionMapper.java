package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRolePermissionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 角色 Permission 关系 Mapper；替换操作必须由已锁定角色聚合的应用事务调用。 */
@Mapper
public interface IamRolePermissionMapper extends MomBaseMapper<IamRolePermissionEntity> {

    /** @return 角色当前全部 Permission ID */
    @Select("""
            SELECT permission_id FROM iam_role_permission
             WHERE role_id=#{roleId}
             ORDER BY permission_id
            """)
    List<String> selectPermissionIds(@Param("roleId") String roleId);

    /** @return 角色当前仍有效且启用的 Permission ID */
    List<String> selectEnabledPermissionIds(@Param("roleId") String roleId);

    /** 删除角色全部 Permission 关系；必须与父版本推进处于同一事务。 */
    @Delete("DELETE FROM iam_role_permission WHERE role_id=#{roleId}")
    int deleteByRoleId(@Param("roleId") String roleId);
}
