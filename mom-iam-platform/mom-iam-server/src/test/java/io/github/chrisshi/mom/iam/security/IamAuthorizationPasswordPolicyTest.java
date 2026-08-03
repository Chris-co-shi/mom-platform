package io.github.chrisshi.mom.iam.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IAM 首次改密最小长度配置边界测试。 */
class IamAuthorizationPasswordPolicyTest {

    @Test
    void defaultMinimumPasswordLengthMustBeSix() {
        IamAuthorizationProperties properties = new IamAuthorizationProperties();
        assertThat(properties.getSecurity().getMinimumPasswordLength()).isEqualTo(6);
    }

    @Test
    void configuredMinimumBelowSixMustFailBeforeProtocolInfrastructureValidation() {
        IamAuthorizationProperties properties = new IamAuthorizationProperties();
        properties.getSecurity().setMinimumPasswordLength(5);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("6 到 128");
    }
}
