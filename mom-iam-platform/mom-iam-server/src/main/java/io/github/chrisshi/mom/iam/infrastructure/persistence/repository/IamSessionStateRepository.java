package io.github.chrisshi.mom.iam.infrastructure.persistence.repository;

import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserSessionEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserSessionMapper;

import java.util.Optional;

/** 用户授权 Session 状态仓储；S02 不实现创建流程、撤销广播或 Redis revoked sid。 */
public class IamSessionStateRepository {
    private final IamUserSessionMapper mapper;

    /** @param mapper Session Mapper */
    public IamSessionStateRepository(IamUserSessionMapper mapper) { this.mapper = mapper; }

    /** @param sessionId 未来 JWT sid @return Session */
    public Optional<IamUserSessionEntity> findById(String sessionId) {
        return Optional.ofNullable(mapper.selectById(sessionId));
    }

    /** @param session 已完成 Client Policy 与时间校验的 Session */
    public void insert(IamUserSessionEntity session) {
        if (mapper.insert(session) != 1) throw new IllegalStateException("IAM Session 写入失败");
    }
}
