package io.github.chrisshi.mom.gateway.security;

import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** S06 四个 Public Client 与 Gateway 入口路由矩阵测试。 */
class MomGatewayClientRoutePolicyTest {
    private final MomGatewayClientRoutePolicy policy = new MomGatewayClientRoutePolicy();

    @Test
    void adminAndMobileMayEnterInternalRoutesOnlyAsInternalUsers() {
        assertTrue(policy.isAllowed("/api/integration/probe", authorization("mom-admin-web", "INTERNAL")));
        assertTrue(policy.isAllowed("/api/wms/orders", authorization("mom-mobile-pda", "INTERNAL")));
        assertFalse(policy.isAllowed("/api/integration/probe", authorization("mom-supplier-web", "SUPPLIER")));
        assertFalse(policy.isAllowed("/api/customer/orders", authorization("mom-admin-web", "INTERNAL")));
    }

    @Test
    void externalClientsMayOnlyEnterTheirOwnPortalRoutes() {
        assertTrue(policy.isAllowed("/api/supplier/orders", authorization("mom-supplier-web", "SUPPLIER")));
        assertTrue(policy.isAllowed("/api/customer/orders", authorization("mom-customer-web", "CUSTOMER")));
        assertFalse(policy.isAllowed("/api/customer/orders", authorization("mom-supplier-web", "SUPPLIER")));
        assertFalse(policy.isAllowed("/api/supplier/orders", authorization("mom-customer-web", "CUSTOMER")));
    }

    @Test
    void iamMeUsesClientUserMatrixAndAdminApiIsAdminOnly() {
        assertTrue(policy.isAllowed("/api/iam/me", authorization("mom-admin-web", "INTERNAL")));
        assertTrue(policy.isAllowed("/api/iam/me", authorization("mom-supplier-web", "SUPPLIER")));
        assertTrue(policy.isAllowed("/api/iam/me", authorization("mom-customer-web", "CUSTOMER")));
        assertTrue(policy.isAllowed("/api/iam/me", authorization("mom-mobile-pda", "INTERNAL")));
        assertFalse(policy.isAllowed("/api/iam/me", authorization("mom-admin-web", "CUSTOMER")));
        assertTrue(policy.isAllowed("/api/iam/admin/users", authorization("mom-admin-web", "INTERNAL")));
        assertFalse(policy.isAllowed("/api/iam/admin/users", authorization("mom-mobile-pda", "INTERNAL")));
    }

    @Test
    void unknownApiRoutesFailClosed() {
        assertFalse(policy.isAllowed("/api/unknown/resource", authorization("mom-admin-web", "INTERNAL")));
    }

    private static MomJwtAuthorization authorization(String clientId, String userType) {
        String partyType = "INTERNAL".equals(userType) ? null : userType;
        String partyId = "INTERNAL".equals(userType) ? null : "party-1";
        return new MomJwtAuthorization(
                "user-1",
                "session-1",
                clientId,
                userType,
                Set.of(),
                Set.of(),
                Set.of("factory-1"),
                partyType,
                partyId);
    }
}
