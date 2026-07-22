package io.github.chrisshi.mom.security.token;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MOM Access Token 业务 Claims 的稳定名称与读取规则。
 *
 * <p>该类只定义协议，不信任 Header 注入，不从请求头构造身份。Gateway 和业务 Resource Server
 * 必须从已完成签名、Issuer、Audience、时间与必需 Claim 校验的 {@link Jwt} 读取这些值。</p>
 */
public final class MomSecurityClaims {
    public static final String SESSION_ID = "sid";
    public static final String CLIENT_ID = "client_id";
    public static final String USER_TYPE = "user_type";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";
    public static final String FACTORY_IDS = "factory_ids";
    public static final String PARTY_TYPE = "party_type";
    public static final String PARTY_ID = "party_id";

    public static final String USER_TYPE_INTERNAL = "INTERNAL";
    public static final String USER_TYPE_SUPPLIER = "SUPPLIER";
    public static final String USER_TYPE_CUSTOMER = "CUSTOMER";

    private MomSecurityClaims() {
    }

    /** 读取并规范化单值字符串 Claim。 */
    public static String stringClaim(Jwt jwt, String claimName) {
        Object value = jwt.getClaims().get(claimName);
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /** 读取并稳定去重字符串集合 Claim；非法类型返回空集合，由 Validator 负责拒绝。 */
    public static Set<String> stringSetClaim(Jwt jwt, String claimName) {
        Object value = jwt.getClaims().get(claimName);
        if (!(value instanceof Collection<?> collection)) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Object item : collection) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim();
            if (!text.isEmpty()) {
                normalized.add(text);
            }
        }
        return Set.copyOf(normalized);
    }

    /** 判断 Claim 是否是字符串集合，即使集合为空也视为协议字段存在。 */
    public static boolean isStringCollectionClaim(Jwt jwt, String claimName) {
        Object value = jwt.getClaims().get(claimName);
        if (!(value instanceof Collection<?> collection)) {
            return false;
        }
        return collection.stream().allMatch(item -> item instanceof String);
    }

    /** 四个冻结 Public Client。 */
    public static List<String> publicClientIds() {
        return List.of("mom-admin-web", "mom-supplier-web", "mom-customer-web", "mom-mobile-pda");
    }
}
