package io.github.chrisshi.mom.security.autoconfigure;

import io.github.chrisshi.mom.core.security.CurrentActorProvider;
import io.github.chrisshi.mom.security.actor.SecurityCurrentActorProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spring Security 当前操作人适配自动配置。
 *
 * <p>该配置只注册稳定的 {@link CurrentActorProvider} 解析边界，不配置登录、JWT 解码器、Authorization
 * Server、RBAC 或接口授权。应用可声明自己的 Provider 覆盖默认实现。</p>
 */
@AutoConfiguration(beforeName = "io.github.chrisshi.mom.data.autoconfigure.MomDataAutoConfiguration")
@ConditionalOnClass(SecurityContextHolder.class)
public class MomSecurityActorAutoConfiguration {

    /** 创建基于 Spring Security 的当前操作人提供器。 */
    @Bean
    @ConditionalOnMissingBean(CurrentActorProvider.class)
    CurrentActorProvider securityCurrentActorProvider() {
        return new SecurityCurrentActorProvider();
    }
}
