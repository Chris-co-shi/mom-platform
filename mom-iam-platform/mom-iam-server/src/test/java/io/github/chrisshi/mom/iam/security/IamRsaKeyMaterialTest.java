package io.github.chrisshi.mom.iam.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** IAM 非 HTTPS Issuer 在两个生产 Profile 下的 Fail Fast 测试。 */
class IamRsaKeyMaterialTest {

    @Test
    void productionProfileMustRejectNonHttpsIssuer() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");

        assertThrows(IllegalStateException.class, () ->
                IamRsaKeyMaterial.requireProductionIssuer(
                        URI.create("http://127.0.0.1:20100"), environment));
    }

    @Test
    void prodProfileMustRejectNonHttpsIssuer() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class, () ->
                IamRsaKeyMaterial.requireProductionIssuer(
                        URI.create("http://127.0.0.1:20100"), environment));
    }

    @Test
    void productionProfileMustRejectTestSigningKeyResources() {
        IamAuthorizationProperties properties = completeProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");

        assertThrows(IllegalStateException.class,
                () -> IamRsaKeyMaterial.load(properties, environment));
    }

    private static IamAuthorizationProperties completeProperties() {
        IamAuthorizationProperties properties = new IamAuthorizationProperties();
        properties.getKey().setKeyId("test-key");
        properties.getKey().setPrivateKeyLocation(
                new ClassPathResource("keys/test/mom-iam-private.pem"));
        properties.getKey().setPublicKeyLocation(
                new ClassPathResource("keys/test/mom-iam-public.pem"));
        properties.getAdminWeb().setRedirectUri(URI.create("http://127.0.0.1/admin"));
        properties.getAdminWeb().setPostLogoutRedirectUri(URI.create("http://127.0.0.1/"));
        properties.getSupplierWeb().setRedirectUri(URI.create("http://127.0.0.1/supplier"));
        properties.getSupplierWeb().setPostLogoutRedirectUri(URI.create("http://127.0.0.1/"));
        properties.getCustomerWeb().setRedirectUri(URI.create("http://127.0.0.1/customer"));
        properties.getCustomerWeb().setPostLogoutRedirectUri(URI.create("http://127.0.0.1/"));
        properties.getMobilePda().setRedirectUri(URI.create("com.mom.mobile:/oauth2/callback"));
        properties.getMobilePda().setPostLogoutRedirectUri(URI.create("com.mom.mobile:/logout/callback"));
        return properties;
    }
}
