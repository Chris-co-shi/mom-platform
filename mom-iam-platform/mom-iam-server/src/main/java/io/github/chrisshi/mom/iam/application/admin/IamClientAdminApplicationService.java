package io.github.chrisshi.mom.iam.application.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminActor;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminCommands;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminRequestContext;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamClientPolicyAdminPort;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** IAM OAuth Client Policy 管理用例。 */
public class IamClientAdminApplicationService {
    private final IamClientPolicyAdminPort clients;
    private final IamSessionRevocationService revocations;
    private final IamAdminAuditService audits;
    private final Clock clock;

    public IamClientAdminApplicationService(
            IamClientPolicyAdminPort clients,
            IamSessionRevocationService revocations,
            IamAdminAuditService audits,
            Clock clock) {
        this.clients = clients;
        this.revocations = revocations;
        this.audits = audits;
        this.clock = clock;
    }

    public List<IamAdminViews.ClientView> listClients(IamAdminActor actor) {
        actor.requirePermission("iam:client:read");
        return clients.listClients();
    }

    @Transactional
    public IamAdminViews.ClientView setClientStatus(
            IamAdminActor actor,
            String clientId,
            IamAdminCommands.ClientStatusChange command,
            IamAdminRequestContext request) {
        IamRecordStatus status = Objects.requireNonNull(command.status(), "status 不能为空");
        actor.requirePermission(
                status == IamRecordStatus.ENABLED ? "iam:client:enable" : "iam:client:disable");
        IamAdminViews.ClientView client = clients.lockClient(clientId)
                .orElseThrow(() -> new IamAdminExceptions.NotFound("Client Policy 不存在"));
        if (command.version() == null || command.version().longValue() != client.version()) {
            throw new IamAdminExceptions.StaleVersion("version 已过期，请重新读取后重试");
        }
        clients.updateClientStatus(
                clientId, status, client.version(), actor.userId(), clock.instant());
        int revoked = status == IamRecordStatus.DISABLED
                ? revocations.revokeClientSessions(
                        clientId, actor.userId(), "client_disabled")
                : 0;
        audits.record(
                actor, request, "iam.client.status-changed", SecurityEventCategory.CLIENT,
                PermissionRiskLevel.HIGH, "CLIENT", clientId, null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"),
                null,
                IamAdminCommandValidator.json(
                        "revokedSessions", Integer.toString(revoked)));
        return clients.lockClient(clientId).orElseThrow(
                () -> new IamAdminExceptions.NotFound("Client Policy 不存在"));
    }
}
