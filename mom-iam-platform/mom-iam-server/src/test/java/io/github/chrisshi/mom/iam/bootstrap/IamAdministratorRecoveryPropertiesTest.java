package io.github.chrisshi.mom.iam.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 管理员恢复默认关闭、生产禁止、双开拒绝和显式确认规则单元测试。 */
class IamAdministratorRecoveryPropertiesTest {

    @Test
    void disabledRecoveryMustNotRequireSecretOrConfirmation() {
        assertDoesNotThrow(() -> new IamAdministratorRecoveryProperties()
                .validate(new MockEnvironment(), false));
    }

    @Test
    void enabledRecoveryMustRejectMissingPassword() {
        var properties = enabledProperties();
        properties.setPassword("");
        assertThrows(IllegalStateException.class,
                () -> properties.validate(new MockEnvironment(), false));
    }

    @Test
    void enabledRecoveryMustRejectMissingExplicitConfirmation() {
        var properties = enabledProperties();
        properties.setConfirmation("");
        assertThrows(IllegalStateException.class,
                () -> properties.validate(new MockEnvironment(), false));
    }

    @Test
    void enabledRecoveryMustRejectBootstrapRunningAtTheSameTime() {
        var properties = enabledProperties();
        assertThrows(IllegalStateException.class,
                () -> properties.validate(new MockEnvironment(), true));
    }

    @Test
    void enabledRecoveryMustRejectDisabledForcedChange() {
        var properties = enabledProperties();
        properties.setForcePasswordChange(false);
        assertThrows(IllegalStateException.class,
                () -> properties.validate(new MockEnvironment(), false));
    }

    @Test
    void bothProductionProfileNamesMustRejectRecovery() {
        var properties = enabledProperties();
        var prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        var production = new MockEnvironment();
        production.setActiveProfiles("production");
        assertThrows(IllegalStateException.class, () -> properties.validate(prod, false));
        assertThrows(IllegalStateException.class,
                () -> properties.validate(production, false));
    }

    @Test
    void explicitSafeNonProductionConfigurationMustPass() {
        assertDoesNotThrow(() -> enabledProperties().validate(new MockEnvironment(), false));
    }

    private static IamAdministratorRecoveryProperties enabledProperties() {
        var properties = new IamAdministratorRecoveryProperties();
        properties.setEnabled(true);
        properties.setPassword("Test-Recovery-Secret-2026!");
        properties.setConfirmation(IamAdministratorRecoveryProperties.REQUIRED_CONFIRMATION);
        return properties;
    }
}
