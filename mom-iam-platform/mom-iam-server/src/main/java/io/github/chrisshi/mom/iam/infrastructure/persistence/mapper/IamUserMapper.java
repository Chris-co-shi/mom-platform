package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/**
 * IAM 用户表 Mapper。
 *
 * <p>除 MyBatis-Plus 基础实体访问外，显式承载管理端乐观锁更新和认证状态的数据库原子更新。
 * 认证失败计数禁止拆成先查后写；管理投影 SQL 位于同名 XML，且不选择密码摘要。</p>
 */
@Mapper
public interface IamUserMapper extends MomBaseMapper<IamUserEntity> {

    /** @return 按用户名排序且不含凭证材料的用户管理分页投影 */
    List<IamAdminViews.UserView> selectAdminUsers(
            @Param("userType") String userType,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /** @return 未删除用户管理投影，未找到时为 null */
    IamAdminViews.UserView selectAdminById(@Param("userId") String userId);

    /** @return 持有 {@code FOR UPDATE} 行锁的用户管理投影，未找到时为 null */
    IamAdminViews.UserView selectAdminForUpdate(@Param("userId") String userId);

    /** 按客户端版本更新展示名并推进聚合版本。 */
    @Update("""
            UPDATE iam_user
               SET display_name=#{displayName},updated_at=#{now},updated_by=#{actor},version=version+1
             WHERE id=#{userId} AND deleted=false AND version=#{version}
            """)
    int updateDisplayName(@Param("userId") String userId, @Param("displayName") String displayName,
            @Param("version") long version, @Param("actor") String actor, @Param("now") Instant now);

    /** 按客户端版本更新账号状态并推进聚合版本。 */
    @Update("""
            UPDATE iam_user
               SET status=#{status},updated_at=#{now},updated_by=#{actor},version=version+1
             WHERE id=#{userId} AND deleted=false AND version=#{version}
            """)
    int updateStatus(@Param("userId") String userId, @Param("status") IamRecordStatus status,
            @Param("version") long version, @Param("actor") String actor, @Param("now") Instant now);

    /** 按客户端版本清除账号锁定并推进聚合版本。 */
    @Update("""
            UPDATE iam_user
               SET failed_login_count=0,locked_until=NULL,updated_at=#{now},
                   updated_by=#{actor},version=version+1
             WHERE id=#{userId} AND deleted=false AND version=#{version}
            """)
    int unlock(@Param("userId") String userId, @Param("version") long version,
            @Param("actor") String actor, @Param("now") Instant now);

    /** 按客户端版本重置密码状态，摘要只作为绑定参数进入数据库。 */
    @Update("""
            UPDATE iam_user
               SET password_hash=#{passwordHash},password_change_required=true,failed_login_count=0,
                   locked_until=NULL,updated_at=#{now},updated_by=#{actor},version=version+1
             WHERE id=#{userId} AND deleted=false AND version=#{version}
            """)
    int resetPassword(@Param("userId") String userId, @Param("passwordHash") String passwordHash,
            @Param("version") long version, @Param("actor") String actor, @Param("now") Instant now);

    /** 按客户端版本逻辑删除用户，并同步禁用账号。 */
    @Update("""
            UPDATE iam_user
               SET status='DISABLED',deleted=true,updated_at=#{now},
                   updated_by=#{actor},version=version+1
             WHERE id=#{userId} AND deleted=false AND version=#{version}
            """)
    int logicalDelete(@Param("userId") String userId, @Param("version") long version,
            @Param("actor") String actor, @Param("now") Instant now);

    /** 关系全量替换完成后按原版本推进用户聚合版本。 */
    @Update("""
            UPDATE iam_user
               SET updated_at=#{now},updated_by=#{actor},version=version+1
             WHERE id=#{userId} AND deleted=false AND version=#{version}
            """)
    int advanceVersion(@Param("userId") String userId, @Param("version") long version,
            @Param("actor") String actor, @Param("now") Instant now);

    /** 到期锁定在认证读取前原子清理。 */
    @Update("""
            UPDATE iam_user
               SET failed_login_count=0,locked_until=NULL,updated_at=#{now},
                   updated_by=#{actor},version=version+1
             WHERE username=#{username} AND deleted=false
               AND locked_until IS NOT NULL AND locked_until<=#{now}
            """)
    int clearExpiredLock(@Param("username") String username, @Param("now") Instant now,
            @Param("actor") String actor);

    /** 原子累计登录失败次数，并在达到阈值时设置锁定截止时间。 */
    @Update("""
            UPDATE iam_user
               SET failed_login_count=failed_login_count+1,
                   locked_until=CASE WHEN failed_login_count+1>=#{maximumAttempts}
                       THEN #{lockedUntil} ELSE locked_until END,
                   updated_at=#{now},updated_by=#{actor},version=version+1
             WHERE username=#{username} AND status='ENABLED' AND deleted=false
               AND (locked_until IS NULL OR locked_until<=#{now})
            """)
    int recordLoginFailure(@Param("username") String username,
            @Param("maximumAttempts") int maximumAttempts, @Param("lockedUntil") Instant lockedUntil,
            @Param("now") Instant now, @Param("actor") String actor);

    /** 登录成功后原子清理失败状态并记录最近登录时间。 */
    @Update("""
            UPDATE iam_user
               SET failed_login_count=0,locked_until=NULL,last_login_at=#{now},
                   updated_at=#{now},updated_by=#{actor},version=version+1
             WHERE username=#{username} AND status='ENABLED' AND deleted=false
            """)
    int recordLoginSuccess(@Param("username") String username, @Param("now") Instant now,
            @Param("actor") String actor);

    /** 首次改密完成后原子替换摘要并解除强制改密标记。 */
    @Update("""
            UPDATE iam_user
               SET password_hash=#{passwordHash},password_change_required=false,
                   failed_login_count=0,locked_until=NULL,updated_at=#{now},
                   updated_by=#{actor},version=version+1
             WHERE username=#{username} AND status='ENABLED' AND deleted=false
            """)
    int changePassword(@Param("username") String username, @Param("passwordHash") String passwordHash,
            @Param("now") Instant now, @Param("actor") String actor);
}
