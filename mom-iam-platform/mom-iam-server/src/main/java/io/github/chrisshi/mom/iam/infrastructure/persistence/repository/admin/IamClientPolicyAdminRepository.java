package io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamOauthClientPolicyMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MOM Client Policy 管理仓储。
 *
 * <p>官方 {@code oauth2_registered_client} 仅用于只读 LEFT JOIN；状态与版本更新始终限定在 MOM
 * 自有 {@code iam_oauth_client_policy} 行，且锁语义为 {@code FOR UPDATE OF p}。</p>
 */
public final class IamClientPolicyAdminRepository {
    private final IamOauthClientPolicyMapper mapper;

    /** @param mapper Client Policy 受控 Mapper */
    public IamClientPolicyAdminRepository(IamOauthClientPolicyMapper mapper) {
        this.mapper = mapper;
    }

    /** @return Client Policy 与注册信息联合投影 */
    public List<IamAdminViews.ClientView> listClients() {
        return mapper.selectAdminClients();
    }

    /** @return 对 MOM Policy 行持有数据库行锁的联合投影 */
    public Optional<IamAdminViews.ClientView> lockClient(String clientId) {
        return Optional.ofNullable(mapper.selectAdminForUpdate(clientId));
    }

    /** 按客户端版本更新 Client Policy 状态。 */
    public void updateClientStatus(
            String clientId, IamRecordStatus status, long version, String actor, Instant now) {
        if (mapper.updateStatus(clientId, status, version, actor, now) != 1) {
            throw new IllegalStateException("Client Policy 已被并发修改");
        }
    }
}
