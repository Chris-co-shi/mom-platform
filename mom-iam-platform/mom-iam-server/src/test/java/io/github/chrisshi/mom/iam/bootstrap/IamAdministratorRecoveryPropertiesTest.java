package io.github.chrisshi.mom.iam.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 一次性管理员恢复配置的默认关闭、六位下限和生产环境拒绝测试。 */
class IamAdministratorRecoveryPropertiesTest {

    @Test
    void disabledRecoveryMustNotRequirePassword() {
        assertDoesNotThrow(() ->
                new IamAdministratorRecoveryProperties().validate(new MockEnvironment()));
    }

    @Test
    void enabledRecoveryMustAcceptSixCharacterPassword() {
        IamAdministratorRecoveryProperties properties = enabled("admin1");
        assertDoesNotThrow(() -> properties.validate(new MockEnvironment()));
    }

    @Test
    void enabledRecoveryMustRejectFiveCharacterPassword() {
        IamAdministratorRecoveryProperties properties = enabled("12345");
        assertThrows(IllegalStateException.class,
                () -> properties.validate(new MockEnvironment()));
    }

    @Test
    void enabledRecoveryMustRejectBlankPassword() {
        IamAdministratorRecoveryProperties properties = enabled(" ");
        assertThrows(IllegalStateException.class,
                () -> properties.validate(new MockEnvironment()));
    }

    @Test
    void prodAndProductionProfilesMustRejectRecovery() {
        for (String profile : new String[]{"prod", "production"}) {
            IamAdministratorRecoveryProperties properties = enabled("admin1");
            MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles(profile);
            assertThrows(IllegalStateException.class,
                    () -> properties.validate(environment));
        }
    }

    private static IamAdministratorRecoveryProperties enabled(String password) {
        IamAdministratorRecoveryProperties properties = new IamAdministratorRecoveryProperties();
        properties.setEnabled(true);
        properties.setPassword(password);
        return properties;
    }
}
