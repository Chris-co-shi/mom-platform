package io.github.chrisshi.mom.auth.infrastructure.query;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 登录阶段的多表 authority 聚合查询。
 *
 * <p>该 Mapper 位于 infrastructure.query，是因为查询跨越 User-Role、Role、Role-Permission 和 Permission；
 * 它不是单表 CRUD，也不需要伪装成某个 Entity 的 BaseMapper。返回值只需要最终 authority 字符串，
 * 因此当前不额外创建无信息量的 Row/Projection。</p>
 */
@Mapper
public interface AuthenticationQueryMapper {

    /**
     * 查询指定用户在当前数据库快照下拥有的 ROLE_* 与 Permission code。
     *
     * <p>结果用于登录时构建 authority 快照；角色/权限后续变更不会自动修改已经签发的 V1 Token。</p>
     *
     * @param userId MOM 用户主键
     * @return 去重并按 SQL 约定稳定排序的 authority 字符串
     */
    @Select("""
        SELECT authority
                FROM (
                    SELECT 'ROLE_' || role.code AS authority
                    FROM auth_user_role user_role
                    JOIN auth_role role ON role.id = user_role.role_id
                    WHERE user_role.user_id = #{userId}
                      AND role.deleted = false
                      AND role.enabled = true

                    UNION

                    SELECT permission.code AS authority
                    FROM auth_user_role user_role
                    JOIN auth_role role ON role.id = user_role.role_id
                    JOIN auth_role_permission role_permission ON role_permission.role_id = role.id
                    JOIN auth_permission permission ON permission.id = role_permission.permission_id
                    WHERE user_role.user_id = #{userId}
                      AND role.deleted = false
                      AND role.enabled = true
                      AND permission.deleted = false
                      AND permission.enabled = true
                ) authority_set
                ORDER BY authority
        """)
    List<String> selectAuthoritiesByUserId(@Param("userId") String userId);
}
