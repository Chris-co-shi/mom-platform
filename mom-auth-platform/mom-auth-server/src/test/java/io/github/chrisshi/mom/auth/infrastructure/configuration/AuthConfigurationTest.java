package io.github.chrisshi.mom.auth.infrastructure.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class AuthConfigurationTest {

    private static final String SEEDED_ADMIN_HASH =
        "{bcrypt}$2y$12$sYX/yTESd4KDo9SO/EC4Resz.hgaJTq2JfT/wa0DdTbzZRLS4u5Ba";

    @Test
    void shouldMatchSeededAdminPasswordAndEncodeWithDelegatingPrefix() {
        PasswordEncoder encoder = new AuthConfiguration().authPasswordEncoder();

        assertThat(encoder.matches("Admin@123456", SEEDED_ADMIN_HASH)).isTrue();
        assertThat(encoder.encode("AnotherPassword@123")).startsWith("{bcrypt}$2");
    }
}
