package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamRefreshTokenEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

/** Refresh Token 状态 Mapper；只按 HMAC 摘要查询，绝不接收或保存明文 Token。 */
@Mapper
public interface IamRefreshTokenMapper extends MomBaseMapper<IamRefreshTokenEntity> {

    /** 插入只包含 HMAC 摘要的 Refresh Token 状态，不经过普通业务审计填充。 */
    @Insert("""
            INSERT INTO iam_refresh_token (
                id,session_id,token_digest,sequence_no,status,issued_at,expires_at,created_at)
            VALUES (
                #{id},#{sessionId},#{tokenDigest},#{sequenceNo},#{status},
                #{issuedAt},#{expiresAt},#{createdAt})
            """)
    int insertAuthenticationRefresh(IamRefreshTokenEntity refresh);

    /** @return 按摘要持有 {@code FOR UPDATE} 行锁的 Refresh 状态 */
    @Select("SELECT * FROM iam_refresh_token WHERE token_digest=#{digest} FOR UPDATE")
    IamRefreshTokenEntity selectForUpdateByDigest(@Param("digest") String digest);

    /** 先把旧 ACTIVE Token 改为 ROTATED，释放每个 Session 的 ACTIVE 唯一槽位。 */
    @Update("""
            UPDATE iam_refresh_token
               SET status='ROTATED',consumed_at=#{consumedAt}
             WHERE id=#{tokenId} AND status='ACTIVE'
            """)
    int markRotated(@Param("tokenId") String tokenId, @Param("consumedAt") Instant consumedAt);

    /** 记录 Rotation 后继 Token ID。 */
    @Update("""
            UPDATE iam_refresh_token
               SET replaced_by_token_id=#{replacementId}
             WHERE id=#{tokenId} AND status='ROTATED'
            """)
    int linkReplacement(@Param("tokenId") String tokenId,
            @Param("replacementId") String replacementId);

    /** 将 Session 的 ACTIVE Refresh Token 标记为 EXPIRED。 */
    @Update("""
            UPDATE iam_refresh_token SET status='EXPIRED'
             WHERE session_id=#{sessionId} AND status='ACTIVE'
            """)
    int expireActiveBySession(@Param("sessionId") String sessionId);

    /** 将 Session 的 ACTIVE Refresh Token 全部撤销。 */
    @Update("""
            UPDATE iam_refresh_token
               SET status='REVOKED',revoked_at=#{now}
             WHERE session_id=#{sessionId} AND status='ACTIVE'
            """)
    int revokeActiveBySession(@Param("sessionId") String sessionId, @Param("now") Instant now);
}
