package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserRoleEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/** 用户角色分配 Mapper，承载有效期过滤和 PLATFORM_ADMIN 安全统计。 */
@Mapper
public interface IamUserRoleMapper extends MomBaseMapper<IamUserRoleEntity> {

    /** @return 用户当前启用的角色关系 ID，稳定排序 */
    @Select("""
            SELECT role_id FROM iam_user_role
             WHERE user_id=#{userId} AND status='ENABLED'
             ORDER BY role_id
            """)
    List<String> selectRoleIds(@Param("userId") String userId);

    /** 删除用户全部角色关系；必须在已锁定父聚合的本地事务中调用。 */
    @Delete("DELETE FROM iam_user_role WHERE user_id=#{userId}")
    int deleteByUserId(@Param("userId") String userId);

    /** @return 与用户类型匹配的当前有效角色编码 */
    List<String> selectEffectiveRoleCodes(@Param("userId") String userId,
            @Param("userType") UserType userType, @Param("now") Instant now);

    /** @return 通过有效角色并集计算出的当前有效 Permission 编码 */
    List<String> selectEffectivePermissionCodes(@Param("userId") String userId,
            @Param("userType") UserType userType, @Param("now") Instant now);

    /** @return 指定用户是否是当前有效 PLATFORM_ADMIN */
    boolean existsEffectivePlatformAdmin(@Param("userId") String userId, @Param("now") Instant now);

    /** @return 当前有效 PLATFORM_ADMIN 用户数 */
    int countEffectivePlatformAdmins(@Param("now") Instant now);
}
