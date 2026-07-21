package io.github.chrisshi.mom.iam.domain.model;

import io.github.chrisshi.mom.iam.domain.type.ApplicationCode;
import io.github.chrisshi.mom.iam.domain.type.ClientChannel;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * S02 数据库无法独立表达的 IAM 跨表领域约束。
 *
 * <p>该类型不访问数据库、不计算最终权限，也不实现登录或 Token 行为。Application Service 与后续管理 API
 * 在写入前复用这些纯函数，数据库继续负责唯一性、外键、枚举和时间区间等结构约束。</p>
 */
public final class IamDomainRules {

    private static final Pattern BUSINESS_CODE =
            Pattern.compile("[A-Z][A-Z0-9_]{2,99}");
    private static final Pattern PERMISSION_CODE =
            Pattern.compile("[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[a-z][a-z0-9-]*");
    private static final Pattern SENSITIVE_AUDIT_CONTENT = Pattern.compile(
            "(?i)(password(_hash)?|access[_ -]?token|refresh[_ -]?token|token[_ -]?digest|"
                    + "authorization[_ -]?code|code[_ -]?verifier|authorization\\s*:\\s*bearer|"
                    + "private[_ -]?key)");
    private static final Map<String, ClientPolicyRule> CLIENT_POLICIES = Map.of(
            "mom-admin-web", new ClientPolicyRule(
                    ApplicationCode.MOM_ADMIN, ClientChannel.WEB, UserType.INTERNAL, false),
            "mom-supplier-web", new ClientPolicyRule(
                    ApplicationCode.MOM_SUPPLIER_PORTAL, ClientChannel.WEB, UserType.SUPPLIER, false),
            "mom-customer-web", new ClientPolicyRule(
                    ApplicationCode.MOM_CUSTOMER_PORTAL, ClientChannel.WEB, UserType.CUSTOMER, false),
            "mom-mobile-pda", new ClientPolicyRule(
                    ApplicationCode.MOM_MOBILE_PDA, ClientChannel.MOBILE, UserType.INTERNAL, true));

    private IamDomainRules() {
    }

    /**
     * 校验外部用户与 Party 类型严格一致。
     *
     * @param userType 用户类型
     * @param partyType 外部主体类型
     */
    public static void requireExternalBinding(UserType userType, PartyType partyType) {
        Objects.requireNonNull(userType, "userType 不能为空");
        Objects.requireNonNull(partyType, "partyType 不能为空");
        if (userType == UserType.INTERNAL || !userType.name().equals(partyType.name())) {
            throw new IllegalArgumentException("只有同类型 SUPPLIER/CUSTOMER 用户可以建立 External Binding");
        }
    }

    /**
     * 校验只有内部用户可以拥有内部资料。
     *
     * @param userType 用户类型
     */
    public static void requireInternalProfile(UserType userType) {
        if (Objects.requireNonNull(userType, "userType 不能为空") != UserType.INTERNAL) {
            throw new IllegalArgumentException("只有 INTERNAL 用户可以拥有内部资料");
        }
    }

    /**
     * 校验用户类型与角色适用类型一致。
     *
     * @param userType 用户类型
     * @param applicableUserType 角色适用类型
     */
    public static void requireRoleAssignment(UserType userType, UserType applicableUserType) {
        if (Objects.requireNonNull(userType, "userType 不能为空")
                != Objects.requireNonNull(applicableUserType, "applicableUserType 不能为空")) {
            throw new IllegalArgumentException("用户类型必须匹配角色 applicable_user_type");
        }
    }

    /**
     * 校验 Mobile Access 只授予内部用户。
     *
     * @param userType 用户类型
     * @param applicationCode 应用编码
     */
    public static void requireApplicationAccess(UserType userType, ApplicationCode applicationCode) {
        Objects.requireNonNull(userType, "userType 不能为空");
        Objects.requireNonNull(applicationCode, "applicationCode 不能为空");
        if (applicationCode != ApplicationCode.MOM_MOBILE_PDA || userType != UserType.INTERNAL) {
            throw new IllegalArgumentException(
                    "P1.5 用户级 Application Access 只允许 INTERNAL 用户的 MOM_MOBILE_PDA");
        }
    }

    /**
     * 校验可选有效期结束严格晚于开始。
     *
     * @param validFrom 生效时间，可为空
     * @param validUntil 失效时间，可为空
     */
    public static void requireValidPeriod(Instant validFrom, Instant validUntil) {
        if (validFrom != null && validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("validUntil 必须晚于 validFrom");
        }
    }

    /**
     * 校验 Permission Code 的 domain:resource:action 格式。
     *
     * @param code Permission Code
     * @return 去除首尾空白后的编码
     */
    public static String requirePermissionCode(String code) {
        String normalized = requireText(code, "permissionCode");
        if (!PERMISSION_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Permission Code 必须符合 domain:resource:action");
        }
        return normalized;
    }

    /**
     * 校验业务编码非空、有意义并符合大写下划线规范。
     *
     * @param code 业务编码
     * @param fieldName 字段名称
     * @return 规范化编码
     */
    public static String requireBusinessCode(String code, String fieldName) {
        String normalized = requireText(code, fieldName);
        if (!BUSINESS_CODE.matcher(normalized).matches()
                || "DEFAULT".equals(normalized)
                || "UNKNOWN".equals(normalized)) {
            throw new IllegalArgumentException(fieldName + " 必须是稳定、有意义的大写下划线编码");
        }
        return normalized;
    }

    /** 校验 Client Policy 的渠道、用户类型与 Mobile Access 矩阵。 */
    public static void requireClientPolicy(
            String clientId,
            ApplicationCode applicationCode,
            ClientChannel channel,
            UserType allowedUserType,
            boolean mobileAccessRequired) {
        String normalizedClientId = requireText(clientId, "clientId");
        ClientPolicyRule expected = CLIENT_POLICIES.get(normalizedClientId);
        ClientPolicyRule actual = new ClientPolicyRule(
                Objects.requireNonNull(applicationCode, "applicationCode 不能为空"),
                Objects.requireNonNull(channel, "channel 不能为空"),
                Objects.requireNonNull(allowedUserType, "allowedUserType 不能为空"),
                mobileAccessRequired);
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException("Client Policy 与冻结的四应用访问矩阵不一致");
        }
    }

    /** 校验 Session 渠道与 Client Policy 渠道一致。 */
    public static void requireSessionChannel(ClientChannel sessionChannel, ClientChannel policyChannel) {
        if (Objects.requireNonNull(sessionChannel, "sessionChannel 不能为空")
                != Objects.requireNonNull(policyChannel, "policyChannel 不能为空")) {
            throw new IllegalArgumentException("Session channel 必须与 Client Policy channel 一致");
        }
    }

    /**
     * 校验安全审计摘要不包含凭证或密钥类敏感内容。
     *
     * @param reasonDetail 受控原因说明，可为空
     * @param changeSummary JSONB 变更摘要文本，可为空
     */
    public static void requireSafeAuditPayload(String reasonDetail, String changeSummary) {
        rejectSensitiveAuditText(reasonDetail, "reasonDetail");
        rejectSensitiveAuditText(changeSummary, "changeSummary");
    }

    private static void rejectSensitiveAuditText(String value, String fieldName) {
        if (value != null && SENSITIVE_AUDIT_CONTENT.matcher(value).find()) {
            throw new IllegalArgumentException(fieldName + " 禁止包含密码、Token、授权码或私钥类敏感内容");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }

    /** 冻结 Client Policy 的不可变对照值。 */
    private record ClientPolicyRule(
            ApplicationCode applicationCode,
            ClientChannel channel,
            UserType userType,
            boolean mobileAccessRequired) {
    }
}
