package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** IAM 角色目录 Mapper，管理查询与精确版本更新 SQL 位于该表边界。 */
@Mapper
public interface IamRoleMapper extends MomBaseMapper<IamRoleEntity> {

    /** @return 按角色编码排序的管理分页投影 */
    List<IamAdminViews.RoleView> selectAdminRoles(
            @Param("userType") String userType, @Param("limit") int limit, @Param("offset") int offset);

    /** @return 持有 {@code FOR UPDATE} 行锁的角色投影 */
    IamAdminViews.RoleView selectAdminForUpdate(@Param("roleId") String roleId);

    /** @return 指定 ID 集合中仍未删除的角色投影 */
    List<IamAdminViews.RoleView> selectAdminByIds(@Param("roleIds") Collection<String> roleIds);

    /** 按客户端版本更新非删除角色并推进版本。 */
    @Update("""
            UPDATE iam_role
               SET name=#{name},description=#{description},status=#{status},
                   updated_at=#{now},updated_by=#{actor},version=version+1
             WHERE id=#{roleId} AND deleted=false AND version=#{version}
            """)
    int updateAdminRole(@Param("roleId") String roleId, @Param("name") String name,
            @Param("description") String description, @Param("status") IamRecordStatus status,
            @Param("version") long version, @Param("actor") String actor, @Param("now") Instant now);

    /** Permission 全量替换后按原版本推进角色聚合版本。 */
    @Update("""
            UPDATE iam_role
               SET updated_at=#{now},updated_by=#{actor},version=version+1
             WHERE id=#{roleId} AND deleted=false AND version=#{version}
            """)
    int advanceVersion(@Param("roleId") String roleId, @Param("version") long version,
            @Param("actor") String actor, @Param("now") Instant now);
}
