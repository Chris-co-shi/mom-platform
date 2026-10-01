package io.github.chrisshi.mom.auth.controller;

import io.github.chrisshi.mom.auth.application.model.AuthPageParams.UserPageParams;
import io.github.chrisshi.mom.auth.application.UserApplication;
import io.github.chrisshi.mom.auth.infrastructure.configuration.AuthExceptionHandler;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Mini Auth POST 分页协议的 Controller 快速测试。
 *
 * <p>测试仅验证 PageQuery JSON 绑定、空强类型 Params 和旧 GET 删除；权限引擎、数据库分页及审计由
 * 各自测试层覆盖。Mock Application 不产生持久化或 Token 副作用。</p>
 */
class AuthPaginationControllerTest {
    private UserApplication application;
    private MockMvc mockMvc;

    /** 为每个测试创建隔离的 Controller、Advice 和模拟 Application。 */
    @BeforeEach
    void setUp() {
        application = mock(UserApplication.class);
        mockMvc = standaloneSetup(new UserController(application))
            .setControllerAdvice(new AuthExceptionHandler())
            .build();
    }

    /** 空 JSON Params 必须绑定为明确的 UserPageParams，而不是 null 或 Map。 */
    @Test
    void shouldBindEmptyTypedParamsFromPostBody() throws Exception {
        when(application.list(any())).thenReturn(new PageResult<>(List.of(), 1, 50, 0, 0));

        mockMvc.perform(post("/users/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"params": {}, "pageNo": 1, "pageSize": 50}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pageNo").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(50));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<PageQuery<UserPageParams>> captor = ArgumentCaptor.forClass(PageQuery.class);
        verify(application).list(captor.capture());
        assertThat(captor.getValue().params()).isInstanceOf(UserPageParams.class);
    }

    /** 旧 GET 分页入口必须删除，不能保留并行协议。 */
    @Test
    void shouldRejectRemovedGetPaginationEndpoint() throws Exception {
        mockMvc.perform(get("/users"))
            .andExpect(status().isMethodNotAllowed());
    }
}
