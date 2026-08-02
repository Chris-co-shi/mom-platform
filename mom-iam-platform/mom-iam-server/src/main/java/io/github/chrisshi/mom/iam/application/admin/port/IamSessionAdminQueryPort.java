package io.github.chrisshi.mom.iam.application.admin.port;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;

import java.util.List;

/** 管理端 Session 非敏感查询 Port。 */
public interface IamSessionAdminQueryPort {
    List<IamAdminViews.SessionView> listSessions(
            String userId, String status, int limit, int offset);
    List<String> activeSessionIdsForUser(String userId);
    List<String> activeSessionIdsForClient(String clientId);
}
