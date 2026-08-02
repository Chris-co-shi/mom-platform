package io.github.chrisshi.mom.iam.application.admin.port;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;

import java.util.List;
import java.util.Optional;

/** 用户管理只读 Port。 */
public interface IamUserAdminQueryPort {
    List<IamAdminViews.UserView> listUsers(
            String userType, String status, int limit, int offset);
    Optional<IamAdminViews.UserView> findUser(String userId);
}
