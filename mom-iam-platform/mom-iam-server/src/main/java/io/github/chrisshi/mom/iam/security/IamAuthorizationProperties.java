package io.github.chrisshi.mom.iam.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/** IAM Authorization Server、账号锁定、JWK、Public Client 与受控服务 Client 的环境配置。 */
@Getter
@ConfigurationProperties("mom.iam.authorization")
public class IamAuthorizationProperties {
    @Setter
    private boolean enabled = true;
    @Setter
    private URI issuer = URI.create("http://127.0.0.1:20100");
    private final AccountSecurity security = new AccountSecurity();
    private final SigningKey key = new SigningKey();
    private final Client adminWeb = new Client();
    private final Client supplierWeb = new Client();
    private final Client customerWeb = new Client();
    private final Client mobilePda = new Client();
    private final ServiceClient systemService = new ServiceClient();

    /** 返回固定 Public Client ID 与环境 URI 的不可变注册清单。 */
    public List<ClientRegistration> registrations() {
        return List.of(
                new ClientRegistration("mom-admin-web", "MOM Admin Web", adminWeb),
                new ClientRegistration("mom-supplier-web", "MOM Supplier Web", supplierWeb),
                new ClientRegistration("mom-customer-web", "MOM Customer Web", customerWeb),
                new ClientRegistration("mom-mobile-pda", "MOM Mobile PDA", mobilePda));
    }

    /** 在创建任何协议 Bean 前验证全部启用的安全相关配置。 */
    public void validate() {
        if (issuer == null || !issuer.isAbsolute() || issuer.getFragment() != null) {
            throw new IllegalStateException("IAM issuer 必须是无 fragment 的绝对 URI");
        }
        if (security.maxFailedAttempts < 1) {
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
        systemService.validate();
    }

    /** 账号认证安全配置。 */
    @Setter
    @Getter
    public static class AccountSecurity {
        private int maxFailedAttempts = 5;
        private Duration lockDuration = Duration.ofMinutes(15);
        private int minimumPasswordLength = 12;
    }

    /** RSA 签名密钥配置。 */
    @Setter
    @Getter
    public static class SigningKey {
        private String keyId;
        private Resource privateKeyLocation;
        private Resource publicKeyLocation;
        private boolean allowTestKey;
    }

    /** 单个 Public Client 的环境相关回调 URI。 */
    @Setter
    @Getter
    public static class Client {
        private URI redirectUri;
        private URI postLogoutRedirectUri;
    }

    /** System 服务身份的 client_credentials 配置。 */
    @Setter
    @Getter
    public static class ServiceClient {
        private boolean enabled;
        private String clientId = "mom-system-server";
        private String clientName = "MOM System Server";
        private String clientSecret;
        private String scope = "iam.permission-reference.read";
        private Duration accessTokenTtl = Duration.ofMinutes(3);

        /** 只验证本服务 Client，不依赖 Public Client 回调 URI。 */
        void validate() {
            if (!enabled) {
                return;
            }
            if (clientId == null || clientId.isBlank() || clientId.length() > 100) {
                throw new IllegalStateException("IAM System Service clientId 必须为 1～100 位非空文本");
            }
            if (clientName == null || clientName.isBlank() || clientName.length() > 200) {
                throw new IllegalStateException("IAM System Service clientName 必须为 1～200 位非空文本");
            }
            if (clientSecret == null || clientSecret.length() < 32) {
                throw new IllegalStateException("IAM System Service clientSecret 至少 32 位");
            }
            if (scope == null || scope.isBlank() || scope.length() > 100) {
                throw new IllegalStateException("IAM System Service scope 必须为 1～100 位非空文本");
            }
            if (accessTokenTtl == null || accessTokenTtl.compareTo(Duration.ofSeconds(30)) < 0
                    || accessTokenTtl.compareTo(Duration.ofMinutes(10)) > 0) {
                throw new IllegalStateException("IAM System Service accessTokenTtl 必须在 30 秒到 10 分钟之间");
            }
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
