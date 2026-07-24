package io.github.chrisshi.mom.iam.infrastructure.persistence.mapper;

import io.github.chrisshi.mom.data.mapper.MomBaseMapper;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamOauthClientPolicyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/** MOM OAuth Client Policy Mapper；官方协议表只在管理只读 JOIN 中出现。 */
@Mapper
public interface IamOauthClientPolicyMapper extends MomBaseMapper<IamOauthClientPolicyEntity> {

    /** @return Client Policy 与官方 Registered Client 的只读联合投影 */
    List<IamAdminViews.ClientView> selectAdminClients();

    /** @return 对 Policy 行持有 {@code FOR UPDATE} 锁的联合投影 */
    IamAdminViews.ClientView selectAdminForUpdate(@Param("clientId") String clientId);

    /** 按客户端版本更新 Policy 状态。 */
    @Update("""
            UPDATE iam_oauth_client_policy
               SET status=#{status},updated_at=#{now},updated_by=#{actor},version=version+1
             WHERE client_id=#{clientId} AND version=#{version}
            """)
    int updateStatus(@Param("clientId") String clientId, @Param("status") IamRecordStatus status,
            @Param("version") long version, @Param("actor") String actor, @Param("now") Instant now);
}
