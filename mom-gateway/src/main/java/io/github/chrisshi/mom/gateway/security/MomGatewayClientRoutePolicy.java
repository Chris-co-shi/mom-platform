package io.github.chrisshi.mom.gateway.security;

import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import io.github.chrisshi.mom.security.token.MomSecurityClaims;

import java.util.Set;

/**
 * 四个 Public Client 的 Gateway 粗粒度入口隔离。
 *
 * <p>该策略只决定 Client 能否进入某类路由，不替代业务服务最终 Permission、Factory/Party 与对象归属授权。</p>
 */
public final class MomGatewayClientRoutePolicy {
    private static final String ADMIN = "mom-admin-web";
    private static final String SUPPLIER = "mom-supplier-web";
    private static final String CUSTOMER = "mom-customer-web";
    private static final String MOBILE = "mom-mobile-pda";
    private static final Set<String> INTERNAL_CLIENTS = Set.of(ADMIN, MOBILE);

    public boolean isAllowed(String path, MomJwtAuthorization authorization) {
        if (path == null || authorization == null) {
            return false;
        }
        String normalized = path.startsWith("/") ? path : "/" + path;

        if (normalized.startsWith("/api/iam/admin/")) {
            return ADMIN.equals(authorization.clientId())
                    && MomSecurityClaims.USER_TYPE_INTERNAL.equals(authorization.userType());
        }
        if (normalized.startsWith("/api/iam/")) {
            return clientMatchesUserType(authorization);
        }
        if (normalized.startsWith("/api/supplier/")) {
            return SUPPLIER.equals(authorization.clientId())
                    && MomSecurityClaims.USER_TYPE_SUPPLIER.equals(authorization.userType());
        }
        if (normalized.startsWith("/api/customer/")) {
            return CUSTOMER.equals(authorization.clientId())
                    && MomSecurityClaims.USER_TYPE_CUSTOMER.equals(authorization.userType());
        }
        if (isInternalBusinessRoute(normalized)) {
            return INTERNAL_CLIENTS.contains(authorization.clientId())
                    && MomSecurityClaims.USER_TYPE_INTERNAL.equals(authorization.userType());
        }
        return false;
    }

    private static boolean clientMatchesUserType(MomJwtAuthorization authorization) {
        return switch (authorization.clientId()) {
            case ADMIN, MOBILE -> MomSecurityClaims.USER_TYPE_INTERNAL.equals(authorization.userType());
            case SUPPLIER -> MomSecurityClaims.USER_TYPE_SUPPLIER.equals(authorization.userType());
            case CUSTOMER -> MomSecurityClaims.USER_TYPE_CUSTOMER.equals(authorization.userType());
            default -> false;
        };
    }

    private static boolean isInternalBusinessRoute(String path) {
        return path.startsWith("/api/integration/")
                || path.startsWith("/api/mdm/")
                || path.startsWith("/api/mes/")
                || path.startsWith("/api/wms/")
                || path.startsWith("/api/qms/")
                || path.startsWith("/api/ems/")
                || path.startsWith("/api/eam/")
                || path.startsWith("/api/traceability/");
    }
}
