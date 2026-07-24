package io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserSessionMapper;

import java.util.List;

/** 管理端 Session 查询仓储；不读取或返回 Refresh Token 表。 */
public final class IamSessionAdminRepository {
    private final IamUserSessionMapper mapper;

    /** @param mapper Session 表受控 Mapper */
    public IamSessionAdminRepository(IamUserSessionMapper mapper) {
        this.mapper = mapper;
    }

    /** @return 不含 Token 材料的 Session 分页结果 */
    public List<IamAdminViews.SessionView> listSessions(
            String userId, String status, int limit, int offset) {
        return mapper.selectAdminSessions(userId, status, limit, offset);
    }

    /** @return 用户全部 ACTIVE Session ID */
    public List<String> activeSessionIdsForUser(String userId) {
        return mapper.selectActiveIdsByUser(userId);
    }

    /** @return Client 全部 ACTIVE Session ID */
    public List<String> activeSessionIdsForClient(String clientId) {
        return mapper.selectActiveIdsByClient(clientId);
    }
}
