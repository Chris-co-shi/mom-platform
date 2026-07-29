package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamSecurityAuditEventEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamBuiltInAdministratorRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSecurityAuditEventAppender;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamAuthorizationAssignmentRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamClientPolicyAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamRoleAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamSecurityAuditQueryRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamSessionAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamUserAccessAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamUserAdminRepository;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import io.github.chrisshi.mom.security.authorization.MomAuthorizationService;
import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IAM Admin 安全不变量、版本、撤销和审计顺序特征测试。
 *
 * <p>该测试在 Repository 与安全端口边界观察当前调用顺序，不伪装 PostgreSQL
 * 行锁验收。S09 拆分 Application Service 后，必须继续通过同一 Facade 产生等价
 * 安全结果与副作用顺序。</p>
 */
@ExtendWith(MockitoExtension.class)
class IamAdminServiceCharacterizationTest {
    private static final Instant NOW = Instant.parse("2026-07-29T06:00:00Z");

    @Mock IamUserAdminRepository users;
    @Mock IamRoleAdminRepository roles;
    @Mock IamAuthorizationAssignmentRepository assignments;
    @Mock IamUserAccessAdminRepository access;
    @Mock IamSessionAdminRepository sessionQueries;
    @Mock IamClientPolicyAdminRepository clients;
    @Mock IamSecurityAuditQueryRepository auditQueries;
    @Mock IamAdminReadModelRepository readModels;
    @Mock IamBuiltInAdministratorRepository builtInAdministrators;
    @Mock MomAuthorizationService authorization;
    @Mock PasswordEncoder passwordEncoder;
    @Mock IamSessionTokenService sessions;
    @Mock IamSecurityAuditEventAppender auditEvents;
    @Mock IamExternalFactoryScopeVerifier externalFactoryVerifier;
    @Mock IamSecureIdGenerator ids;
    @Mock Authentication authentication;

    private IamAdminService service;
    private MomJwtAuthorization actor;

    @BeforeEach
    void setUp() {
        actor = new MomJwtAuthorization(
                "100", "900", "mom-admin-web", "INTERNAL",
                Set.of("MOM_ADMIN"), Set.of("iam:user:disable"), Set.of(), null, null);
        service = new IamAdminService(
                users, roles, assignments, access, sessionQueries, clients, auditQueries,
                readModels, builtInAdministrators, authorization, passwordEncoder, sessions,
                auditEvents, externalFactoryVerifier, ids, Clock.fixed(NOW, ZoneOffset.UTC));
        when(authorization.current(authentication)).thenReturn(actor);
    }

    /** 禁用自己必须在状态更新、Session 撤销和成功审计之前失败。 */
    @Test
    void disablingSelfMustFailBeforeAnyWrite() {
        when(users.lockUser("100")).thenReturn(Optional.of(user("100", UserType.INTERNAL, 3L)));

        assertThatThrownBy(() -> service.setUserStatus(
                authentication, "100",
                new IamAdminService.StatusChange(IamRecordStatus.DISABLED, 3L, "security"),
                request()))
                .isInstanceOf(IamAdminExceptions.Conflict.class)
                .hasMessage("不能禁用当前登录账号");

        verify(users, never()).updateUserStatus(anyString(), any(), any(Long.class), anyString(), any());
        verify(sessions, never()).revoke(anyString(), anyString(), anyString());
        verify(auditEvents, never()).append(any());
    }

    /** 最后一名有效 PLATFORM_ADMIN 必须在持有共享角色锁时 Fail Closed。 */
    @Test
    void lastPlatformAdministratorMustRemainProtected() {
        when(users.lockUser("200")).thenReturn(Optional.of(user("200", UserType.INTERNAL, 4L)));
        when(builtInAdministrators.lockPlatformAdminRole())
                .thenReturn(Optional.of(platformAdminRole()));
        when(builtInAdministrators.isEffectivePlatformAdmin("200", NOW)).thenReturn(true);
        when(builtInAdministrators.countEffectivePlatformAdministrators(NOW)).thenReturn(1);

        assertThatThrownBy(() -> service.setUserStatus(
                authentication, "200",
                new IamAdminService.StatusChange(IamRecordStatus.DISABLED, 4L, "security"),
                request()))
                .isInstanceOf(IamAdminExceptions.Conflict.class)
                .hasMessage("系统必须至少保留一个有效 PLATFORM_ADMIN");

        verify(users, never()).updateUserStatus(anyString(), any(), any(Long.class), anyString(), any());
        verify(auditEvents, never()).append(any());
    }

    /** 外部 Factory 权威校验不可用时，关系、版本与成功审计均不得推进。 */
    @Test
    void externalFactoryVerificationFailureMustFailClosedBeforeMutation() {
        when(users.lockUser("200")).thenReturn(Optional.of(user("200", UserType.SUPPLIER, 2L)));
        when(access.partyBinding("200")).thenReturn(Optional.of(
                new IamAdminViews.PartyBindingView(
                        "binding-1", PartyType.SUPPLIER, "200", IamRecordStatus.ENABLED, 0L)));
        when(externalFactoryVerifier.isAllowed(PartyType.SUPPLIER, "200", Set.of("300")))
                .thenThrow(new IllegalStateException("upstream host detail"));

        assertThatThrownBy(() -> service.replaceFactoryScopes(
                authentication, "200",
                new IamAdminService.FactoryScopeChange(Set.of("300"), 2L, "scope"),
                request()))
                .isInstanceOf(IamAdminExceptions.DependencyUnavailable.class)
                .hasMessage("外部 Party 与 Factory 关系校验不可用")
                .hasMessageNotContaining("host");

        verify(access, never()).replaceFactoryScopes(anyString(), any(), anyString(), any(), any());
        verify(assignments, never()).advanceUserVersion(anyString(), any(Long.class), anyString(), any());
        verify(auditEvents, never()).append(any());
    }

    /** 禁用 Mobile Access 必须保持关系更新、Session 撤销、版本推进、成功审计顺序。 */
    @Test
    void disablingMobileAccessMustKeepRevocationVersionAndAuditOrder() {
        when(users.lockUser("200")).thenReturn(Optional.of(user("200", UserType.INTERNAL, 5L)));
        when(sessionQueries.activeSessionIdsForUser("200"))
                .thenReturn(List.of("901", "902"));
        when(ids.nextId()).thenReturn("800");
        when(readModels.userAuthorization("200")).thenReturn(
                new IamAdminViews.UserAuthorizationView(
                        "200", 6L, Set.of(), Set.of(), false, null));

        IamAdminViews.UserAuthorizationView result = service.setMobileAccess(
                authentication, "200",
                new IamAdminService.MobileAccessChange(false, 5L, "lost device"), request());

        InOrder order = inOrder(access, sessions, assignments, auditEvents, readModels);
        order.verify(access).setMobileAccess(
                org.mockito.ArgumentMatchers.eq("200"),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq("100"),
                org.mockito.ArgumentMatchers.eq(NOW), any());
        order.verify(sessions).revoke("901", "100", "mobile_access_disabled");
        order.verify(sessions).revoke("902", "100", "mobile_access_disabled");
        order.verify(assignments).advanceUserVersion("200", 5L, "100", NOW);
        ArgumentCaptor<IamSecurityAuditEventEntity> event =
                ArgumentCaptor.forClass(IamSecurityAuditEventEntity.class);
        order.verify(auditEvents).append(event.capture());
        order.verify(readModels).userAuthorization("200");
        assertThat(result.userVersion()).isEqualTo(6L);
        assertThat(event.getValue().getEventType()).isEqualTo("iam.user.mobile-access-changed");
        assertThat(event.getValue().getReasonCode()).isEqualTo("lost device");
        assertThat(event.getValue().getChangeSummary()).isEqualTo("{\"enabled\":\"false\"}");
    }

    /** 过期聚合版本必须在关系查询、替换和审计前失败。 */
    @Test
    void staleRoleAssignmentMustFailBeforeRelationWork() {
        when(users.lockUser("200")).thenReturn(Optional.of(user("200", UserType.INTERNAL, 8L)));

        assertThatThrownBy(() -> service.replaceUserRoles(
                authentication, "200",
                new IamAdminService.RoleAssignment(Set.of("400"), 7L, "role change"),
                request()))
                .isInstanceOf(IamAdminExceptions.StaleVersion.class);

        verify(roles, never()).findRoles(any());
        verify(assignments, never()).replaceUserRoles(anyString(), any(), anyString(), any(), any());
        verify(auditEvents, never()).append(any());
    }

    /** 内置角色更新必须在 Repository 更新和成功审计之前被拒绝。 */
    @Test
    void builtInRoleMustRemainReadOnly() {
        when(roles.lockRole("400")).thenReturn(Optional.of(platformAdminRole()));

        assertThatThrownBy(() -> service.updateRole(
                authentication, "400",
                new IamAdminService.UpdateRole(
                        "Platform Admin", null, IamRecordStatus.ENABLED, 0L, "rename"),
                request()))
                .isInstanceOf(IamAdminExceptions.Conflict.class)
                .hasMessage("内置角色在 P1.5 管理 API 中只读");

        verify(roles, never()).updateRole(anyString(), anyString(), any(), any(), any(Long.class), anyString(), any());
        verify(auditEvents, never()).append(any());
    }

    private static IamAdminViews.UserView user(String id, UserType type, long version) {
        return new IamAdminViews.UserView(
                id, "user", "User", type, IamRecordStatus.ENABLED,
                0, null, false, false, null, version);
    }

    private static IamAdminViews.RoleView platformAdminRole() {
        return new IamAdminViews.RoleView(
                "400", "PLATFORM_ADMIN", "Platform Admin", UserType.INTERNAL,
                IamRecordStatus.ENABLED, true, null, 0L);
    }

    private static IamAdminService.RequestContext request() {
        return new IamAdminService.RequestContext("127.0.0.1", "JUnit");
    }
}
