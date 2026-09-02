package io.github.chrisshi.mom.iam.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Bootstrap 默认关闭、固定用户名、六位密码下限和双生产 Profile 拒绝规则单元测试。 */
class IamAdministratorBootstrapPropertiesTest {

    @Test
    void disabledBootstrapMustNotRequirePassword() {
        IamAdministratorBootstrapProperties properties = new IamAdministratorBootstrapProperties();
        assertDoesNotThrow(() -> properties.validate(new MockEnvironment()));
    }

    @Test
    void enabledBootstrapMustAcceptSixCharacterPassword() {
        IamAdministratorBootstrapProperties properties = enabledProperties();
        assertDoesNotThrow(() -> properties.validate(new MockEnvironment()));
    }

    @Test
    void enabledBootstrapMustRejectFiveCharacterPassword() {
        IamAdministratorBootstrapProperties properties = enabledProperties();
        properties.setPassword("12345");
        assertThrows(IllegalStateException.class,
                () -> properties.validate(new MockEnvironment()));
    }

    @Test
    void enabledBootstrapMustRejectMissingPassword() {
        IamAdministratorBootstrapProperties properties = enabledProperties();
        properties.setPassword(" ");
        assertThrows(IllegalStateException.class,
                () -> properties.validate(new MockEnvironment()));
    }

    @Test
    void enabledBootstrapMustRejectUsernameOverride() {
        IamAdministratorBootstrapProperties properties = enabledProperties();
        properties.setUsername("another-admin");
        assertThrows(IllegalStateException.class,
                () -> properties.validate(new MockEnvironment()));
    }

    @Test
    void prodProfileMustRejectBootstrap() {
        IamAdministratorBootstrapProperties properties = enabledProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class,
                () -> properties.validate(environment));
    }

    @Test
    void productionProfileMustRejectBootstrap() {
        IamAdministratorBootstrapProperties properties = enabledProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        assertThrows(IllegalStateException.class,
                () -> properties.validate(environment));
    }

    private static IamAdministratorBootstrapProperties enabledProperties() {
        IamAdministratorBootstrapProperties properties = new IamAdministratorBootstrapProperties();
        properties.setEnabled(true);
        properties.setPassword("admin1");
        return properties;
    }
}
