package io.github.chrisshi.mom.iam.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** S05 用户授权 Session、Refresh HMAC 与 revoked sid 配置。 */
@ConfigurationProperties("mom.iam.session")
public class IamSessionProperties {
    private Duration webAbsoluteTtl = Duration.ofHours(8);
    private Duration mobileAbsoluteTtl = Duration.ofHours(12);
    private Duration accessTokenTtl = Duration.ofMinutes(10);
    private int refreshTokenBytes = 48;
    private String hmacPepper = "mom-iam-local-refresh-pepper-change-me";
    private String revokedKeyPrefix = "mom:iam:revoked:sid:";
    private boolean allowLocalPepper = true;

    public Duration getWebAbsoluteTtl() { return webAbsoluteTtl; }
    public void setWebAbsoluteTtl(Duration webAbsoluteTtl) { this.webAbsoluteTtl = webAbsoluteTtl; }
    public Duration getMobileAbsoluteTtl() { return mobileAbsoluteTtl; }
    public void setMobileAbsoluteTtl(Duration mobileAbsoluteTtl) { this.mobileAbsoluteTtl = mobileAbsoluteTtl; }
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
    public int getRefreshTokenBytes() { return refreshTokenBytes; }
    public void setRefreshTokenBytes(int refreshTokenBytes) { this.refreshTokenBytes = refreshTokenBytes; }
    public String getHmacPepper() { return hmacPepper; }
    public void setHmacPepper(String hmacPepper) { this.hmacPepper = hmacPepper; }
    public String getRevokedKeyPrefix() { return revokedKeyPrefix; }
    public void setRevokedKeyPrefix(String revokedKeyPrefix) { this.revokedKeyPrefix = revokedKeyPrefix; }
    public boolean isAllowLocalPepper() { return allowLocalPepper; }
    public void setAllowLocalPepper(boolean allowLocalPepper) { this.allowLocalPepper = allowLocalPepper; }

    /** 在创建任何 Session/Refresh Bean 前执行安全配置校验。 */
    public void validate(boolean production) {
        requirePositive(webAbsoluteTtl, "Web Session 绝对时长");
        requirePositive(mobileAbsoluteTtl, "Mobile Session 绝对时长");
        requirePositive(accessTokenTtl, "Access Token 时长");
        if (refreshTokenBytes < 32 || refreshTokenBytes > 128) {
            throw new IllegalStateException("Refresh Token 随机字节数必须在 32 到 128 之间");
        }
        if (hmacPepper == null || hmacPepper.length() < 32) {
            throw new IllegalStateException("Refresh HMAC Pepper 至少需要 32 个字符");
        }
        if (production && allowLocalPepper) {
            throw new IllegalStateException("生产环境禁止使用本地默认 Refresh HMAC Pepper");
        }
        if (revokedKeyPrefix == null || revokedKeyPrefix.isBlank()) {
            throw new IllegalStateException("revoked sid Redis Key 前缀不能为空");
        }
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException(name + "必须为正数");
        }
    }
}
