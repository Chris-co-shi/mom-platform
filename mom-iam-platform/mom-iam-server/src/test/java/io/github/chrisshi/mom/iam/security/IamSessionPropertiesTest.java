package io.github.chrisshi.mom.iam.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IAM Session 配置的安全默认值、显式 Pepper 与生产环境拒绝规则单元测试。 */
class IamSessionPropertiesTest {

    @Test
    void defaultsMustRequireExplicitPepperAndDisableLocalException() {
        IamSessionProperties properties = new IamSessionProperties();

        assertTrue(properties.getHmacPepper().isEmpty());
        assertFalse(properties.isAllowLocalPepper());
        assertThrows(IllegalStateException.class, () -> properties.validate(false));
    }

    @Test
    void explicitNonProductionPepperMustPassValidation() {
        IamSessionProperties properties = new IamSessionProperties();
        properties.setHmacPepper("x".repeat(32));

        assertDoesNotThrow(() -> properties.validate(false));
    }

    @Test
    void productionMustRejectLocalPepperException() {
        IamSessionProperties properties = new IamSessionProperties();
        properties.setHmacPepper("x".repeat(32));
        properties.setAllowLocalPepper(true);

        assertThrows(IllegalStateException.class, () -> properties.validate(true));
    }
}
