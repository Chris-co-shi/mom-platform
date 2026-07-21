package io.github.chrisshi.mom.iam.domain.model;

import io.github.chrisshi.mom.iam.domain.type.ApplicationCode;
import io.github.chrisshi.mom.iam.domain.type.ClientChannel;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** IAM 跨表领域约束单元测试。 */
class IamDomainRulesTest {

    @Test
    void externalBindingMustMatchExternalUserType() {
        IamDomainRules.requireExternalBinding(UserType.SUPPLIER, PartyType.SUPPLIER);
        IamDomainRules.requireExternalBinding(UserType.CUSTOMER, PartyType.CUSTOMER);
        assertThrows(IllegalArgumentException.class,
                () -> IamDomainRules.requireExternalBinding(UserType.INTERNAL, PartyType.SUPPLIER));
        assertThrows(IllegalArgumentException.class,
                () -> IamDomainRules.requireExternalBinding(UserType.SUPPLIER, PartyType.CUSTOMER));
    }

    @Test
    void internalProfileMustBelongToInternalUser() {
        IamDomainRules.requireInternalProfile(UserType.INTERNAL);
        assertThrows(IllegalArgumentException.class,
                () -> IamDomainRules.requireInternalProfile(UserType.SUPPLIER));
    }

    @Test
    void roleAssignmentMustUseSameUserType() {
        IamDomainRules.requireRoleAssignment(UserType.INTERNAL, UserType.INTERNAL);
        assertThrows(IllegalArgumentException.class,
                () -> IamDomainRules.requireRoleAssignment(UserType.INTERNAL, UserType.CUSTOMER));
    }

    @Test
    void mobileAccessMustBelongToInternalUser() {
        IamDomainRules.requireApplicationAccess(UserType.INTERNAL, ApplicationCode.MOM_MOBILE_PDA);
        assertThrows(IllegalArgumentException.class,
                () -> IamDomainRules.requireApplicationAccess(
                        UserType.SUPPLIER, ApplicationCode.MOM_MOBILE_PDA));
        assertThrows(IllegalArgumentException.class,
                () -> IamDomainRules.requireApplicationAccess(
                        UserType.INTERNAL, ApplicationCode.MOM_ADMIN));
    }

    @Test
    void validityAndPermissionCodeMustBeStrict() {
        Instant from = Instant.parse("2026-07-21T00:00:00Z");
        IamDomainRules.requireValidPeriod(from, from.plusSeconds(1));
        assertThrows(IllegalArgumentException.class,
                () -> IamDomainRules.requireValidPeriod(from, from));
        assertEquals("iam:user:read", IamDomainRules.requirePermissionCode(" iam:user:read "));
        assertThrows(IllegalArgumentException.class,
                () -> IamDomainRules.requirePermissionCode("IAM_USER_READ"));
    }

    @Test
    void clientPolicyAndSessionChannelMustMatchFrozenMatrix() {
        IamDomainRules.requireClientPolicy(
                "mom-admin-web", ApplicationCode.MOM_ADMIN,
                ClientChannel.WEB, UserType.INTERNAL, false);
        IamDomainRules.requireClientPolicy(
                "mom-mobile-pda", ApplicationCode.MOM_MOBILE_PDA,
                ClientChannel.MOBILE, UserType.INTERNAL, true);
        assertThrows(IllegalArgumentException.class, () -> IamDomainRules.requireClientPolicy(
                "mom-mobile-pda", ApplicationCode.MOM_MOBILE_PDA,
                ClientChannel.WEB, UserType.INTERNAL, true));
        assertThrows(IllegalArgumentException.class, () -> IamDomainRules.requireClientPolicy(
                "mom-customer-web", ApplicationCode.MOM_CUSTOMER_PORTAL,
                ClientChannel.WEB, UserType.INTERNAL, false));
        IamDomainRules.requireSessionChannel(ClientChannel.WEB, ClientChannel.WEB);
        assertThrows(IllegalArgumentException.class,
                () -> IamDomainRules.requireSessionChannel(ClientChannel.WEB, ClientChannel.MOBILE));
    }

    @Test
    void securityAuditPayloadMustRejectCredentialsAndTokens() {
        IamDomainRules.requireSafeAuditPayload("账号已禁用", "{\"fields\":[\"status\"]}");
        assertThrows(IllegalArgumentException.class,
                () -> IamDomainRules.requireSafeAuditPayload("refresh_token=secret", "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> IamDomainRules.requireSafeAuditPayload(
                        "安全变更", "{\"password_hash\":\"secret\"}"));
    }

    @Test
    void businessCodeMustBeStableAndMeaningful() {
        assertEquals("PLATFORM_ADMIN",
                IamDomainRules.requireBusinessCode(" PLATFORM_ADMIN ", "roleCode"));
        assertThrows(IllegalArgumentException.class,
                () -> IamDomainRules.requireBusinessCode("DEFAULT", "roleCode"));
        assertThrows(IllegalArgumentException.class,
                () -> IamDomainRules.requireBusinessCode("platform-admin", "roleCode"));
    }
}
