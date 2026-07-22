package io.github.chrisshi.mom.security.token;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** MOM Access Token 的 Issuer、Audience、时间和业务 Claim 验证器工厂。 */
public final class MomJwtValidators {
    private static final String INVALID_TOKEN = "invalid_token";
    private static final Set<String> USER_TYPES = Set.of(
            MomSecurityClaims.USER_TYPE_INTERNAL,
            MomSecurityClaims.USER_TYPE_SUPPLIER,
            MomSecurityClaims.USER_TYPE_CUSTOMER);

    private MomJwtValidators() {
    }

    /** 组合 Spring 标准时间/Issuer 验证与 MOM 业务协议验证。 */
    public static OAuth2TokenValidator<Jwt> create(String issuer, Collection<String> acceptedAudiences) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer 不能为空");
        }
        Set<String> audiences = normalized(acceptedAudiences);
        if (audiences.isEmpty()) {
            throw new IllegalArgumentException("JWT accepted audiences 不能为空");
        }
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AcceptedAudienceValidator(audiences),
                new ClientAudienceConsistencyValidator(),
                new RequiredClaimsValidator());
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                INVALID_TOKEN,
                description,
                null));
    }

    private static Set<String> normalized(Collection<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    result.add(value.trim());
                }
            }
        }
        return Set.copyOf(result);
    }

    /** 至少一个 aud 必须属于当前 Resource Server 接受的 Client/Audience 集合。 */
    static final class AcceptedAudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final Set<String> acceptedAudiences;

        AcceptedAudienceValidator(Set<String> acceptedAudiences) {
            this.acceptedAudiences = acceptedAudiences;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            List<String> audience = token.getAudience();
            if (audience != null && audience.stream().anyMatch(acceptedAudiences::contains)) {
                return OAuth2TokenValidatorResult.success();
            }
            return failure("JWT audience 不属于当前 MOM Resource Server");
        }
    }

    /** client_id 必须同时出现在 aud 中，防止 Client Claim 与标准 Audience 分离。 */
    static final class ClientAudienceConsistencyValidator implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            String clientId = MomSecurityClaims.stringClaim(token, MomSecurityClaims.CLIENT_ID);
            if (clientId != null && token.getAudience() != null && token.getAudience().contains(clientId)) {
                return OAuth2TokenValidatorResult.success();
            }
            return failure("JWT client_id 与 audience 不一致");
        }
    }

    /** 验证 S04/S05 冻结的业务 Claims 形态和外部主体一致性。 */
    static final class RequiredClaimsValidator implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            if (blank(token.getSubject()) || blank(token.getId())) {
                return failure("JWT 缺少 sub 或 jti");
            }
            String sessionId = MomSecurityClaims.stringClaim(token, MomSecurityClaims.SESSION_ID);
            String clientId = MomSecurityClaims.stringClaim(token, MomSecurityClaims.CLIENT_ID);
            String userType = MomSecurityClaims.stringClaim(token, MomSecurityClaims.USER_TYPE);
            if (blank(sessionId) || blank(clientId) || !USER_TYPES.contains(userType)) {
                return failure("JWT 缺少 sid/client_id 或 user_type 非法");
            }
            if (!MomSecurityClaims.isStringCollectionClaim(token, MomSecurityClaims.ROLES)
                    || !MomSecurityClaims.isStringCollectionClaim(token, MomSecurityClaims.PERMISSIONS)
                    || !MomSecurityClaims.isStringCollectionClaim(token, MomSecurityClaims.FACTORY_IDS)) {
                return failure("JWT roles/permissions/factory_ids 必须是字符串集合");
            }
            String partyType = MomSecurityClaims.stringClaim(token, MomSecurityClaims.PARTY_TYPE);
            String partyId = MomSecurityClaims.stringClaim(token, MomSecurityClaims.PARTY_ID);
            if (MomSecurityClaims.USER_TYPE_INTERNAL.equals(userType)) {
                if (partyType != null || partyId != null) {
                    return failure("INTERNAL Token 不得携带外部 Party");
                }
            }
            else {
                if (!userType.equals(partyType) || blank(partyId)) {
                    return failure("外部用户 Token 必须携带匹配的 party_type 与 party_id");
                }
            }
            return OAuth2TokenValidatorResult.success();
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }
}
