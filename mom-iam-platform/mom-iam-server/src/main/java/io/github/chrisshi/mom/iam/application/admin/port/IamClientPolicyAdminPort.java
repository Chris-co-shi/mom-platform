package io.github.chrisshi.mom.iam.application.admin.port;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** MOM Client Policy 管理 Port；不修改 SAS Registered Client。 */
public interface IamClientPolicyAdminPort {
    List<IamAdminViews.ClientView> listClients();
    Optional<IamAdminViews.ClientView> lockClient(String clientId);
    void updateClientStatus(
            String clientId, IamRecordStatus status, long expectedVersion,
            String actor, Instant now);
}
