package io.github.chrisshi.mom.iam.bootstrap;

import io.github.chrisshi.mom.iam.application.recovery.IamAdministratorRecoveryApplicationService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * 内置管理员一次性恢复配置。
 *
 * <p>配置默认关闭，临时凭据和显式确认串只能由运行环境注入。该对象不实现恢复用例、不连接数据库，
 * 也不会通过 {@code toString()} 输出 Secret。生产 Profile、Bootstrap 同时启用、目标用户名变化、关闭
 * 强制改密或缺少确认串都会在 Runner 创建阶段 Fail Fast。</p>
 */
@Getter
@Setter
@ConfigurationProperties("mom.iam.recovery")
public class IamAdministratorRecoveryProperties {
    public static final String REQUIRED_CONFIRMATION = "RESET_ADMIN_CREDENTIAL";

    private boolean enabled;
    private String username = IamAdministratorRecoveryApplicationService.BUILT_IN_USERNAME;
    private String password = "";
    private String confirmation = "";
    private boolean forcePasswordChange = true;

    /**
     * 校验启动恢复的安全前置条件。
     *
     * @param environment 当前 Spring Profile 环境
     * @param bootstrapEnabled 内置管理员 Bootstrap 是否同时启用
     * @throws IllegalStateException 任一安全前置条件不成立时抛出，异常不包含配置值
     */
    public void validate(Environment environment, boolean bootstrapEnabled) {
        if (!enabled) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException(
                    "IAM administrator recovery is forbidden in prod and production profiles");
        }
        if (bootstrapEnabled) {
            throw new IllegalStateException(
                    "IAM bootstrap and administrator recovery cannot be enabled together");
        }
        if (!IamAdministratorRecoveryApplicationService.BUILT_IN_USERNAME.equals(username)) {
            throw new IllegalStateException("IAM recovery username must be admin");
        }
        if (password == null || password.length() < 12 || password.length() > 200) {
            throw new IllegalStateException(
                    "IAM_ADMIN_RECOVERY_PASSWORD must contain 12 to 200 characters");
        }
        if (!REQUIRED_CONFIRMATION.equals(confirmation)) {
            throw new IllegalStateException(
                    "IAM_ADMIN_RECOVERY_CONFIRMATION must explicitly confirm the recovery operation");
        }
        if (!forcePasswordChange) {
            throw new IllegalStateException(
                    "IAM administrator recovery must require a subsequent credential change");
        }
    }
}
