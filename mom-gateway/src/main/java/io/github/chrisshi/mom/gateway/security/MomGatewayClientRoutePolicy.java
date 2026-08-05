package io.github.chrisshi.mom.gateway.security;

import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import io.github.chrisshi.mom.security.token.MomSecurityClaims;

import java.util.Set;

/**
 * 四个 Public Client 的 Gateway 粗粒度入口隔离。
 *
 * <p>该策略属于 Gateway 安全边界，只依赖已经通过 JWT 校验的标准化授权上下文，不访问数据库、网络或
 * 下游服务。它只决定 Client 能否进入某类路由，不替代业务服务最终 Permission、Factory/Party 与对象
 * 归属授权。类型本身无可变状态，可被多个请求并发安全复用；未知路径、缺失身份和 Client/User Type
 * 错配一律 fail closed。</p>
 */
public final class MomGatewayClientRoutePolicy {
    private static final String ADMIN = "mom-admin-web";
    private static final String SUPPLIER = "mom-supplier-web";
    private static final String CUSTOMER = "mom-customer-web";
    private static final String MOBILE = "mom-mobile-pda";
    private static final Set<String> INTERNAL_CLIENTS = Set.of(ADMIN, MOBILE);

    /**
     * 判断已认证 Public Client 是否可以进入指定 Gateway 路径。
     *
     * @param path 请求路径；允许省略开头斜杠，null 会被拒绝
     * @param authorization 已由 Gateway JWT 链校验并转换的授权上下文
     * @return 仅在 Client、User Type 与入口类型匹配时返回 true
     * @throws RuntimeException 不主动抛出异常；未知或不完整输入直接返回 false
     *
     * <p>方法无外部副作用且幂等。System 管理入口只允许 Admin/Internal；System Runtime 入口允许四个
     * 已登记 Public Client，并继续由 System Resource Server 与方法级 Permission 执行最终授权。</p>
     */
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
        if (normalized.equals("/api/system/admin") || normalized.startsWith("/api/system/admin/")) {
            return ADMIN.equals(authorization.clientId())
                    && MomSecurityClaims.USER_TYPE_INTERNAL.equals(authorization.userType());
        }
        if (normalized.startsWith("/api/system/")) {
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
