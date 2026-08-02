package io.github.chrisshi.mom.iam.infrastructure.persistence.query;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamSessionAdminQueryPort;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserSessionMapper;

import java.util.List;

/** Session 管理查询 Adapter。 */
public final class MybatisIamSessionAdminQuery implements IamSessionAdminQueryPort {
    private final IamUserSessionMapper mapper;

    public MybatisIamSessionAdminQuery(IamUserSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<IamAdminViews.SessionView> listSessions(
            String userId, String status, int limit, int offset) {
        return mapper.selectAdminSessions(userId, status, limit, offset);
    }

    @Override
    public List<String> activeSessionIdsForUser(String userId) {
        return mapper.selectActiveIdsByUser(userId);
    }

    @Override
    public List<String> activeSessionIdsForClient(String clientId) {
        return mapper.selectActiveIdsByClient(clientId);
    }
}
