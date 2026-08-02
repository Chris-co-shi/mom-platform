package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.security.revocation.MomRevokedSessionKeys;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * IAM 用户授权 Session、Refresh HMAC 与 revoked sid 的安全配置边界。
 *
 * <p>该类型只承载 IAM Server 的运行参数，不依赖 Web、数据库或 Redis 实现。默认值保持环境中立：
 * 生命周期和 Key 前缀沿用 P1.5 协议基线，Refresh HMAC Pepper 必须由运行环境显式注入，且本地
 * Pepper 例外默认关闭。配置校验在创建 Session/Refresh Bean 前执行，失败时直接阻止相关安全
 * Bean 启动，不执行外部基础设施访问，也不改变既有 Session、Rotation 或撤销语义。</p>
 */
@ConfigurationProperties("mom.iam.session")
public class IamSessionProperties {
    private Duration webAbsoluteTtl = Duration.ofHours(8);
    private Duration mobileAbsoluteTtl = Duration.ofHours(12);
    private Duration accessTokenTtl = Duration.ofMinutes(10);
    private int refreshTokenBytes = 48;
    private String hmacPepper = "";
    private String revokedKeyPrefix = MomRevokedSessionKeys.DEFAULT_PREFIX;
    private boolean allowLocalPepper;

    /** 返回 Web 用户授权 Session 的绝对有效期。 */
    public Duration getWebAbsoluteTtl() { return webAbsoluteTtl; }
    /** 设置 Web 用户授权 Session 的绝对有效期。 */
    public void setWebAbsoluteTtl(Duration webAbsoluteTtl) { this.webAbsoluteTtl = webAbsoluteTtl; }
    /** 返回 Mobile 用户授权 Session 的绝对有效期。 */
    public Duration getMobileAbsoluteTtl() { return mobileAbsoluteTtl; }
    /** 设置 Mobile 用户授权 Session 的绝对有效期。 */
    public void setMobileAbsoluteTtl(Duration mobileAbsoluteTtl) { this.mobileAbsoluteTtl = mobileAbsoluteTtl; }
    /** 返回 Access Token 有效期。 */
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    /** 设置 Access Token 有效期。 */
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
    /** 返回 Refresh Token 的安全随机字节数。 */
    public int getRefreshTokenBytes() { return refreshTokenBytes; }
    /** 设置 Refresh Token 的安全随机字节数。 */
    public void setRefreshTokenBytes(int refreshTokenBytes) { this.refreshTokenBytes = refreshTokenBytes; }
    /** 返回由部署环境注入的 Refresh HMAC Pepper。 */
    public String getHmacPepper() { return hmacPepper; }
    /** 设置 Refresh HMAC Pepper；调用方不得记录该值。 */
    public void setHmacPepper(String hmacPepper) { this.hmacPepper = hmacPepper; }
    /** 返回 revoked sid 的 Redis Key 前缀。 */
    public String getRevokedKeyPrefix() { return revokedKeyPrefix; }
    /** 设置 revoked sid 的 Redis Key 前缀。 */
    public void setRevokedKeyPrefix(String revokedKeyPrefix) { this.revokedKeyPrefix = revokedKeyPrefix; }
    /** 返回是否显式允许非生产环境使用本地 Pepper。 */
    public boolean isAllowLocalPepper() { return allowLocalPepper; }
    /** 设置是否允许非生产环境使用本地 Pepper；生产环境始终拒绝该例外。 */
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
