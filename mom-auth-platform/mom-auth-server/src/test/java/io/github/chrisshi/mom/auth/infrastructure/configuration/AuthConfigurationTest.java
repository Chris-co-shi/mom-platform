package io.github.chrisshi.mom.auth.infrastructure.configuration;

import io.github.chrisshi.mom.auth.infrastructure.security.AuthUserDetailsService;
import io.github.chrisshi.mom.auth.infrastructure.security.AuthUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthConfigurationTest {

    private static final String SEEDED_ADMIN_HASH =
        "{bcrypt}$2y$12$sYX/yTESd4KDo9SO/EC4Resz.hgaJTq2JfT/wa0DdTbzZRLS4u5Ba";

    @Test
    void shouldMatchSeededAdminPasswordAndEncodeWithDelegatingPrefix() {
        PasswordEncoder encoder = new AuthConfiguration().authPasswordEncoder();

        assertThat(encoder.matches("Admin@123456", SEEDED_ADMIN_HASH)).isTrue();
        assertThat(encoder.encode("AnotherPassword@123")).startsWith("{bcrypt}$2");
    }

    @Test
    void shouldAuthenticateThroughDaoAuthenticationProviderAndEraseCredentials() {
        AuthConfiguration configuration = new AuthConfiguration();
        PasswordEncoder passwordEncoder = configuration.authPasswordEncoder();
        AuthUserDetailsService userDetailsService = mock(AuthUserDetailsService.class);
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(
            principal(SEEDED_ADMIN_HASH, true)
        );
        AuthenticationManager authenticationManager = configuration.authAuthenticationManager(
            userDetailsService,
            passwordEncoder
        );

        Authentication authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated("admin", "Admin@123456")
        );

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthUserPrincipal.class);
        AuthUserPrincipal principal = (AuthUserPrincipal) authentication.getPrincipal();
        assertThat(principal.userId()).isEqualTo("1001");
        assertThat(principal.getPassword()).isNull();
    }

    @Test
    void shouldRejectWrongPasswordThroughDaoAuthenticationProvider() {
        AuthConfiguration configuration = new AuthConfiguration();
        PasswordEncoder passwordEncoder = configuration.authPasswordEncoder();
        AuthUserDetailsService userDetailsService = mock(AuthUserDetailsService.class);
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(
            principal(SEEDED_ADMIN_HASH, true)
        );
        AuthenticationManager authenticationManager = configuration.authAuthenticationManager(
            userDetailsService,
            passwordEncoder
        );

        assertThatThrownBy(() -> authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated("admin", "wrong-password")
        )).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldRejectDisabledUserThroughDaoAuthenticationProvider() {
        AuthConfiguration configuration = new AuthConfiguration();
        PasswordEncoder passwordEncoder = configuration.authPasswordEncoder();
        AuthUserDetailsService userDetailsService = mock(AuthUserDetailsService.class);
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(
            principal(SEEDED_ADMIN_HASH, false)
        );
        AuthenticationManager authenticationManager = configuration.authAuthenticationManager(
            userDetailsService,
            passwordEncoder
        );

        assertThatThrownBy(() -> authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated("admin", "Admin@123456")
        )).isInstanceOf(DisabledException.class);
    }

    private static AuthUserPrincipal principal(String passwordHash, boolean enabled) {
        return new AuthUserPrincipal(
            "1001",
            "admin",
            passwordHash,
            enabled,
            List.of("ROLE_PLATFORM_ADMIN", "auth:user:read")
        );
    }
}
