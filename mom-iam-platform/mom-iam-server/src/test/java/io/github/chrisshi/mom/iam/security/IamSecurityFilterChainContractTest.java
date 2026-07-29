package io.github.chrisshi.mom.iam.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAM 三条 SecurityFilterChain 的顺序与第一方 JSON 匹配行为保持测试。
 *
 * <p>测试使用隔离 Web 上下文，不加载数据库、Redis、SAS JDBC Store 或真实 JWK。它不测试认证业务，
 * 只冻结已经发布的 Chain 优先级，以及 JSON 登录/首次改密/Refresh 公开、Logout Bearer、未知认证路径
 *拒绝和 CSRF 现状，避免 S03 文档或门禁改变协议。</p>
 */
class IamSecurityFilterChainContractTest {

    /** SAS、JSON 与页面/API Chain 必须保持 HIGHEST、1、2 的顺序。 */
    @Test
    void securityFilterChainOrderMustRemainStable() throws Exception {
        assertThat(order(IamAuthorizationServerProtocolConfiguration.class,
                "iamAuthorizationServerSecurityFilterChain")).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(order(IamDirectAuthenticationConfiguration.class,
                "iamDirectAuthenticationSecurityFilterChain")).isEqualTo(1);
        assertThat(order(IamLoginPageSecurityConfiguration.class,
                "iamLoginAndApiSecurityFilterChain")).isEqualTo(2);
    }

    /** JSON Chain 必须保持当前公开、Bearer 和 CSRF 匹配语义。 */
    @Test
    void directAuthenticationChainMustKeepPublishedMatcherBehavior() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestWebConfiguration.class, IamDirectAuthenticationConfiguration.class);
            context.refresh();
            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(springSecurity())
                    .build();

            mockMvc.perform(post("/api/iam/auth/login")).andExpect(status().isOk());
            mockMvc.perform(post("/api/iam/auth/password/change-required")).andExpect(status().isOk());
            mockMvc.perform(post("/api/iam/auth/refresh")).andExpect(status().isOk());
            mockMvc.perform(post("/api/iam/auth/logout")).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/iam/auth/logout").with(jwt())).andExpect(status().isOk());
            mockMvc.perform(post("/api/iam/auth/unknown").with(jwt())).andExpect(status().isForbidden());
        }
    }

    /** 读取指定 Bean 方法的显式顺序；缺少注解直接导致测试失败。 */
    private static int order(Class<?> type, String methodName) {
        Method method = java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        Order annotation = method.getAnnotation(Order.class);
        assertThat(annotation).as(methodName + " 必须显式声明 @Order").isNotNull();
        return annotation.value();
    }

    /**
     * 为 Chain 行为提供最小 MVC 和 JwtDecoder；Decoder 不访问网络，jwt() 直接建立测试认证。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    static class TestWebConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new IllegalStateException("Chain 契约测试不得解码真实 Token");
            };
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    /** 仅用于观察安全链放行/拒绝结果的无副作用测试控制器。 */
    @RestController
    static class ProbeController {

        /** 已发布 JSON 认证路径命中测试控制器时统一返回 200，不执行业务。 */
        @PostMapping({
                "/api/iam/auth/login",
                "/api/iam/auth/password/change-required",
                "/api/iam/auth/refresh",
                "/api/iam/auth/logout",
                "/api/iam/auth/unknown"
        })
        void probe() {
            // 无副作用；HTTP 结果只由 SecurityFilterChain 决定。
        }
    }
}
