package io.github.chrisshi.mom.iam.bootstrap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * 内置管理员 Bootstrap 配置。
 *
 * <p>功能默认关闭；用户名虽然作为显式配置展示，但只接受固定值 {@code admin}。密码只能由部署环境
 * 注入，校验异常不会包含密码或摘要。生产 Profile 和缺失密码均在任何数据库写入前 Fail Fast。</p>
 */
@Getter
@Setter
@ConfigurationProperties("mom.iam.bootstrap")
public class IamAdministratorBootstrapProperties {
    /** 唯一允许的内置系统管理员用户名。 */
    public static final String BUILT_IN_USERNAME = "admin";

    private boolean enabled;
    private String username = BUILT_IN_USERNAME;
    private String password = "";
    private String displayName = "Platform Administrator";

    /**
     * 校验 Bootstrap 安全前置条件。
     *
     * @param environment 当前 Spring Profile 环境
     * @throws IllegalStateException 生产环境启用、用户名被覆盖或密码缺失时抛出
     */
    public void validate(Environment environment) {
        if (!enabled) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException(
                    "IAM built-in administrator bootstrap is forbidden in prod and production profiles");
        }
        if (!BUILT_IN_USERNAME.equals(username)) {
            throw new IllegalStateException("IAM bootstrap username must be admin");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "IAM_BOOTSTRAP_PASSWORD is required when IAM bootstrap is enabled");
        }
        if (password.length() < 6 || password.length() > 200) {
            throw new IllegalStateException(
                    "IAM_BOOTSTRAP_PASSWORD must contain 6 to 200 characters");
        }
        if (displayName == null || displayName.isBlank() || displayName.trim().length() > 200) {
            throw new IllegalStateException(
                    "IAM bootstrap display name must contain 1 to 200 characters");
        }
    }
}
