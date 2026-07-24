package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserSessionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/** IAM Session Mapper，所有 Rotation、撤销和重放判定更新保持数据库行锁与条件更新。 */
@Mapper
public interface IamUserSessionMapper extends MomBaseMapper<IamUserSessionEntity> {

    /**
     * 插入认证系统主体创建的 Session。
     *
     * <p>该显式 SQL 不调用普通业务审计填充器，确保 created_by/updated_by 保持稳定 SYSTEM Actor。</p>
     */
    @Insert("""
            INSERT INTO iam_user_session (
                id,user_id,client_id,channel,status,login_at,last_refresh_at,
                absolute_expires_at,latest_access_token_expires_at,
                ip_address,user_agent,device_name,
                created_at,created_by,updated_at,updated_by,version)
            VALUES (
                #{session.id},#{session.userId},#{session.clientId},#{session.channel},
                #{session.status},#{session.loginAt},NULL,#{session.absoluteExpiresAt},
                #{session.latestAccessTokenExpiresAt},#{session.ipAddress},#{session.userAgent},
                #{session.deviceName},#{session.createdAt},#{actor},
                #{session.updatedAt},#{actor},0)
            """)
    int insertAuthenticationSession(
            @Param("session") IamUserSessionEntity session, @Param("actor") String actor);

    /** @return 管理端 Session 分页投影，不含任何 Token 材料 */
    List<IamAdminViews.SessionView> selectAdminSessions(
            @Param("userId") String userId, @Param("status") String status,
            @Param("limit") int limit, @Param("offset") int offset);

    /** @return 用户全部 ACTIVE Session ID */
    @Select("""
            SELECT id FROM iam_user_session
             WHERE user_id=#{userId} AND status='ACTIVE'
             ORDER BY id
            """)
    List<String> selectActiveIdsByUser(@Param("userId") String userId);

    /** @return Client 全部 ACTIVE Session ID */
    @Select("""
            SELECT id FROM iam_user_session
             WHERE client_id=#{clientId} AND status='ACTIVE'
             ORDER BY id
            """)
    List<String> selectActiveIdsByClient(@Param("clientId") String clientId);

    /** @return 持有 {@code FOR UPDATE} 行锁的 Session 实体 */
    @Select("SELECT * FROM iam_user_session WHERE id=#{sessionId} FOR UPDATE")
    IamUserSessionEntity selectForUpdate(@Param("sessionId") String sessionId);

    /** Refresh 成功后推进 Session 状态和版本。 */
    @Update("""
            UPDATE iam_user_session
               SET last_refresh_at=#{refreshedAt},
                   latest_access_token_expires_at=#{latestAccessExpiresAt},
                   updated_at=#{refreshedAt},updated_by=#{actor},version=version+1
             WHERE id=#{sessionId} AND status='ACTIVE'
            """)
    int updateRefreshSuccess(@Param("sessionId") String sessionId,
            @Param("refreshedAt") Instant refreshedAt,
            @Param("latestAccessExpiresAt") Instant latestAccessExpiresAt,
            @Param("actor") String actor);

    /** 将 ACTIVE Session 条件更新为 EXPIRED。 */
    @Update("""
            UPDATE iam_user_session
               SET status='EXPIRED',updated_at=#{now},updated_by=#{actor},version=version+1
             WHERE id=#{sessionId} AND status='ACTIVE'
            """)
    int markExpired(@Param("sessionId") String sessionId, @Param("now") Instant now,
            @Param("actor") String actor);

    /** 将 Session 标记为 COMPROMISED，重复判定保持幂等。 */
    @Update("""
            UPDATE iam_user_session
               SET status='COMPROMISED',revoked_at=#{now},revoked_by=#{actor},
                   revoke_reason=#{reason},updated_at=#{now},updated_by=#{actor},version=version+1
             WHERE id=#{sessionId} AND status IN ('ACTIVE','COMPROMISED')
            """)
    int compromise(@Param("sessionId") String sessionId, @Param("now") Instant now,
            @Param("actor") String actor, @Param("reason") String reason);

    /** 将 ACTIVE Session 条件更新为 REVOKED。 */
    @Update("""
            UPDATE iam_user_session
               SET status='REVOKED',revoked_at=#{now},revoked_by=#{actor},
                   revoke_reason=#{reason},updated_at=#{now},updated_by=#{actor},version=version+1
             WHERE id=#{sessionId} AND status='ACTIVE'
            """)
    int revoke(@Param("sessionId") String sessionId, @Param("now") Instant now,
            @Param("actor") String actor, @Param("reason") String reason);
}
