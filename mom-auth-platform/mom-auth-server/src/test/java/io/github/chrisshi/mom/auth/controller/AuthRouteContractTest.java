package io.github.chrisshi.mom.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证认证会话与本人用户资源使用各自的 Controller 资源根。 */
class AuthRouteContractTest {
    @Test
    void sessionAndCurrentUserRoutesMustHaveExplicitResourceRoots() throws Exception {
        assertThat(AuthenticationController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/sessions");
        assertThat(AuthenticationController.class.getMethod("login",
                io.github.chrisshi.mom.auth.controller.request.LoginRequest.class)
                .getAnnotation(PostMapping.class).value()).containsExactly("/login");
        assertThat(CurrentUserController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/users");
    }
}
