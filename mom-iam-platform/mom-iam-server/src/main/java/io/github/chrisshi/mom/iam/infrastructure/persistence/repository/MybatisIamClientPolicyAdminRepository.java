package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import io.github.chrisshi.mom.iam.application.admin.IamAdminExceptions;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamClientPolicyAdminPort;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamOauthClientPolicyMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** MOM Client Policy 与 SAS 注册信息联合 Adapter。 */
public final class MybatisIamClientPolicyAdminRepository
        implements IamClientPolicyAdminPort {
    private final IamOauthClientPolicyMapper mapper;

    public MybatisIamClientPolicyAdminRepository(IamOauthClientPolicyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<IamAdminViews.ClientView> listClients() {
        return mapper.selectAdminClients();
    }

    @Override
    public Optional<IamAdminViews.ClientView> lockClient(String clientId) {
        return Optional.ofNullable(mapper.selectAdminForUpdate(clientId));
    }

    @Override
    public void updateClientStatus(
            String clientId, IamRecordStatus status, long expectedVersion,
            String actor, Instant now) {
        if (mapper.updateStatus(
                clientId, status, expectedVersion, actor, now) != 1) {
            throw new IamAdminExceptions.StaleVersion("version 已过期，请重新读取后重试");
        }
    }
}
