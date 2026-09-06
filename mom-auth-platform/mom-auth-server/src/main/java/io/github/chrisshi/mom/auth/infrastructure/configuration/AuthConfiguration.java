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
 * Mini Auth 的 Spring Security 认证基础设施配置。
 *
 * <p>用户名密码认证复用 AuthenticationManager → ProviderManager → DaoAuthenticationProvider →
 * AuthUserDetailsService 的原生链路。这里仅装配 Credential Authentication，认证成功后的 Opaque Token
 * 签发仍由 AuthenticationApplication/MomTokenStore 负责。</p>
 *
 * <p>使用 ConditionalOnMissingBean 保留框架级替换能力，但本模块默认不创建第二套并行认证链。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    /** bcrypt cost，和现有 V2 初始化密码摘要保持一致。 */
    private static final int BCRYPT_STRENGTH = 12;

    /**
     * 创建支持 `{bcrypt}` 前缀协议的密码编码器。
     *
     * <p>必须使用 {@link DelegatingPasswordEncoder}，避免手工截取 `{bcrypt}` 前缀或绕过 Spring Security
     * 的 encoding id 协议；新密码继续使用 bcrypt strength 12。</p>
     *
     * @return 默认 PasswordEncoder
     */
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    PasswordEncoder authPasswordEncoder() {
        String idForEncode = "bcrypt";
        PasswordEncoder bcrypt = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
        return new DelegatingPasswordEncoder(idForEncode, Map.of(idForEncode, bcrypt));
    }

    /**
     * 装配 Mini Auth 的用户名密码 AuthenticationManager。
     *
     * @param userDetailsService MOM 用户与 authority 加载适配器
     * @param passwordEncoder 支持 `{bcrypt}` 的密码编码器
     * @return 基于 DaoAuthenticationProvider 的 ProviderManager
     */
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
