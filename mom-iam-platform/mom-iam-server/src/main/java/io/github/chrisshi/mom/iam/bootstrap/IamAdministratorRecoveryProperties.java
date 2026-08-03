package io.github.chrisshi.mom.iam.bootstrap;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * 已存在内置管理员的一次性本地恢复配置。
 *
 * <p>功能默认关闭，只能在非 {@code prod}/{@code production} Profile 使用。密码只允许通过环境变量
 * 注入；校验异常、日志和返回结果均不得包含密码或密码摘要。</p>
 */
@Getter
@Setter
@ConfigurationProperties("mom.iam.recovery")
public class IamAdministratorRecoveryProperties {
    private boolean enabled;
    private String password = "";
    private boolean forcePasswordChange;

    /** 在任何数据库写入前验证一次性恢复安全边界。 */
    public void validate(Environment environment) {
        if (!enabled) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException(
                    "IAM built-in administrator recovery is forbidden in prod and production profiles");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "IAM_ADMIN_RECOVERY_PASSWORD is required when IAM administrator recovery is enabled");
        }
        if (password.length() < 6 || password.length() > 200) {
            throw new IllegalStateException(
                    "IAM_ADMIN_RECOVERY_PASSWORD must contain 6 to 200 characters");
        }
    }
}
