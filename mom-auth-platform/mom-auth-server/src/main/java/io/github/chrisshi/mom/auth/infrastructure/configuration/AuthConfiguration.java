package io.github.chrisshi.mom.auth.infrastructure.configuration;

import io.github.chrisshi.mom.auth.infrastructure.security.AuthUserDetailsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * Mini Auth 的认证基础设施配置。
 *
 * <p>用户名密码认证复用 Spring Security 的 AuthenticationManager/
 * DaoAuthenticationProvider/UserDetailsService；MOM 只在认证成功后负责自己的 Opaque Token 生命周期。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    PasswordEncoder authPasswordEncoder() {
        String idForEncode = "bcrypt";
        PasswordEncoder bcrypt = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
        return new DelegatingPasswordEncoder(idForEncode, Map.of(idForEncode, bcrypt));
    }

    @Bean
    @ConditionalOnMissingBean(AuthenticationManager.class)
    AuthenticationManager authAuthenticationManager(
        AuthUserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
