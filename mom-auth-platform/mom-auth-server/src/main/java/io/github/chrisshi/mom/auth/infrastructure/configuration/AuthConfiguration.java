package io.github.chrisshi.mom.auth.infrastructure.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

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
}
