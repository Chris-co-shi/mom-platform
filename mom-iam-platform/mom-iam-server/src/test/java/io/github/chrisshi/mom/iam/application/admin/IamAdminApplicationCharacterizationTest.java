package io.github.chrisshi.mom.iam.application.admin;

import io.github.chrisshi.mom.iam.application.admin.model.*;
import io.github.chrisshi.mom.iam.application.admin.port.*;
import io.github.chrisshi.mom.iam.domain.authorization.IamPartyBinding;
import io.github.chrisshi.mom.iam.domain.policy.PlatformAdministratorRetentionPolicy;
import io.github.chrisshi.mom.iam.domain.role.IamRole;
import io.github.chrisshi.mom.iam.domain.role.IamRoleRepository;
import io.github.chrisshi.mom.iam.domain.type.*;
import io.github.chrisshi.mom.iam.domain.user.IamUserAccount;
import io.github.chrisshi.mom.iam.domain.user.IamUserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** IAM Admin 安全不变量、版本、撤销和审计顺序特征测试。 */
@ExtendWith(MockitoExtension.class)
class IamAdminApplicationCharacterizationTest {
    private static final Instant NOW = Instant.parse("2026-07-29T06:00:00Z");

    @Mock IamUserAccountRepository users;
    @Mock IamUserAdminQueryPort userQueries;
    @Mock IamRoleRepository roles;
    @Mock IamRoleAdminQueryPort roleQueries;
    @Mock IamPermissionAdminQueryPort permissionQueries;
    @Mock IamAuthorizationAssignmentPort assignments;
    @Mock IamUserAccessPort access;
    @Mock IamAuthorizationReadPort readModels;
    @Mock IamSessionAdminQueryPort sessionQueries;
    @Mock IamSessionRevocationPort sessionRevocations;
    @Mock IamSecurityAuditSink auditSink;
    @Mock IamPlatformAdministratorPort administrators;
    @Mock IamExternalFactoryScopeVerifier externalFactoryVerifier;
    @Mock IamIdentifierGenerator ids;
    @Mock IamPasswordHasher passwordHasher;

    private IamUserAdminApplicationService userService;
    private IamUserAuthorizationApplicationService authorizationService;
    private IamRoleAdminApplicationService roleService;
    private IamAdminActor actor;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        IamAdminAuditService audits = new IamAdminAuditService(auditSink, ids, clock);
        IamSessionRevocationService revocations =
                new IamSessionRevocationService(sessionQueries, sessionRevocations);
        IamPlatformAdministratorGuard guard = new IamPlatformAdministratorGuard(
                administrators, new PlatformAdministratorRetentionPolicy(), clock);
        userService = new IamUserAdminApplicationService(
                users, userQueries, access, passwordHasher, ids, audits,
                revocations, guard, clock);
        authorizationService = new IamUserAuthorizationApplicationService(
                users, roles, assignments, access, readModels,
                externalFactoryVerifier, ids, audits, revocations, guard, clock);
        roleService = new IamRoleAdminApplicationService(
                roles, roleQueries, permissionQueries, assignments,
                readModels, ids, audits, clock);
        actor = new IamAdminActor(
                "100", "900", "mom-admin-web",
                Set.of(
                        "iam:user:disable", "iam:user:factory-scope-assign",
                        "iam:user:mobile-access-manage", "iam:user:role-assign",
                        "iam:role:update"));
    }

    @Test
    void disablingSelfMustFailBeforeAnyWrite() {
        when(users.lockById("100")).thenReturn(Optional.of(user(
                "100", UserType.INTERNAL, 3L)));

        assertThatThrownBy(() -> userService.setUserStatus(
                actor, "100",
                new IamAdminCommands.StatusChange(
                        IamRecordStatus.DISABLED, 3L, "security"),
                request()))
                .isInstanceOf(IamAdminExceptions.Conflict.class)
                .hasMessage("不能禁用当前登录账号");

        verify(users, never()).updateStatus(anyString(), any(), anyLong(), anyString(), any());
        verify(sessionRevocations, never()).revoke(anyString(), anyString(), anyString());
        verify(auditSink, never()).append(any());
    }

    @Test
    void lastPlatformAdministratorMustRemainProtected() {
        when(users.lockById("200")).thenReturn(Optional.of(user(
                "200", UserType.INTERNAL, 4L)));
        when(administrators.lockPlatformAdminRole())
                .thenReturn(Optional.of(platformAdminRole()));
        when(administrators.isEffectivePlatformAdmin("200", NOW)).thenReturn(true);
        when(administrators.countEffectivePlatformAdministrators(NOW)).thenReturn(1);

        assertThatThrownBy(() -> userService.setUserStatus(
                actor, "200",
                new IamAdminCommands.StatusChange(
                        IamRecordStatus.DISABLED, 4L, "security"),
                request())
                .isInstanceOf(IamAdminExceptions.Conflict.class)
                .hasMessage("件色不存在");

        verify(users, never()).updateStatus(anyString(), any(), anyLong(), anyString(), any());
        verify(sessionRevocations, never()).revoke(anyString(), anyString(), anyString());
        verify(auditSink, never()).append(any());
    }

    @Test
    void externalFactoryVerificationFailureMustFailClosedBeforeMutation() {
        when(users.lockById("200")).thenReturn(Optional.of(user(
                "200", UserType.SUPPLIER, 2L)));
        when(access.partyBinding("200")).thenReturn(Optional.of(
                new IamPartyBinding(
                        "1", PartyType.SUPPLIER, "200",
                        IamRecordStatus.ENABLED, 0L)));
        when(externalFactoryVerifier.isAllowed(
                PartyType.SUPPLIER, "200", Set.of("300")))
                .thenThrow(new IllegalStateException("upstream host detail"));

        assertThatThrownBy(() -> authorizationService.replaceFactoryScopes(
                actor, "200",
                new IamAdminCommands.FactoryScopeChange(
                        Set.of("300"), 2L, "scope"),
                request()))
                .isInstanceOf(IamAdminExceptions.DependencyUnavailable.class)
                .hasMessage("外部 Party 与 Factory 关系校验不可用")
                .hasMessageNotContaining("host");

        verify(access, never()).replaceFactoryScopes(
                anyString(), any(), anyString(), any(), any());
        verify(assignments, never()).advanceUserVersion(
                anyString(), anyLong(), anyString(), any());
        verify(auditSink, never()).append(any());
    }

    @Test
    void disablingMobileAccessMustKeepRevocationVersionAndAuditOrder() {
        when(users.lockById("200")).thenReturn(Optional.of(user(
                "200", UserType.INTERNAL, 5L)));
        when(access.partyBinding("200")).thenReturn(Optional.empty());
        when(sessionQueries.activeSessionIdsForUser("200"))
                .thenReturn(List.of("901", "902"));
        when(ids.nextId()).thenReturn("800");
        when(readModels.userAuthorization("200")).thenReturn(Optional.of(
                new IamAdminViews.UserAuthorizationView(
                        "200", 6L, Set.of(), Set.of(), false, null)));

        authorizationService.setMobileAccess(
                actor, "200",
                new IamAdminCommands.MobileAccessChange(
                        false, 5L, "lost device"),
                request());

        InOrder order = inOrder(
                access, sessionRevocations, assignments, auditSink, readModels);
        order.verify(access).setMobileAccess(
                eq("200"), eq(false), eq("100"), eq(NOW), any());
        order.verify(sessionRevocations).revoke(
                "901", "100", "mobile_access_disabled");
        order.verify(sessionRevocations).revoke(
                "902", "100", "mobile_access_disabled");
        order.verify(assignments).advanceUserVersion("200", 5L, "100", NOW);
        order.verify(auditSink).append(any(IamSecurityAuditEvent.class));
        order.verify(readModels).userAuthorization("200");
    }

    @Test
    void staleRoleAssignmentMustFailBeforeRelationWork() {
        when(users.lockById("200")).thenReturn(Optional.of(user(
                "200", UserType.INTERNAL, 8L)));
        assertThatThrownBy(() -> authorizationService.replaceUserRoles(
                actor, "200",
                new IamAdminCommands.RoleAssignment(
                        Set.of("400"), 7L, "role change"),
                request())
                .isInstanceOf(IamAdminExceptions.StaleVersion.class);

        verify(roles, never()).findByIds(any());
        verify(assignments, never()).replaceUserRoles(
                anyString(), any(), anyString(), any(), any());
        verify(auditSink, never()).append(any());
    }

    @Test
    void builtInRoleMustRemainReadOnly() {
        when(roles.lockById("400")).thenReturn(Optional.of(platformAdminRole()));

        assertThatThrownBy(() -> roleService.updateRole(
                actor, "400",
                new IamAdminCommands.UpdateRole(
                        "Platform Admin", null,
                        IamRecordStatus.ENABLED, 0L, "rename"),
                request()))
                .isInstanceOf(IamAdminExceptions.Conflict.class)
                .hasMessage("内置角色在 P1.5 管理 API 中只读");

        verify(roles, never()).update(
                anyString(), anyString(), any(), any(), anyLong(), anyString(), any());
        verify(auditSink, never()).append(any());
    }

    private static IamUserAccount user(String id, UserType type, long version) {
        return new IamUserAccount(
                id, "user", "User", type, IamRecordStatus.ENABLED,
                0, null, false, false, null, version);
    }

    private static IamRole platformAdminRole() {
        return new IamRole(
                "400", "PLATFORM_ADMIN", "Platform Admin",
                UserType.INTERNAL, IamRecordStatus.ENABLED,
                true, null, 0L);
    }

    private static IamAdminRequestContext request() {
        return new IamAdminRequestContext("127.0.0.1", "JUnit");
    }
}
