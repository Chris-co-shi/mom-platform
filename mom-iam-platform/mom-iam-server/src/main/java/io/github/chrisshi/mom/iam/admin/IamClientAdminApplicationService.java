package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamClientPolicyAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamSessionAdminRepository;
import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * IAM OAuth Client Policy 管理应用服务。
 *
 * <p>该服务只读取和变更 MOM Client Policy，不修改 SAS Registered Client、Grant Type
 * 或 Redirect URI。禁用 Client 时仍在同一本地事务中按原顺序更新 Policy、同步撤销
 * Active Session 并追加审计；任一撤销失败向上传播。</p>
 */
public class IamClientAdminApplicationService {
    private final IamClientPolicyAdminRepository clients;
    private final IamSessionAdminRepository sessionQueries;
    private final IamAdminOperationSupport support;

    IamClientAdminApplicationService(
            IamClientPolicyAdminRepository clients,
            IamSessionAdminRepository sessionQueries,
            IamAdminOperationSupport support) {
        this.clients = clients;
        this.sessionQueries = sessionQueries;
        this.support = support;
    }

    /** 读取 MOM Client Policy 与 SAS 注册信息的非敏感联合投影。 */
    public List<IamAdminViews.ClientView> listClients(Authentication authentication) {
        support.requirePermission(authentication, "iam:client:read");
        return clients.listClients();
    }

    /** 按客户端版本变更 Policy；禁用时同步撤销对应 Active Session。 */
    @Transactional
    public IamAdminViews.ClientView setClientStatus(
            Authentication authentication, String clientId,
            IamAdminService.ClientStatusChange command, IamAdminService.RequestContext request) {
        IamRecordStatus status = Objects.requireNonNull(command.status(), "status 不能为空");
        MomJwtAuthorization actor = support.actor(authentication,
                status == IamRecordStatus.ENABLED ? "iam:client:enable" : "iam:client:disable");
        IamAdminViews.ClientView client = clients.lockClient(clientId)
                .orElseThrow(() -> new IamAdminExceptions.NotFound("Client Policy 不存在"));
        clients.updateClientStatus(clientId, status,
                IamAdminCommandValidator.requireVersion(command.version(), client.version()),
                actor.userId(), support.now());
        int revoked = 0;
        if (status == IamRecordStatus.DISABLED) {
            for (String sessionId : sessionQueries.activeSessionIdsForClient(clientId)) {
                support.revokeSession(sessionId, actor.userId(), "client_disabled");
                revoked++;
            }
        }
        support.audit(actor, request, "iam.client.status-changed", SecurityEventCategory.CLIENT,
                PermissionRiskLevel.HIGH, "CLIENT", clientId, null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"), null,
                IamAdminCommandValidator.json("revokedSessions", Integer.toString(revoked)));
        return clients.lockClient(clientId).orElseThrow();
    }
}
