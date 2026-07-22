package io.github.chrisshi.mom.iam.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationProvider;

/** 登录安全链只使用数据库密码 Provider；Refresh Provider 仅注册到 Token Endpoint。 */
@Configuration(proxyBeanMethods = false)
class IamPasswordAuthenticationProviderSelection {
    @Bean
    @Primary
    AuthenticationProvider iamPrimaryPasswordAuthenticationProvider(
            @Qualifier("iamAuthenticationProvider") AuthenticationProvider delegate) {
        return delegate;
    }
}
