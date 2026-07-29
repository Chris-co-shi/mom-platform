package io.github.chrisshi.mom.iam.admin;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** IAM Admin 兼容错误字段、稳定 code、HTTP 状态与底层异常脱敏测试。 */
class IamAdminExceptionHandlerTest {
    private final IamAdminExceptionHandler handler = new IamAdminExceptionHandler();

    /** 六类已发布错误必须继续只返回 code 和 message。 */
    @Test
    void stableErrorsMustKeepStatusCodeAndTwoFieldBody() throws Exception {
        assertError("notFound", HttpStatus.NOT_FOUND, "not_found",
                handler.notFound(new IamAdminExceptions.NotFound("用户不存在")));
        assertError("staleVersion", HttpStatus.CONFLICT, "stale_version",
                handler.staleVersion(new IamAdminExceptions.StaleVersion("version 已过期")));
        assertError("conflict", HttpStatus.CONFLICT, "conflict",
                handler.conflict(new IamAdminExceptions.Conflict("状态冲突")));
        assertError("unavailable", HttpStatus.SERVICE_UNAVAILABLE, "dependency_unavailable",
                handler.unavailable(new IamAdminExceptions.DependencyUnavailable("外部校验不可用")));
        assertError("badRequest", HttpStatus.BAD_REQUEST, "invalid_request",
                handler.badRequest(new IllegalArgumentException("请求无效")));
        assertError("forbidden", HttpStatus.FORBIDDEN, "forbidden",
                handler.forbidden(new AccessDeniedException("internal permission detail")));
    }

    /** 数据库异常文本不得通过管理 API 泄漏。 */
    @Test
    void dataIntegrityFailureMustRemainGeneric() {
        IamAdminErrorResponse response = handler.conflict(
                new DataIntegrityViolationException("constraint uk_user secret sql detail"));

        assertThat(response.code()).isEqualTo("conflict");
        assertThat(response.message()).isEqualTo("操作违反唯一性、引用或并发约束");
        assertThat(response.toString()).doesNotContain("uk_user", "sql", "secret");
    }

    /** HTTP 序列化必须继续产生仅含 code/message 的 JSON 对象。 */
    @Test
    void httpErrorBodyMustRemainTwoFieldJson() throws Exception {
        IamAdminService service = mock(IamAdminService.class);
        when(service.getUser(any(), org.mockito.ArgumentMatchers.eq("100")))
                .thenThrow(new IamAdminExceptions.NotFound("用户不存在"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new IamAdminController(service))
                .setControllerAdvice(handler)
                .build();

        String body = mockMvc.perform(get("/api/iam/admin/users/100"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.message").value("用户不存在"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("timestamp", "path", "details", "traceId", "correlationId");
    }

    private static void assertError(
            String handlerMethod, HttpStatus expectedStatus, String code,
            IamAdminErrorResponse response) throws Exception {
        Method method = java.util.Arrays.stream(IamAdminExceptionHandler.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(handlerMethod))
                .findFirst().orElseThrow();
        assertThat(method.getAnnotation(ResponseStatus.class).value()).isEqualTo(expectedStatus);
        assertThat(response.code()).isEqualTo(code);
        assertThat(IamAdminErrorResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("code", "message");
    }
}
