package io.github.chrisshi.mom.iam.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/** IAM Authorization Server、账号锁定、JWK 与四个 Public Client 的环境配置。 */
@ConfigurationProperties("mom.iam.authorization")
public class IamAuthorizationProperties {
    private boolean enabled = true;
    private URI issuer = URI.create("http://127.0.0.1:20100");
    private final AccountSecurity security = new AccountSecurity();
    private final SigningKey key = new SigningKey();
    private final Client adminWeb = new Client();
    private final Client supplierWeb = new Client();
    private final Client customerWeb = new Client();
    private final Client mobilePda = new Client();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public URI getIssuer() { return issuer; }
    public void setIssuer(URI issuer) { this.issuer = issuer; }
    public AccountSecurity getSecurity() { return security; }
    public SigningKey getKey() { return key; }
    public Client getAdminWeb() { return adminWeb; }
    public Client getSupplierWeb() { return supplierWeb; }
    public Client getCustomerWeb() { return customerWeb; }
    public Client getMobilePda() { return mobilePda; }

    /** 返回固定 Client ID 与环境 URI 的不可变注册清单。 */
    public List<ClientRegistration> registrations() {
        return List.of(
                new ClientRegistration("mom-admin-web", "MOM Admin Web", adminWeb),
                new ClientRegistration("mom-supplier-web", "MOM Supplier Web", supplierWeb),
                new ClientRegistration("mom-customer-web", "MOM Customer Web", customerWeb),
                new ClientRegistration("mom-mobile-pda", "MOM Mobile PDA", mobilePda));
    }

    /** 在创建任何协议 Bean 前验证安全相关配置。 */
    public void validate() {
        if (issuer == null || !issuer.isAbsolute() || issuer.getFragment() != null) {
            throw new IllegalStateException("IAM issuer 必须是无 fragment 的绝对 URI");
        }
        if (security.maximumFailedAttempts < 1) {
            throw new IllegalStateException("IAM 登录失败锁定阈值必须大于零");
        }
        if (security.lockDuration == null || security.lockDuration.isZero()
                || security.lockDuration.isNegative()) {
            throw new IllegalStateException("IAM 临时锁定时长必须为正数");
        }
        if (security.minimumPasswordLength < 12 || security.minimumPasswordLength > 128) {
            throw new IllegalStateException("IAM 最小密码长度必须在 12 到 128 之间");
        }
        if (key.keyId == null || key.keyId.isBlank()
                || key.privateKeyLocation == null || key.publicKeyLocation == null) {
            throw new IllegalStateException("IAM JWK kid、私钥和公钥资源必须完整配置");
        }
        registrations().forEach(ClientRegistration::validate);
    }

    /** 账号认证安全配置。 */
    public static class AccountSecurity {
        private int maximumFailedAttempts = 5;
        private Duration lockDuration = Duration.ofMinutes(15);
        private int minimumPasswordLength = 12;

        public int getMaximumFailedAttempts() { return maximumFailedAttempts; }
        public void setMaximumFailedAttempts(int maximumFailedAttempts) {
            this.maximumFailedAttempts = maximumFailedAttempts;
        }
        public Duration getLockDuration() { return lockDuration; }
        public void setLockDuration(Duration lockDuration) { this.lockDuration = lockDuration; }
        public int getMinimumPasswordLength() { return minimumPasswordLength; }
        public void setMinimumPasswordLength(int minimumPasswordLength) {
            this.minimumPasswordLength = minimumPasswordLength;
        }
    }

    /** RSA 签名密钥配置。 */
    public static class SigningKey {
        private String keyId;
        private Resource privateKeyLocation;
        private Resource publicKeyLocation;
        private boolean allowTestKey;

        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }
        public Resource getPrivateKeyLocation() { return privateKeyLocation; }
        public void setPrivateKeyLocation(Resource privateKeyLocation) {
            this.privateKeyLocation = privateKeyLocation;
        }
        public Resource getPublicKeyLocation() { return publicKeyLocation; }
        public void setPublicKeyLocation(Resource publicKeyLocation) {
            this.publicKeyLocation = publicKeyLocation;
        }
        public boolean isAllowTestKey() { return allowTestKey; }
        public void setAllowTestKey(boolean allowTestKey) { this.allowTestKey = allowTestKey; }
    }

    /** 单个 Public Client 的环境相关回调 URI。 */
    public static class Client {
        private URI redirectUri;
        private URI postLogoutRedirectUri;

        public URI getRedirectUri() { return redirectUri; }
        public void setRedirectUri(URI redirectUri) { this.redirectUri = redirectUri; }
        public URI getPostLogoutRedirectUri() { return postLogoutRedirectUri; }
        public void setPostLogoutRedirectUri(URI postLogoutRedirectUri) {
            this.postLogoutRedirectUri = postLogoutRedirectUri;
        }
    }

    /** 固定 Client ID、名称与环境 URI 的组合。 */
    public record ClientRegistration(String clientId, String clientName, Client client) {
        private void validate() {
            requireExactUri(client.redirectUri, clientId + " redirect_uri");
            requireExactUri(client.postLogoutRedirectUri, clientId + " post_logout_redirect_uri");
        }

        private static void requireExactUri(URI uri, String name) {
            if (uri == null || !uri.isAbsolute() || uri.getFragment() != null) {
                throw new IllegalStateException(name + " 必须是无 fragment 的绝对 URI");
            }
        }
    }
}
